#!/usr/bin/env python3
"""Build the August 2026 historical daily-bar and corporate-action resources.

The download window is deliberately fixed so the checked-in resources can be
reproduced without a clock-dependent ``range=6mo`` request. Yahoo's chart quote
arrays are expressed in the post-split unit when a split occurs in the window.
For every reported split, this generator restores pre-effective-date OHLCV to
the contemporaneous RAW unit: OHLC is multiplied by the cumulative future
split ratio and volume is divided by it. Reconstructed Korean prices are
rounded half-up to the nearest won, reconstructed US prices to the nearest
cent, and reconstructed volume to the nearest whole share. Provider-side
integer rounding of an already adjusted volume is not reversible, so the
restored value can differ from an exchange tape by several or tens of shares.
Yahoo adjustedClose is retained
unchanged because it intentionally remains adjustment-aware.

NAVER Finance's chart endpoint is used only to discover KRX dates that Yahoo
omitted. Every substituted OHLCV row comes from NAVER's unadjusted HTML daily
table, because the chart endpoint may return dividend-adjusted ETF prices and
volumes. The raw table does not expose a separate adjusted-close series, so
adjustedClose is omitted on NAVER-only bars rather than fabricating one.

This script uses only the Python standard library. It writes deterministic JSON
(stable catalog/date order and no retrieval timestamp) and reports hashes plus
the source-catalog entries that the scenario manifest must reference.
"""

from __future__ import annotations

import argparse
import ast
import concurrent.futures
import gzip
import hashlib
import io
import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, replace
from datetime import date, datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
from pathlib import Path
from typing import Any, Iterable
from zoneinfo import ZoneInfo


COMPOSE_APP_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CATALOG = (
    COMPOSE_APP_ROOT
    / "src/commonMain/composeResources/files/instruments/market_instrument_catalog_v6.json"
)
DEFAULT_SCENARIO_DIR = (
    COMPOSE_APP_ROOT
    / "src/commonMain/composeResources/files/scenarios/august_2026"
)
DEFAULT_ACTION_OUTPUT = (
    DEFAULT_SCENARIO_DIR / "corporate_actions_v2.json"
)

PERIOD1 = 1_627_776_000  # 2021-08-01T00:00:00Z
PERIOD2 = 1_787_097_600  # 2026-08-19T00:00:00Z (exclusive)
FIRST_LOCAL_DATE = date(2021, 8, 1)
LAST_LOCAL_DATE = date(2026, 8, 18)
ACTION_FIRST_INSTANT = int(datetime(2026, 8, 1, tzinfo=timezone.utc).timestamp())
ACTION_LAST_INSTANT = int(datetime(2026, 8, 18, 20, tzinfo=timezone.utc).timestamp())

YAHOO_SOURCE_ID = "yahoo-finance-chart-api"
NAVER_SOURCE_ID = "naver-finance-daily-chart"
NAVER_RAW_TABLE_SOURCE_ID = "naver-finance-raw-daily-table"
YAHOO_ENDPOINT = "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}"
NAVER_ENDPOINT = "https://api.finance.naver.com/siseJson.naver"
NAVER_RAW_TABLE_ENDPOINT = "https://finance.naver.com/item/sise_day.naver?code={symbol}"
KIND_310970_URL = (
    "https://kind.krx.co.kr/disclosure/etfisudetail.do?"
    "method=searchEtfIsuSummary&strIsurCd=31097"
)
RISE_477080_URL = "https://www.riseetf.co.kr/prod/finderDetail/44G4"
SAMSUNG_BIO_SPINOFF_URL = (
    "https://kind.krx.co.kr/external/2025/09/02/000316/20250902000908/10085.htm"
)
SOXS_US_HISTORY_URL = "https://stockanalysis.com/etf/soxs/history/"
FINANCECHARTS_XAGG_HISTORY_URL = "https://www.financecharts.com/etfs/XAGG/summary/price"
SOXS_CANADA_CONSOLIDATION_URL = (
    "https://newsfile.moomoo.com/public/NN-PersistNoticeAttachment/7781/20260515/"
    "SEDAR_PLUS/CSA_SEDAR_PLUS_NOTICE_RECORD_ID_2407699.pdf"
)

EXPECTED_INSTRUMENT_COUNT = 554
EXPECTED_GAME_ACTION_COUNT = 111
EXPECTED_GAME_DIVIDEND_COUNT = 110
MAX_RESOURCE_BYTES = 32 * 1024 * 1024
MAX_DAILY_RECORDS_PER_SHARD = 100_000
DAILY_SHARD_INSTRUMENT_COUNT = 50

KOREAN_MARKETS = frozenset({"KOSPI", "KOSDAQ"})
CORRECTED_012450_DATES = frozenset(
    {date(2026, 3, 27), date(2026, 4, 1), date(2026, 6, 10)}
)
EXPECTED_012450_CORRECTIONS = {
    date(2026, 3, 27): (1_349_000, 1_349_000, 1_287_000, 1_335_000, 205_178),
    date(2026, 4, 1): (1_323_000, 1_364_000, 1_283_000, 1_333_000, 243_803),
    date(2026, 6, 10): (1_038_000, 1_066_000, 1_004_000, 1_031_000, 199_694),
}
EXPECTED_NAVER_RAW_TABLE_CORRECTIONS = {
    ("KOSPI:472150", date(2024, 1, 18)): (9_810, 9_870, 9_775, 9_850, 549_602),
    ("KOSPI:472150", date(2024, 8, 27)): (10_985, 10_985, 10_930, 10_965, 6_680),
    ("KOSPI:472150", date(2024, 10, 30)): (10_465, 10_465, 10_325, 10_325, 11_209),
    ("KOSPI:472150", date(2024, 11, 5)): (10_285, 10_285, 10_230, 10_260, 35_124),
    ("KOSPI:494300", date(2024, 10, 30)): (10_380, 10_380, 10_335, 10_365, 406_937),
    ("KOSPI:494300", date(2024, 11, 5)): (10_035, 10_080, 10_030, 10_075, 614_173),
}
KNOWN_EXTREME_NON_SPLIT_MOVES = frozenset(
    {
        ("NYSE_ARCA:SOXS", date(2025, 4, 8), date(2025, 4, 9)),
        ("KOSPI:0193L0", date(2026, 7, 30), date(2026, 7, 31)),
    }
)


@dataclass(frozen=True)
class Instrument:
    order: int
    symbol: str
    market: str
    name: str

    @property
    def instrument_id(self) -> str:
        return f"{self.market}:{self.symbol}"

    @property
    def yahoo_symbol(self) -> str:
        if self.market == "KOSPI":
            return f"{self.symbol}.KS"
        if self.market == "KOSDAQ":
            return f"{self.symbol}.KQ"
        return self.symbol.replace(".", "-")

    @property
    def expected_timezone(self) -> str:
        return "Asia/Seoul" if self.market in KOREAN_MARKETS else "America/New_York"

    @property
    def currency(self) -> str:
        return "KRW" if self.market in KOREAN_MARKETS else "USD"


@dataclass(frozen=True)
class DailyBar:
    trading_date: date
    timestamp: int
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    adjusted_close: Decimal | None
    volume: int
    source_id: str
    pregame_split_adjusted_price_factor: Decimal = Decimal(1)


@dataclass(frozen=True)
class SplitEvent:
    timestamp: int
    ratio: Fraction

    @property
    def numerator(self) -> int:
        return self.ratio.numerator

    @property
    def denominator(self) -> int:
        return self.ratio.denominator


@dataclass(frozen=True)
class DividendEvent:
    timestamp: int
    amount: Decimal


@dataclass
class YahooSeries:
    instrument: Instrument
    bars: dict[date, DailyBar]
    splits: list[SplitEvent]
    dividends: list[DividendEvent]
    raw_sha256: str
    omitted_null_rows: list[dict[str, Any]]


def apply_audited_yahoo_split_event_corrections(
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    """Removes provider duplicate corporate-action markers proven by issuer filings."""
    series = series_by_id["KOSPI:207940"]
    duplicated_ratio = Fraction(650_391, 1_000_000)
    duplicate_timestamps = {
        int(datetime(2025, 9, 29, tzinfo=timezone.utc).timestamp()),
        int(datetime(2025, 10, 30, tzinfo=timezone.utc).timestamp()),
    }
    matching = [
        event
        for event in series.splits
        if event.timestamp in duplicate_timestamps and event.ratio == duplicated_ratio
    ]
    if len(matching) != 2:
        raise ValueError(
            "Yahoo 207940 duplicate spin-off markers changed; audited correction must be reviewed"
        )
    effective = max(matching, key=lambda event: event.timestamp)
    series.splits = [
        event
        for event in series.splits
        if event not in matching
    ] + [effective]
    series.splits.sort(key=lambda event: event.timestamp)
    return [
        {
            "stockId": "KOSPI:207940",
            "removedEffectiveAt": instant_text(min(matching, key=lambda event: event.timestamp).timestamp),
            "keptEffectiveAt": instant_text(effective.timestamp),
            "splitNumerator": effective.numerator,
            "splitDenominator": effective.denominator,
            "reason": (
                "Yahoo duplicated one 0.650391 Samsung Biologics surviving-company share "
                "consolidation at the old and revised trading-halt boundaries. The official "
                "spin-off filing specifies a single consolidation."
            ),
            "officialSourceUrl": SAMSUNG_BIO_SPINOFF_URL,
        }
    ]


def validate_no_duplicate_split_event_markers(
    series_by_id: dict[str, YahooSeries],
) -> None:
    maximum_duplicate_window_seconds = 120 * 24 * 60 * 60
    for stock_id, series in series_by_id.items():
        for previous, current in zip(series.splits, series.splits[1:]):
            if (
                previous.ratio == current.ratio
                and current.timestamp - previous.timestamp <= maximum_duplicate_window_seconds
            ):
                raise ValueError(
                    "unreviewed duplicate Yahoo split markers: "
                    f"{stock_id}/{instant_text(previous.timestamp)}/"
                    f"{instant_text(current.timestamp)}/ratio={current.ratio}"
                )


def apply_audited_soxs_cross_ticker_correction(
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    """Removes a Canadian same-ticker consolidation erroneously applied to U.S. SOXS."""
    corrected_through = int(datetime(2026, 5, 26, 13, 30, tzinfo=timezone.utc).timestamp())
    series = series_by_id["NYSE_ARCA:SOXS"]
    corrected: dict[date, DailyBar] = {}
    affected = 0
    for trading_date, bar in series.bars.items():
        if bar.timestamp >= corrected_through:
            corrected[trading_date] = bar
            continue
        corrected[trading_date] = replace(
            bar,
            open=bar.open / Decimal(15),
            high=bar.high / Decimal(15),
            low=bar.low / Decimal(15),
            close=bar.close / Decimal(15),
            adjusted_close=(
                bar.adjusted_close / Decimal(15)
                if bar.adjusted_close is not None
                else None
            ),
            volume=bar.volume * 15,
        )
        affected += 1
    series.bars = corrected
    return [
        {
            "stockId": "NYSE_ARCA:SOXS",
            "throughExclusive": instant_text(corrected_through),
            "affectedBars": affected,
            "reason": (
                "Yahoo applied the Canadian BetaPro SOXS 1:15 consolidation to the "
                "unrelated U.S. Direxion SOXS history. U.S. cross-source prices require "
                "dividing pre-2026-05-26 OHLC/adjustedClose by 15 and multiplying volume by 15."
            ),
            "usHistorySourceUrl": SOXS_US_HISTORY_URL,
            "canadianCorporateActionSourceUrl": SOXS_CANADA_CONSOLIDATION_URL,
        }
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--daily-output-dir", type=Path, default=DEFAULT_SCENARIO_DIR)
    parser.add_argument("--actions-output", type=Path, default=DEFAULT_ACTION_OUTPUT)
    parser.add_argument(
        "--raw-cache-dir",
        type=Path,
        help=(
            "Optional cache for fixed-window Yahoo JSON and NAVER text. Cache "
            "filenames include the fixed period to prevent accidental range reuse."
        ),
    )
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--retries", type=int, default=5)
    return parser.parse_args()


def load_catalog(path: Path) -> list[Instrument]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    instruments = [
        Instrument(
            order=int(item["order"]),
            symbol=str(item["symbol"]),
            market=str(item["market"]),
            name=str(item["name"]),
        )
        for item in payload["instruments"]
    ]
    instruments.sort(key=lambda item: item.order)
    if len(instruments) != EXPECTED_INSTRUMENT_COUNT:
        raise ValueError(
            f"expected {EXPECTED_INSTRUMENT_COUNT} instruments, got {len(instruments)}"
        )
    if [item.order for item in instruments] != list(range(len(instruments))):
        raise ValueError("catalog order must be unique and contiguous from zero")
    ids = [item.instrument_id for item in instruments]
    providers = [item.yahoo_symbol for item in instruments]
    if len(set(ids)) != len(ids):
        raise ValueError("catalog contains duplicate instrument IDs")
    if len(set(providers)) != len(providers):
        raise ValueError("catalog maps multiple instruments to the same Yahoo symbol")
    return instruments


def make_ssl_context() -> ssl.SSLContext:
    paths = ssl.get_default_verify_paths()
    candidates = [paths.cafile, "/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt"]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return ssl.create_default_context(cafile=candidate)
    return ssl.create_default_context()


def fetch_text(url: str, retries: int, context: ssl.SSLContext) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json,text/plain,*/*",
            "User-Agent": "KMPBook-HistoricalScenarioBuilder/1.0",
        },
    )
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=60, context=context) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code not in {408, 425, 429, 500, 502, 503, 504}:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = error
        if attempt + 1 < retries:
            time.sleep(min(8.0, 0.5 * (2**attempt)))
    raise RuntimeError(f"download failed after {retries} attempts: {url}") from last_error


def atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(text, encoding="utf-8")
    os.replace(temporary, path)


def atomic_write_bytes(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(payload)
    os.replace(temporary, path)


def yahoo_url(symbol: str) -> str:
    query = urllib.parse.urlencode(
        {
            "period1": PERIOD1,
            "period2": PERIOD2,
            "interval": "1d",
            "events": "div,splits,capitalGains",
            "includeAdjustedClose": "true",
        }
    )
    return f"{YAHOO_ENDPOINT.format(symbol=urllib.parse.quote(symbol, safe=''))}?{query}"


def naver_url(symbol: str) -> str:
    query = urllib.parse.urlencode(
        {
            "symbol": symbol,
            "requestType": 1,
            "startTime": FIRST_LOCAL_DATE.strftime("%Y%m%d"),
            "endTime": LAST_LOCAL_DATE.strftime("%Y%m%d"),
            "timeframe": "day",
        }
    )
    return f"{NAVER_ENDPOINT}?{query}"


def naver_raw_table_url(symbol: str, page: int) -> str:
    query = urllib.parse.urlencode({"code": symbol, "page": page})
    return f"https://finance.naver.com/item/sise_day.naver?{query}"


def cache_safe(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", value)


def read_validated_yahoo_cache(path: Path, instrument: Instrument) -> str | None:
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    try:
        payload = json.loads(text)
        result = payload["chart"]["result"][0]
        meta = result["meta"]
        actual_symbol = meta["symbol"]
        actual_timezone = meta["exchangeTimezoneName"]
        granularity = meta["dataGranularity"]
        timestamps = [int(value) for value in (result.get("timestamp") or [])]
    except (KeyError, IndexError, TypeError, json.JSONDecodeError):
        return None
    if (
        actual_symbol != instrument.yahoo_symbol
        or actual_timezone != instrument.expected_timezone
        or granularity != "1d"
    ):
        return None
    if instrument.instrument_id == "KOSPI:310970":
        # Yahoo carries no usable in-period history for this identifier; the
        # audited NAVER raw-table replacement is the canonical source. Retain
        # the fixed-window response instead of downloading changing quote meta
        # on every deterministic rebuild.
        return text
    if not timestamps:
        return None
    local_zone = ZoneInfo(instrument.expected_timezone)
    local_dates = [
        datetime.fromtimestamp(timestamp, tz=local_zone).date()
        for timestamp in timestamps
    ]
    if max(local_dates) < LAST_LOCAL_DATE:
        return None
    first_trade_timestamp = int(meta.get("firstTradeDate") or 0)
    if first_trade_timestamp <= PERIOD1 and min(local_dates) > FIRST_LOCAL_DATE.replace(day=8):
        # A fixed-window cache for an already-listed instrument must reach the
        # first week of the requested period. This rejects legacy six-month
        # <catalog-order>.json files that previously looked valid by symbol.
        return None
    return text


def load_or_fetch_yahoo(
    instrument: Instrument,
    raw_cache_dir: Path | None,
    retries: int,
    context: ssl.SSLContext,
) -> str:
    canonical: Path | None = None
    if raw_cache_dir is not None:
        canonical = (
            raw_cache_dir
            / f"yahoo-{PERIOD1}-{PERIOD2}"
            / f"{instrument.order:03d}-{cache_safe(instrument.yahoo_symbol)}.json"
        )
        cached = read_validated_yahoo_cache(canonical, instrument)
        if cached is not None:
            return cached
    downloaded = fetch_text(yahoo_url(instrument.yahoo_symbol), retries, context)
    if canonical is not None:
        atomic_write_text(canonical, downloaded)
    return downloaded


def load_or_fetch_naver(
    symbol: str,
    raw_cache_dir: Path | None,
    retries: int,
    context: ssl.SSLContext,
) -> str:
    cache_path: Path | None = None
    if raw_cache_dir is not None:
        cache_path = (
            raw_cache_dir
            / f"naver-{FIRST_LOCAL_DATE:%Y%m%d}-{LAST_LOCAL_DATE:%Y%m%d}"
            / f"{symbol}.txt"
        )
        if cache_path.is_file():
            return cache_path.read_text(encoding="utf-8")
    downloaded = fetch_text(naver_url(symbol), retries, context)
    if cache_path is not None:
        atomic_write_text(cache_path, downloaded)
    return downloaded


def load_or_fetch_naver_raw_table_page(
    symbol: str,
    page: int,
    raw_cache_dir: Path | None,
    retries: int,
    context: ssl.SSLContext,
) -> str:
    cache_path: Path | None = None
    if raw_cache_dir is not None:
        cache_path = (
            raw_cache_dir
            / f"naver-raw-table-{FIRST_LOCAL_DATE:%Y%m%d}-{LAST_LOCAL_DATE:%Y%m%d}"
            / f"{symbol}-{page:04d}.html"
        )
        if cache_path.is_file():
            return cache_path.read_text(encoding="utf-8")
    request = urllib.request.Request(
        naver_raw_table_url(symbol, page),
        headers={"User-Agent": "Mozilla/5.0 KMPBook-HistoricalScenarioBuilder/1.0"},
    )
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=60, context=context) as response:
                downloaded = response.read().decode("euc-kr", errors="strict")
            if cache_path is not None:
                atomic_write_text(cache_path, downloaded)
            return downloaded
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code not in {408, 425, 429, 500, 502, 503, 504}:
                raise
        except (UnicodeDecodeError, urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = error
        if attempt + 1 < retries:
            time.sleep(min(8.0, 0.5 * (2**attempt)))
    raise RuntimeError(
        f"NAVER raw daily table download failed: {symbol}/page={page}"
    ) from last_error


def as_decimal(value: Any, field: str) -> Decimal:
    if isinstance(value, Decimal):
        result = value
    elif isinstance(value, (int, float, str)):
        result = Decimal(str(value))
    else:
        raise TypeError(f"{field} is not numeric: {value!r}")
    if not result.is_finite():
        raise ValueError(f"{field} is not finite: {value!r}")
    return result


def parse_split_ratio(event: dict[str, Any]) -> Fraction:
    split_ratio = event.get("splitRatio")
    if isinstance(split_ratio, str) and ":" in split_ratio:
        numerator_text, denominator_text = split_ratio.split(":", 1)
        ratio = Fraction(Decimal(numerator_text)) / Fraction(Decimal(denominator_text))
    else:
        ratio = Fraction(as_decimal(event["numerator"], "split numerator")) / Fraction(
            as_decimal(event["denominator"], "split denominator")
        )
    if ratio <= 0 or ratio == 1:
        raise ValueError(f"invalid split ratio: {event!r}")
    return ratio


def parse_yahoo_series(instrument: Instrument, text: str) -> YahooSeries:
    payload = json.loads(text, parse_float=Decimal)
    chart = payload.get("chart")
    if not isinstance(chart, dict) or chart.get("error") is not None:
        raise ValueError(f"Yahoo returned an error for {instrument.yahoo_symbol}: {chart!r}")
    results = chart.get("result")
    if not isinstance(results, list) or len(results) != 1:
        raise ValueError(f"Yahoo returned no unique result for {instrument.yahoo_symbol}")
    result = results[0]
    meta = result["meta"]
    if meta.get("symbol") != instrument.yahoo_symbol:
        raise ValueError(
            f"Yahoo symbol redirect/mismatch: {instrument.yahoo_symbol} -> {meta.get('symbol')}"
        )
    if meta.get("exchangeTimezoneName") != instrument.expected_timezone:
        raise ValueError(
            f"Yahoo timezone mismatch for {instrument.instrument_id}: "
            f"{meta.get('exchangeTimezoneName')}"
        )
    local_zone = ZoneInfo(instrument.expected_timezone)
    timestamps = result.get("timestamp") or []
    indicators = result.get("indicators") or {}
    quote_list = indicators.get("quote") or []
    if len(quote_list) != 1:
        raise ValueError(f"Yahoo quote array missing for {instrument.yahoo_symbol}")
    quote = quote_list[0]
    adjusted_list = indicators.get("adjclose") or []
    adjusted_values = adjusted_list[0].get("adjclose", []) if adjusted_list else []
    arrays = {key: quote.get(key) or [] for key in ("open", "high", "low", "close", "volume")}
    lengths = {len(timestamps), *(len(values) for values in arrays.values())}
    if adjusted_values:
        lengths.add(len(adjusted_values))
    if len(lengths) != 1:
        raise ValueError(
            f"Yahoo indicator lengths differ for {instrument.yahoo_symbol}: {sorted(lengths)}"
        )

    bars: dict[date, DailyBar] = {}
    omitted_null_rows: list[dict[str, Any]] = []
    for index, timestamp_value in enumerate(timestamps):
        timestamp = int(timestamp_value)
        trading_date = datetime.fromtimestamp(timestamp, tz=local_zone).date()
        if not FIRST_LOCAL_DATE <= trading_date <= LAST_LOCAL_DATE:
            continue
        values = {key: arrays[key][index] for key in arrays}
        if any(value is None for value in values.values()):
            omitted_null_rows.append(
                {
                    "tradingDate": trading_date.isoformat(),
                    "nullFields": sorted(key for key, value in values.items() if value is None),
                }
            )
            continue
        adjusted_value = adjusted_values[index] if adjusted_values else None
        if trading_date in bars:
            raise ValueError(f"duplicate Yahoo date for {instrument.instrument_id}: {trading_date}")
        bars[trading_date] = DailyBar(
            trading_date=trading_date,
            timestamp=timestamp,
            open=as_decimal(values["open"], "open"),
            high=as_decimal(values["high"], "high"),
            low=as_decimal(values["low"], "low"),
            close=as_decimal(values["close"], "close"),
            adjusted_close=(
                as_decimal(adjusted_value, "adjustedClose")
                if adjusted_value is not None
                else None
            ),
            volume=int(values["volume"]),
            source_id=YAHOO_SOURCE_ID,
        )

    events = result.get("events") or {}
    split_events: list[SplitEvent] = []
    for event in (events.get("splits") or {}).values():
        split_events.append(
            SplitEvent(timestamp=int(event["date"]), ratio=parse_split_ratio(event))
        )
    split_events.sort(key=lambda item: item.timestamp)
    dividend_events = []
    for event in (events.get("dividends") or {}).values():
        amount = as_decimal(event["amount"], "dividend amount")
        if amount <= 0:
            raise ValueError(
                f"Yahoo non-positive dividend for {instrument.instrument_id}: {event!r}"
            )
        dividend_events.append(
            DividendEvent(timestamp=int(event["date"]), amount=amount)
        )
    dividend_events.sort(key=lambda item: item.timestamp)
    return YahooSeries(
        instrument=instrument,
        bars=bars,
        splits=split_events,
        dividends=dividend_events,
        raw_sha256=hashlib.sha256(text.encode("utf-8")).hexdigest(),
        omitted_null_rows=omitted_null_rows,
    )


def reconstructed_price(value: Decimal, factor: Fraction, market: str) -> Decimal:
    exact = value * Decimal(factor.numerator) / Decimal(factor.denominator)
    quantum = Decimal("1") if market in KOREAN_MARKETS else Decimal("0.01")
    return exact.quantize(quantum, rounding=ROUND_HALF_UP)


def reconstruct_raw_split_units(series: YahooSeries) -> list[dict[str, Any]]:
    if not series.splits:
        return []
    affected_by_event = {event.timestamp: 0 for event in series.splits}
    reconstructed: dict[date, DailyBar] = {}
    for trading_date, bar in series.bars.items():
        factor = Fraction(1, 1)
        pregame_factor = Fraction(1, 1)
        for event in series.splits:
            if bar.timestamp < event.timestamp:
                factor *= event.ratio
                affected_by_event[event.timestamp] += 1
                if event.timestamp < ACTION_FIRST_INSTANT:
                    pregame_factor *= event.ratio
        if factor == 1:
            reconstructed[trading_date] = replace(
                bar,
                pregame_split_adjusted_price_factor=(
                    Decimal(pregame_factor.denominator) /
                    Decimal(pregame_factor.numerator)
                ),
            )
            continue
        raw_volume = (
            Decimal(bar.volume) * Decimal(factor.denominator) / Decimal(factor.numerator)
        ).to_integral_value(rounding=ROUND_HALF_UP)
        reconstructed[trading_date] = replace(
            bar,
            open=reconstructed_price(bar.open, factor, series.instrument.market),
            high=reconstructed_price(bar.high, factor, series.instrument.market),
            low=reconstructed_price(bar.low, factor, series.instrument.market),
            close=reconstructed_price(bar.close, factor, series.instrument.market),
            volume=int(raw_volume),
            pregame_split_adjusted_price_factor=(
                Decimal(pregame_factor.denominator) / Decimal(pregame_factor.numerator)
            ),
        )
    series.bars = reconstructed
    return [
        {
            "stockId": series.instrument.instrument_id,
            "effectiveAt": instant_text(event.timestamp),
            "splitNumerator": event.numerator,
            "splitDenominator": event.denominator,
            "affectedBars": affected_by_event[event.timestamp],
        }
        for event in series.splits
    ]


def has_valid_ohlcv(bar: DailyBar) -> bool:
    return (
        min(bar.open, bar.high, bar.low, bar.close) > 0
        and bar.low <= min(bar.open, bar.close)
        and bar.high >= max(bar.open, bar.close)
        and bar.low <= bar.high
        and bar.volume >= 0
    )


def reconcile_provider_rounding_envelope(bar: DailyBar) -> DailyBar:
    required_high = max(bar.open, bar.close)
    required_low = min(bar.open, bar.close)
    high_gap = max(Decimal(0), required_high - bar.high)
    low_gap = max(Decimal(0), bar.low - required_low)
    tolerance = max(Decimal(5), required_high * Decimal("0.0001"))
    if high_gap > tolerance or low_gap > tolerance:
        return bar
    return replace(bar, high=max(bar.high, required_high), low=min(bar.low, required_low))


def annotate_raw_fallback_bar(bar: DailyBar, split_events: list[SplitEvent]) -> DailyBar:
    pregame_factor = Fraction(1, 1)
    for event in split_events:
        if bar.timestamp < event.timestamp < ACTION_FIRST_INSTANT:
            pregame_factor *= event.ratio
    return replace(
        bar,
        pregame_split_adjusted_price_factor=(
            Decimal(pregame_factor.denominator) / Decimal(pregame_factor.numerator)
        ),
    )


def apply_audited_us_missing_row_corrections(
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    stock_id = "NYSE_ARCA:XAGG"
    trading_date = date(2025, 11, 11)
    series = series_by_id[stock_id]
    omitted = next(
        (
            row
            for row in series.omitted_null_rows
            if row["tradingDate"] == trading_date.isoformat()
        ),
        None,
    )
    if omitted is None or set(omitted["nullFields"]) != {
        "open", "high", "low", "close", "volume",
    }:
        raise ValueError("Yahoo XAGG audited all-null row changed; correction must be reviewed")
    if trading_date in series.bars:
        raise ValueError("XAGG audited fallback would overwrite an existing Yahoo bar")
    bar = DailyBar(
        trading_date=trading_date,
        timestamp=int(
            datetime(2025, 11, 11, 9, 30, tzinfo=ZoneInfo("America/New_York")).timestamp()
        ),
        open=Decimal("50.09"),
        high=Decimal("50.15"),
        low=Decimal("50.05"),
        close=Decimal("50.08"),
        adjusted_close=None,
        volume=115_644,
        source_id="financecharts-xagg-history",
    )
    if not has_valid_ohlcv(bar):
        raise ValueError("audited XAGG fallback violates OHLCV invariants")
    series.bars[trading_date] = bar
    series.omitted_null_rows = [row for row in series.omitted_null_rows if row is not omitted]
    return [
        {
            "stockId": stock_id,
            "tradingDate": trading_date.isoformat(),
            "barCount": 1,
            "reason": (
                "Yahoo returned an all-null row on an open NYSE Arca session; the independent "
                "history provides raw OHLCV matching the adjacent Yahoo price unit."
            ),
            "sourceUrl": FINANCECHARTS_XAGG_HISTORY_URL,
        }
    ]


def apply_naver_invariant_corrections(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
    naver_raw_by_symbol: dict[str, dict[date, DailyBar]],
) -> list[dict[str, Any]]:
    corrections: list[dict[str, Any]] = []
    for instrument in instruments:
        if instrument.market not in KOREAN_MARKETS or instrument.symbol not in naver_raw_by_symbol:
            continue
        series = series_by_id[instrument.instrument_id]
        invalid_dates = sorted(
            trading_date
            for trading_date, bar in series.bars.items()
            if not has_valid_ohlcv(bar)
        )
        for trading_date in invalid_dates:
            source = naver_raw_by_symbol[instrument.symbol].get(trading_date)
            if source is None:
                raise ValueError(
                    f"NAVER lacks audited fallback for {instrument.instrument_id}/{trading_date}"
                )
            replacement = reconcile_provider_rounding_envelope(
                annotate_raw_fallback_bar(source, series.splits)
            )
            if not has_valid_ohlcv(replacement):
                raise ValueError(
                    f"NAVER fallback also violates OHLCV for {instrument.instrument_id}/{trading_date}"
                )
            original = series.bars[trading_date]
            series.bars[trading_date] = replacement
            corrections.append(
                {
                    "stockId": instrument.instrument_id,
                    "tradingDate": trading_date.isoformat(),
                    "reason": "Yahoo OHLC range invariant failed; audited NAVER row substituted",
                    "yahooOhlcv": [
                        json_number(original.open), json_number(original.high),
                        json_number(original.low), json_number(original.close), original.volume,
                    ],
                    "naverRawOhlcv": [
                        json_number(replacement.open), json_number(replacement.high),
                        json_number(replacement.low), json_number(replacement.close), replacement.volume,
                    ],
                }
            )
    return corrections


def apply_naver_missing_row_fills(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
    naver_raw_by_symbol: dict[str, dict[date, DailyBar]],
) -> list[dict[str, Any]]:
    summaries: list[dict[str, Any]] = []
    for instrument in instruments:
        series = series_by_id[instrument.instrument_id]
        if instrument.market not in KOREAN_MARKETS:
            continue
        naver_bars = naver_raw_by_symbol.get(instrument.symbol)
        if naver_bars is None:
            continue
        filled_dates: list[date] = []
        unresolved: list[dict[str, Any]] = []
        omitted_by_date = {
            date.fromisoformat(omitted["tradingDate"]): omitted
            for omitted in series.omitted_null_rows
        }
        candidate_dates = sorted(set(naver_bars) - set(series.bars))
        for trading_date in candidate_dates:
            source = naver_bars.get(trading_date)
            if source is None:
                omitted = omitted_by_date.get(trading_date)
                if omitted is not None:
                    unresolved.append(omitted)
                continue
            replacement = reconcile_provider_rounding_envelope(
                annotate_raw_fallback_bar(source, series.splits)
            )
            if not has_valid_ohlcv(replacement):
                raise ValueError(
                    f"NAVER missing-row fill violates OHLCV for {instrument.instrument_id}/{trading_date}"
                )
            if trading_date in series.bars:
                raise ValueError(
                    f"missing-row fill would overwrite an existing bar: {instrument.instrument_id}/{trading_date}"
                )
            series.bars[trading_date] = replacement
            filled_dates.append(trading_date)
        filled_set = set(filled_dates)
        unresolved.extend(
            omitted
            for omitted_date, omitted in omitted_by_date.items()
            if omitted_date not in filled_set and omitted not in unresolved
        )
        series.omitted_null_rows = unresolved
        if filled_dates:
            summaries.append(
                {
                    "stockId": instrument.instrument_id,
                    "barCount": len(filled_dates),
                    "dates": f"{min(filled_dates).isoformat()}..{max(filled_dates).isoformat()}",
                    "reason": "Yahoo omitted or null-filled traded KRX rows; NAVER session rows restored after split-unit normalization",
                    "unresolvedCount": len(unresolved),
                }
            )
    return summaries


def validate_audited_naver_raw_table_rows(
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    corrections: list[dict[str, Any]] = []
    for (stock_id, trading_date), expected in sorted(
        EXPECTED_NAVER_RAW_TABLE_CORRECTIONS.items(),
        key=lambda item: (item[0][0], item[0][1]),
    ):
        series = series_by_id[stock_id]
        current = series.bars.get(trading_date)
        if current is None or current.source_id != NAVER_RAW_TABLE_SOURCE_ID:
            raise ValueError(
                f"audited NAVER raw-table row source changed: {stock_id}/{trading_date}"
            )
        open_value, high_value, low_value, close_value, volume = expected
        actual = (
            int(current.open),
            int(current.high),
            int(current.low),
            int(current.close),
            current.volume,
        )
        if actual != expected:
            raise ValueError(
                f"audited NAVER raw-table row changed: {stock_id}/{trading_date}/{actual!r}"
            )
        corrections.append(
            {
                "stockId": stock_id,
                "tradingDate": trading_date.isoformat(),
                "rawOhlcv": list(expected),
                "reason": (
                    "NAVER chart API exposed dividend-adjusted ETF units; the unadjusted "
                    "NAVER daily table restores contemporaneous exchange OHLCV."
                ),
            }
        )
    return corrections


def parse_naver_bars(text: str, symbol: str) -> dict[date, DailyBar]:
    try:
        rows = ast.literal_eval(text.strip())
    except (SyntaxError, ValueError) as error:
        raise ValueError(f"NAVER response is not a literal table for {symbol}") from error
    if not isinstance(rows, list) or not rows:
        raise ValueError(f"NAVER returned no table for {symbol}")
    expected_header = ["날짜", "시가", "고가", "저가", "종가", "거래량", "외국인소진율"]
    if rows[0] != expected_header:
        raise ValueError(f"unexpected NAVER header for {symbol}: {rows[0]!r}")
    bars: dict[date, DailyBar] = {}
    for row in rows[1:]:
        if not isinstance(row, list) or len(row) < 6:
            raise ValueError(f"malformed NAVER row for {symbol}: {row!r}")
        trading_date = datetime.strptime(str(row[0]), "%Y%m%d").date()
        if not FIRST_LOCAL_DATE <= trading_date <= LAST_LOCAL_DATE:
            continue
        if trading_date in bars:
            raise ValueError(f"duplicate NAVER date for {symbol}: {trading_date}")
        bars[trading_date] = DailyBar(
            trading_date=trading_date,
            timestamp=int(
                datetime(
                    trading_date.year,
                    trading_date.month,
                    trading_date.day,
                    9,
                    tzinfo=ZoneInfo("Asia/Seoul"),
                ).timestamp()
            ),
            open=as_decimal(row[1], "NAVER open"),
            high=as_decimal(row[2], "NAVER high"),
            low=as_decimal(row[3], "NAVER low"),
            close=as_decimal(row[4], "NAVER close"),
            adjusted_close=None,
            volume=int(row[5]),
            source_id=NAVER_SOURCE_ID,
        )
    return bars


def parse_naver_raw_table_page(text: str, symbol: str, page: int) -> dict[date, DailyBar]:
    bars: dict[date, DailyBar] = {}
    for row_match in re.finditer(r"<tr[^>]*>(.*?)</tr>", text, flags=re.IGNORECASE | re.DOTALL):
        row = row_match.group(1)
        date_match = re.search(r"(20\d{2}\.\d{2}\.\d{2})", row)
        if date_match is None:
            continue
        trading_date = datetime.strptime(date_match.group(1), "%Y.%m.%d").date()
        if not FIRST_LOCAL_DATE <= trading_date <= LAST_LOCAL_DATE:
            continue
        numbers = [
            int(value.replace(",", ""))
            for value in re.findall(
                r'<span class="tah p11(?: [^"]+)?">\s*([0-9,]+)\s*</span>',
                row,
                flags=re.IGNORECASE,
            )
        ]
        if len(numbers) < 6:
            raise ValueError(
                f"malformed NAVER raw daily table row: {symbol}/page={page}/{trading_date}"
            )
        close_value, _, open_value, high_value, low_value, volume = numbers[:6]
        if trading_date in bars:
            raise ValueError(
                f"duplicate NAVER raw daily table date: {symbol}/page={page}/{trading_date}"
            )
        bars[trading_date] = DailyBar(
            trading_date=trading_date,
            timestamp=int(
                datetime(
                    trading_date.year,
                    trading_date.month,
                    trading_date.day,
                    9,
                    tzinfo=ZoneInfo("Asia/Seoul"),
                ).timestamp()
            ),
            open=Decimal(open_value),
            high=Decimal(high_value),
            low=Decimal(low_value),
            close=Decimal(close_value),
            adjusted_close=None,
            volume=volume,
            source_id=NAVER_RAW_TABLE_SOURCE_ID,
        )
    return bars


def required_naver_raw_table_targets(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
    naver_by_symbol: dict[str, dict[date, DailyBar]],
) -> dict[str, set[date]]:
    targets: dict[str, set[date]] = {}
    for instrument in instruments:
        if instrument.market not in KOREAN_MARKETS:
            continue
        series = series_by_id[instrument.instrument_id]
        naver_dates = set(naver_by_symbol[instrument.symbol])
        dates = naver_dates - set(series.bars)
        dates.update(
            trading_date
            for trading_date, bar in series.bars.items()
            if not has_valid_ohlcv(bar)
        )
        if instrument.instrument_id == "KOSPI:310970":
            dates = naver_dates
        elif instrument.instrument_id == "KOSPI:477080" and series.bars:
            yahoo_first = min(series.bars)
            dates.update(day for day in naver_dates if day < yahoo_first)
        if instrument.instrument_id == "KOSPI:012450":
            dates.update(CORRECTED_012450_DATES)
        if dates:
            targets[instrument.symbol] = dates
    return targets


def required_naver_raw_table_pages(
    target_dates_by_symbol: dict[str, set[date]],
    naver_by_symbol: dict[str, dict[date, DailyBar]],
) -> set[tuple[str, int]]:
    requests: set[tuple[str, int]] = set()
    for symbol, target_dates in target_dates_by_symbol.items():
        descending_dates = sorted(naver_by_symbol[symbol], reverse=True)
        index_by_date = {trading_date: index for index, trading_date in enumerate(descending_dates)}
        for trading_date in target_dates:
            index = index_by_date.get(trading_date)
            if index is None:
                raise ValueError(f"NAVER chart date discovery lacks target: {symbol}/{trading_date}")
            estimated_page = index // 10 + 1
            for page in range(max(1, estimated_page - 1), estimated_page + 3):
                requests.add((symbol, page))
    return requests


def merge_naver_raw_table_pages(
    pages: Iterable[tuple[str, int, str]],
) -> dict[str, dict[date, DailyBar]]:
    merged: dict[str, dict[date, DailyBar]] = {}
    for symbol, page, text in pages:
        by_date = merged.setdefault(symbol, {})
        for trading_date, bar in parse_naver_raw_table_page(text, symbol, page).items():
            existing = by_date.get(trading_date)
            if existing is not None and existing != bar:
                raise ValueError(
                    f"NAVER raw daily pages disagree: {symbol}/{trading_date}/page={page}"
                )
            by_date[trading_date] = bar
    return merged


def same_ohlcv(left: DailyBar, right: DailyBar) -> bool:
    return (
        left.open == right.open
        and left.high == right.high
        and left.low == right.low
        and left.close == right.close
        and left.volume == right.volume
    )


def apply_naver_fallbacks(
    series_by_id: dict[str, YahooSeries],
    naver_raw_by_symbol: dict[str, dict[date, DailyBar]],
) -> list[dict[str, Any]]:
    details: list[dict[str, Any]] = []

    missing = series_by_id["KOSPI:310970"]
    replacement = {
        trading_date: annotate_raw_fallback_bar(bar, missing.splits)
        for trading_date, bar in naver_raw_by_symbol["310970"].items()
    }
    if len(replacement) < 1_000 or date(2026, 7, 31) not in replacement:
        raise ValueError(
            f"310970 NAVER fallback lacks five-year/baseline coverage: {len(replacement)} bars"
        )
    missing.bars = dict(replacement)
    missing.omitted_null_rows = []
    details.append(
        {
            "stockId": "KOSPI:310970",
            "dates": f"{min(replacement).isoformat()}..{max(replacement).isoformat()}",
            "barCount": len(replacement),
            "reason": "Yahoo returned no historical rows; NAVER supplied the complete KRX calendar",
            "officialIdentityCheck": KIND_310970_URL,
            "adjustedClose": "omitted because NAVER exposes no distinct adjusted-close series",
        }
    )

    rise = series_by_id["KOSPI:477080"]
    naver_rise = {
        trading_date: annotate_raw_fallback_bar(bar, rise.splits)
        for trading_date, bar in naver_raw_by_symbol["477080"].items()
    }
    overlap_dates = sorted(set(rise.bars) & set(naver_rise))
    mismatches = [
        item.isoformat()
        for item in overlap_dates
        if not same_ohlcv(rise.bars[item], naver_rise[item])
    ]
    if mismatches:
        raise ValueError(f"477080 Yahoo/NAVER overlap mismatch: {mismatches[:5]}")
    yahoo_first = min(rise.bars)
    if yahoo_first > date(2026, 7, 16):
        raise ValueError(f"477080 Yahoo history starts later than the audited boundary: {yahoo_first}")
    inserted_dates = sorted(item for item in naver_rise if item < yahoo_first)
    for item in inserted_dates:
        rise.bars[item] = naver_rise[item]
    details.append(
        {
            "stockId": "KOSPI:477080",
            "dates": f"{inserted_dates[0].isoformat()}..{inserted_dates[-1].isoformat()}",
            "barCount": len(inserted_dates),
            "reason": (
                "Yahoo incorrectly began history at 2026-07-16; pre-gap NAVER bars "
                f"were accepted after {len(overlap_dates)} overlapping bars matched exactly"
            ),
            "officialListingCheck": RISE_477080_URL,
            "adjustedClose": "omitted on NAVER-only pre-gap bars",
        }
    )

    hanwha = series_by_id["KOSPI:012450"]
    naver_hanwha = {
        trading_date: annotate_raw_fallback_bar(bar, hanwha.splits)
        for trading_date, bar in naver_raw_by_symbol["012450"].items()
    }
    correction_reasons = {
        date(2026, 3, 27): (
            "Yahoo close 1,369,000 exceeded its own high 1,349,000; NAVER OHLCV "
            "restores the invariant and cross-source close 1,335,000"
        ),
        date(2026, 4, 1): (
            "Yahoo close 1,331,000 disagreed with NAVER and Investing.com; "
            "cross-source close is 1,333,000"
        ),
        date(2026, 6, 10): (
            "Yahoo volume 35 was an isolated truncation; NAVER reports 199,694 "
            "while OHLC matches"
        ),
    }
    corrected_rows: list[dict[str, Any]] = []
    for trading_date in sorted(CORRECTED_012450_DATES):
        naver_bar = naver_hanwha[trading_date]
        actual = (
            int(naver_bar.open),
            int(naver_bar.high),
            int(naver_bar.low),
            int(naver_bar.close),
            naver_bar.volume,
        )
        if actual != EXPECTED_012450_CORRECTIONS[trading_date]:
            raise ValueError(
                f"012450 correction source changed on {trading_date}: {actual!r}"
            )
        # No dividend or split affects these dates; corrected close is therefore
        # also the valid adjustment-aware close for this isolated replacement.
        corrected = replace(naver_bar, adjusted_close=naver_bar.close)
        hanwha.bars[trading_date] = corrected
        corrected_rows.append(
            {
                "tradingDate": trading_date.isoformat(),
                "reason": correction_reasons[trading_date],
                "ohlcv": list(actual),
            }
        )
    details.append(
        {
            "stockId": "KOSPI:012450",
            "barCount": len(corrected_rows),
            "corrections": corrected_rows,
            "filterPolicy": "only independently identified field/row anomalies are replaced",
        }
    )
    return details


def instant_text(timestamp: int) -> str:
    return datetime.fromtimestamp(timestamp, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def json_number(value: Decimal) -> int | float:
    integral = value.to_integral_value()
    return int(integral) if value == integral else float(value)


def daily_record(instrument: Instrument, bar: DailyBar) -> dict[str, Any]:
    record: dict[str, Any] = {
        "instrumentId": instrument.instrument_id,
        "tradingDate": bar.trading_date.isoformat(),
        "open": json_number(bar.open),
        "high": json_number(bar.high),
        "low": json_number(bar.low),
        "close": json_number(bar.close),
    }
    if bar.adjusted_close is not None:
        record["adjustedClose"] = json_number(bar.adjusted_close)
    if bar.pregame_split_adjusted_price_factor != 1:
        record["pregameSplitAdjustedPriceFactor"] = json_number(
            bar.pregame_split_adjusted_price_factor
        )
    record.update(
        {
            "volume": bar.volume,
            "priceBasis": "RAW",
            "sourceId": bar.source_id,
        }
    )
    return record


def validate_pregame_split_adjusted_continuity(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    allowed_extremes: list[dict[str, Any]] = []
    for instrument in instruments:
        bars = [
            bar
            for bar in sorted(
                series_by_id[instrument.instrument_id].bars.values(),
                key=lambda item: item.trading_date,
            )
            if bar.timestamp < ACTION_FIRST_INSTANT
        ]
        for previous, current in zip(bars, bars[1:]):
            previous_close = previous.close * previous.pregame_split_adjusted_price_factor
            current_close = current.close * current.pregame_split_adjusted_price_factor
            ratio = current_close / previous_close
            if Decimal("0.55") <= ratio <= Decimal("1.8"):
                continue
            key = (instrument.instrument_id, previous.trading_date, current.trading_date)
            if key not in KNOWN_EXTREME_NON_SPLIT_MOVES:
                raise ValueError(
                    "unexplained pregame split-adjusted close discontinuity: "
                    f"{instrument.instrument_id}/{previous.trading_date}->{current.trading_date} "
                    f"ratio={ratio}"
                )
            allowed_extremes.append(
                {
                    "stockId": instrument.instrument_id,
                    "from": previous.trading_date.isoformat(),
                    "through": current.trading_date.isoformat(),
                    "closeRatio": float(ratio),
                    "classification": "cross-source-confirmed leveraged/inverse product move",
                }
            )
    return allowed_extremes


def validate_cross_source_boundaries(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
) -> int:
    checked = 0
    for instrument in instruments:
        bars = sorted(
            series_by_id[instrument.instrument_id].bars.values(),
            key=lambda item: item.trading_date,
        )
        for previous, current in zip(bars, bars[1:]):
            if previous.source_id == current.source_id:
                continue
            checked += 1
            ratio = current.close / previous.close
            key = (instrument.instrument_id, previous.trading_date, current.trading_date)
            if Decimal("0.7") <= ratio <= Decimal("1.43"):
                continue
            if key in KNOWN_EXTREME_NON_SPLIT_MOVES:
                continue
            raise ValueError(
                "cross-source raw close discontinuity exceeds the audited boundary: "
                f"{instrument.instrument_id}/{previous.trading_date}->{current.trading_date} "
                f"{previous.source_id}->{current.source_id} ratio={ratio}"
            )
    return checked


def build_action_records(series_list: Iterable[YahooSeries]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for series in series_list:
        instrument = series.instrument
        id_part = f"{instrument.market.lower()}:{instrument.symbol.lower()}"
        for event in series.dividends:
            if not ACTION_FIRST_INSTANT <= event.timestamp <= ACTION_LAST_INSTANT:
                continue
            records.append(
                {
                    "id": f"yahoo:cash-dividend:{id_part}:{event.timestamp}",
                    "stockId": instrument.instrument_id,
                    "effectiveAt": instant_text(event.timestamp),
                    "kind": "CASH_DIVIDEND",
                    "cashAmount": json_number(event.amount),
                    "currency": instrument.currency,
                    "sourceId": YAHOO_SOURCE_ID,
                }
            )
        for event in series.splits:
            if not ACTION_FIRST_INSTANT <= event.timestamp <= ACTION_LAST_INSTANT:
                continue
            records.append(
                {
                    "id": f"yahoo:stock-split:{id_part}:{event.timestamp}",
                    "stockId": instrument.instrument_id,
                    "effectiveAt": instant_text(event.timestamp),
                    "kind": "STOCK_SPLIT",
                    "splitNumerator": event.numerator,
                    "splitDenominator": event.denominator,
                    "sourceId": YAHOO_SOURCE_ID,
                }
            )
    records.sort(key=lambda item: (item["effectiveAt"], item["stockId"], item["kind"], item["id"]))
    ids = [item["id"] for item in records]
    if len(ids) != len(set(ids)):
        raise ValueError("generated duplicate corporate-action IDs")
    return records


def validate_bars(
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
) -> dict[str, Any]:
    total = 0
    adjusted_count = 0
    seen: set[tuple[str, date]] = set()
    for instrument in instruments:
        series = series_by_id[instrument.instrument_id]
        if date(2026, 7, 31) not in series.bars:
            raise ValueError(f"missing 2026-07-31 baseline: {instrument.instrument_id}")
        for trading_date, bar in series.bars.items():
            key = (instrument.instrument_id, trading_date)
            if key in seen:
                raise ValueError(f"duplicate daily bar: {key}")
            seen.add(key)
            total += 1
            if bar.adjusted_close is not None:
                adjusted_count += 1
            if min(bar.open, bar.high, bar.low, bar.close) <= 0:
                raise ValueError(f"non-positive OHLC: {key}")
            if bar.low > min(bar.open, bar.close) or bar.high < max(bar.open, bar.close):
                raise ValueError(f"OHLC range invariant failed: {key}")
            if bar.low > bar.high or bar.volume < 0:
                raise ValueError(f"invalid range/volume: {key}")
            if bar.adjusted_close is not None and bar.adjusted_close <= 0:
                raise ValueError(f"invalid adjustedClose: {key}")
    target_start = date(2026, 8, 1)
    korea_reference = series_by_id["KOSPI:005930"].bars
    us_reference = series_by_id["NYSE_ARCA:SPY"].bars
    target_korea = {item for item in korea_reference if target_start <= item <= LAST_LOCAL_DATE}
    target_us = {item for item in us_reference if target_start <= item <= LAST_LOCAL_DATE}
    if len(target_korea) != 11 or len(target_us) != 12:
        raise ValueError(
            f"unexpected target calendars: Korea={len(target_korea)}, US={len(target_us)}"
        )
    for instrument in instruments:
        expected = target_korea if instrument.market in KOREAN_MARKETS else target_us
        missing = expected - set(series_by_id[instrument.instrument_id].bars)
        if missing:
            raise ValueError(
                f"target-period bars missing for {instrument.instrument_id}: "
                f"{sorted(item.isoformat() for item in missing)}"
            )

    recent_starts = {
        "KR": sorted(korea_reference)[-100],
        "US": sorted(us_reference)[-100],
    }
    incomplete: list[dict[str, Any]] = []
    for instrument in instruments:
        reference = korea_reference if instrument.market in KOREAN_MARKETS else us_reference
        expected = set(sorted(reference)[-100:])
        actual = set(series_by_id[instrument.instrument_id].bars)
        missing = sorted(expected - actual)
        if missing:
            incomplete.append(
                {
                    "stockId": instrument.instrument_id,
                    "firstAvailableDate": min(actual).isoformat(),
                    "missingReferenceSessions": len(missing),
                }
            )
    unresolved_null_rows = [
        {
            "stockId": instrument.instrument_id,
            "rows": series_by_id[instrument.instrument_id].omitted_null_rows,
        }
        for instrument in instruments
        if series_by_id[instrument.instrument_id].omitted_null_rows
    ]
    if unresolved_null_rows:
        raise ValueError(f"unresolved Yahoo null rows remain: {unresolved_null_rows!r}")
    return {
        "recordCount": total,
        "adjustedClosePresent": adjusted_count,
        "adjustedCloseOmitted": total - adjusted_count,
        "baselineComplete": len(instruments),
        "targetCalendarSessions": {"Korea": len(target_korea), "US": len(target_us)},
        "recent100StartsOn": {
            key: value.isoformat() for key, value in recent_starts.items()
        },
        "recent100CompleteInstruments": len(instruments) - len(incomplete),
        "recent100IncompleteInstruments": incomplete,
        "omittedYahooNullRows": unresolved_null_rows,
    }


def encode_resource(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def encode_compact_resource(payload: dict[str, Any]) -> bytes:
    return (
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def deterministic_gzip(payload: bytes) -> bytes:
    output = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=output, compresslevel=9, mtime=0) as stream:
        stream.write(payload)
    return output.getvalue()


def hash_bytes(payload: bytes) -> dict[str, Any]:
    return {"bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}


def hash_file_bytes(text: str) -> dict[str, Any]:
    encoded = text.encode("utf-8")
    return {"bytes": len(encoded), "sha256": hashlib.sha256(encoded).hexdigest()}


def write_daily_shards(
    output_dir: Path,
    instruments: list[Instrument],
    series_by_id: dict[str, YahooSeries],
) -> list[dict[str, Any]]:
    output_dir.mkdir(parents=True, exist_ok=True)
    written_paths: set[Path] = set()
    shards: list[dict[str, Any]] = []
    for start in range(0, len(instruments), DAILY_SHARD_INSTRUMENT_COUNT):
        shard_instruments = instruments[start : start + DAILY_SHARD_INSTRUMENT_COUNT]
        end = start + len(shard_instruments) - 1
        records = [
            daily_record(instrument, series_by_id[instrument.instrument_id].bars[trading_date])
            for instrument in shard_instruments
            for trading_date in sorted(series_by_id[instrument.instrument_id].bars)
        ]
        if not records or len(records) > MAX_DAILY_RECORDS_PER_SHARD:
            raise ValueError(
                f"daily shard {start:03d}-{end:03d} has invalid record count: {len(records)}"
            )
        uncompressed = encode_compact_resource({"schemaVersion": 1, "records": records})
        if len(uncompressed) > MAX_RESOURCE_BYTES:
            raise ValueError(
                f"daily shard {start:03d}-{end:03d} exceeds the 32 MiB expansion limit"
            )
        compressed = deterministic_gzip(uncompressed)
        if len(compressed) > MAX_RESOURCE_BYTES:
            raise ValueError(
                f"daily shard {start:03d}-{end:03d} exceeds the 32 MiB bundle limit"
            )
        filename = f"daily_bars_{start:03d}_{end:03d}_v2.json.gz"
        path = output_dir / filename
        atomic_write_bytes(path, compressed)
        written_paths.add(path.resolve())
        shards.append(
            {
                "kind": "DAILY_BARS",
                "path": f"files/scenarios/august_2026/{filename}",
                "contentSha256": hashlib.sha256(compressed).hexdigest(),
                "recordCount": len(records),
                "compressedBytes": len(compressed),
                "uncompressedBytes": len(uncompressed),
                "firstCatalogOrder": shard_instruments[0].order,
                "lastCatalogOrder": shard_instruments[-1].order,
            }
        )
    for stale in output_dir.glob("daily_bars_*_v2.json.gz"):
        if stale.resolve() not in written_paths:
            stale.unlink()
    return shards


def main() -> int:
    args = parse_args()
    if args.workers < 1:
        raise ValueError("--workers must be positive")
    if args.retries < 1:
        raise ValueError("--retries must be positive")
    instruments = load_catalog(args.catalog.resolve())
    context = make_ssl_context()

    yahoo_text_by_id: dict[str, str] = {}
    failures: list[str] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_by_instrument = {
            executor.submit(
                load_or_fetch_yahoo,
                instrument,
                args.raw_cache_dir,
                args.retries,
                context,
            ): instrument
            for instrument in instruments
        }
        for future in concurrent.futures.as_completed(future_by_instrument):
            instrument = future_by_instrument[future]
            try:
                yahoo_text_by_id[instrument.instrument_id] = future.result()
            except Exception as error:  # Report all failed symbols together.
                failures.append(f"{instrument.instrument_id}/{instrument.yahoo_symbol}: {error}")
    if failures:
        raise RuntimeError("Yahoo downloads failed:\n" + "\n".join(sorted(failures)))

    series_by_id = {
        instrument.instrument_id: parse_yahoo_series(
            instrument, yahoo_text_by_id[instrument.instrument_id]
        )
        for instrument in instruments
    }
    provider_split_event_corrections = apply_audited_yahoo_split_event_corrections(series_by_id)
    validate_no_duplicate_split_event_markers(series_by_id)
    cross_ticker_corrections = apply_audited_soxs_cross_ticker_correction(series_by_id)
    split_reconstruction: list[dict[str, Any]] = []
    for instrument in instruments:
        split_reconstruction.extend(
            reconstruct_raw_split_units(series_by_id[instrument.instrument_id])
        )

    naver_symbols = {
        instrument.symbol
        for instrument in instruments
        if instrument.market in KOREAN_MARKETS
    }
    naver_text_by_symbol: dict[str, str] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(6, len(naver_symbols))) as executor:
        future_by_symbol = {
            executor.submit(
                load_or_fetch_naver,
                symbol,
                args.raw_cache_dir,
                args.retries,
                context,
            ): symbol
            for symbol in sorted(naver_symbols)
        }
        for future in concurrent.futures.as_completed(future_by_symbol):
            symbol = future_by_symbol[future]
            naver_text_by_symbol[symbol] = future.result()
    naver_by_symbol = {
        symbol: parse_naver_bars(text, symbol)
        for symbol, text in naver_text_by_symbol.items()
    }
    naver_raw_targets = required_naver_raw_table_targets(
        instruments,
        series_by_id,
        naver_by_symbol,
    )
    naver_raw_requests = required_naver_raw_table_pages(
        naver_raw_targets,
        naver_by_symbol,
    )
    naver_raw_text_by_request: dict[tuple[str, int], str] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_by_request = {
            executor.submit(
                load_or_fetch_naver_raw_table_page,
                symbol,
                page,
                args.raw_cache_dir,
                args.retries,
                context,
            ): (symbol, page)
            for symbol, page in sorted(naver_raw_requests)
        }
        for future in concurrent.futures.as_completed(future_by_request):
            request_key = future_by_request[future]
            naver_raw_text_by_request[request_key] = future.result()
    naver_raw_by_symbol = merge_naver_raw_table_pages(
        (symbol, page, text)
        for (symbol, page), text in naver_raw_text_by_request.items()
    )
    missing_raw_targets = {
        symbol: sorted(target_dates - set(naver_raw_by_symbol.get(symbol, {})))
        for symbol, target_dates in naver_raw_targets.items()
        if not target_dates.issubset(naver_raw_by_symbol.get(symbol, {}))
    }
    if missing_raw_targets:
        raise ValueError(f"NAVER raw daily table lacks target rows: {missing_raw_targets!r}")

    fallback_details = apply_audited_us_missing_row_corrections(series_by_id)
    fallback_details.extend(apply_naver_fallbacks(series_by_id, naver_raw_by_symbol))
    missing_row_fills = apply_naver_missing_row_fills(
        instruments,
        series_by_id,
        naver_raw_by_symbol,
    )
    if missing_row_fills:
        fallback_details.append(
            {
                "kind": "NAVER_MISSING_ROW_FILLS",
                "stockCount": len(missing_row_fills),
                "barCount": sum(item["barCount"] for item in missing_row_fills),
                "stocks": missing_row_fills,
            }
        )
    audited_raw_table_rows = validate_audited_naver_raw_table_rows(series_by_id)
    if audited_raw_table_rows:
        fallback_details.append(
            {
                "kind": "NAVER_RAW_DAILY_TABLE_AUDIT_SAMPLES",
                "barCount": len(audited_raw_table_rows),
                "rows": audited_raw_table_rows,
            }
        )
    invariant_corrections = apply_naver_invariant_corrections(
        instruments,
        series_by_id,
        naver_raw_by_symbol,
    )
    if invariant_corrections:
        fallback_details.append(
            {
                "kind": "AUDITED_OHLC_INVARIANT_CORRECTIONS",
                "barCount": len(invariant_corrections),
                "corrections": invariant_corrections,
            }
        )

    allowed_extreme_moves = validate_pregame_split_adjusted_continuity(
        instruments,
        series_by_id,
    )
    cross_source_boundaries_checked = validate_cross_source_boundaries(
        instruments,
        series_by_id,
    )
    validation = validate_bars(instruments, series_by_id)
    daily_shards = write_daily_shards(
        args.daily_output_dir.resolve(),
        instruments,
        series_by_id,
    )
    if sum(shard["recordCount"] for shard in daily_shards) != validation["recordCount"]:
        raise ValueError("daily shard record total differs from validated history")

    action_records = build_action_records(series_by_id.values())
    kind_counts = {
        kind: sum(1 for item in action_records if item["kind"] == kind)
        for kind in ("CASH_DIVIDEND", "STOCK_SPLIT")
    }
    if len(action_records) != EXPECTED_GAME_ACTION_COUNT:
        raise ValueError(
            f"expected {EXPECTED_GAME_ACTION_COUNT} game-period actions, got {len(action_records)}"
        )
    if kind_counts != {"CASH_DIVIDEND": EXPECTED_GAME_DIVIDEND_COUNT, "STOCK_SPLIT": 1}:
        raise ValueError(f"unexpected game-period action counts: {kind_counts}")
    game_split = [item for item in action_records if item["kind"] == "STOCK_SPLIT"]
    if not (
        len(game_split) == 1
        and game_split[0]["stockId"] == "KOSDAQ:196170"
        and game_split[0]["effectiveAt"] == "2026-08-05T00:00:00Z"
        and game_split[0]["splitNumerator"] == 13
        and game_split[0]["splitDenominator"] == 10
    ):
        raise ValueError(f"unexpected game-period split: {game_split!r}")

    action_text = encode_resource({"schemaVersion": 1, "records": action_records})
    if len(action_text.encode("utf-8")) > MAX_RESOURCE_BYTES:
        raise ValueError("corporate-action resource exceeds the parser limit")
    atomic_write_text(args.actions_output.resolve(), action_text)

    raw_entries = [
        {
            "provider": "Yahoo Finance",
            "providerSymbol": instrument.yahoo_symbol,
            "sha256": series_by_id[instrument.instrument_id].raw_sha256,
        }
        for instrument in instruments
    ] + [
        {
            "provider": "NAVER Finance",
            "providerSymbol": symbol,
            "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        }
        for symbol, text in sorted(naver_text_by_symbol.items())
    ] + [
        {
            "provider": "NAVER Finance unadjusted daily table",
            "providerSymbol": symbol,
            "page": page,
            "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        }
        for (symbol, page), text in sorted(naver_raw_text_by_request.items())
    ]
    raw_manifest_hash = hashlib.sha256(
        json.dumps(raw_entries, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
            "utf-8"
        )
    ).hexdigest()
    output = {
        "fixedRequest": {
            "period1": PERIOD1,
            "period2Exclusive": PERIOD2,
            "localTradingDateThrough": LAST_LOCAL_DATE.isoformat(),
            "actionInstantRange": [
                instant_text(ACTION_FIRST_INSTANT),
                instant_text(ACTION_LAST_INSTANT),
            ],
        },
        "catalog": {
            "instrumentCount": len(instruments),
            "uniqueYahooSymbols": len({item.yahoo_symbol for item in instruments}),
        },
        "dailyBars": validation,
        "corporateActions": {
            "recordCount": len(action_records),
            "kindCounts": kind_counts,
        },
        "splitRawReconstruction": {
            "eventCount": len(split_reconstruction),
            "events": split_reconstruction,
            "auditedProviderEventCorrections": provider_split_event_corrections,
            "auditedProviderCorrections": cross_ticker_corrections,
            "ohlcRule": (
                "multiply pre-effective Yahoo quote OHLC by cumulative future split ratio; "
                "round half-up to nearest KRW (Korea) or USD cent (US)"
            ),
            "volumeRule": (
                "divide pre-effective Yahoo quote volume by cumulative future split ratio; "
                "round half-up to whole shares; provider-side adjusted-volume rounding is "
                "irreversible and can leave several/tens-of-shares tape differences"
            ),
            "adjustedCloseRule": "preserve Yahoo adjustedClose unchanged",
        },
        "pregameContinuityAudit": {
            "extremeMoveThresholds": [0.55, 1.8],
            "allowedCrossSourceConfirmedMoves": allowed_extreme_moves,
            "crossSourceBoundaryCount": cross_source_boundaries_checked,
            "crossSourceRawCloseRatioBounds": [0.7, 1.43],
        },
        "fallbacksAndCorrections": fallback_details,
        "rawInputManifest": {
            "entryCount": len(raw_entries),
            "sha256": raw_manifest_hash,
        },
        "outputs": {
            "dailyShards": daily_shards,
            str(args.actions_output.resolve()): hash_file_bytes(action_text),
        },
        "requiredSourceCatalogEntries": [
            {
                "id": YAHOO_SOURCE_ID,
                "publisher": "Yahoo Finance",
                "title": "Yahoo Finance Chart API historical daily bars and corporate actions",
                "url": "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}",
                "accessedOn": "2026-08-19",
                "note": (
                    f"fixed period1={PERIOD1}, period2={PERIOD2}, interval=1d, "
                    "events=div,splits,capitalGains, includeAdjustedClose=true; market-local "
                    f"date <=2026-08-18. All {len(split_reconstruction)} provider-reported "
                    "in-window splits were reversed to RAW OHLCV "
                    "using cumulative ratios; OHLC and inverse-volume half-up rounding follows "
                    "the generator. Adjusted integer volume cannot be inverted losslessly and "
                    "may differ from tape by several/tens of shares; Yahoo adjustedClose is "
                    "preserved."
                ),
            },
            {
                "id": NAVER_SOURCE_ID,
                "publisher": "NAVER Finance",
                "title": "NAVER Finance daily price chart",
                "url": NAVER_ENDPOINT,
                "accessedOn": "2026-08-19",
                "note": (
                    "Five-year KRX date-discovery and cross-check input. The chart endpoint is "
                    "not used as an OHLCV source because it can expose dividend-adjusted ETF "
                    "prices and volumes; every substituted row is read from the separately "
                    "catalogued unadjusted NAVER daily table."
                ),
            },
            {
                "id": NAVER_RAW_TABLE_SOURCE_ID,
                "publisher": "NAVER Finance",
                "title": "NAVER Finance unadjusted daily price table",
                "url": NAVER_RAW_TABLE_ENDPOINT,
                "accessedOn": "2026-08-19",
                "note": (
                    "Unadjusted HTML daily table used for complete 310970 history, 477080 before "
                    "Yahoo history begins, three independently checked 012450 anomalies, invalid "
                    "Yahoo OHLC envelopes, and every traded KRX row omitted or null-filled by "
                    "Yahoo. Six covered-call ETF gaps are retained as fixed audit samples proving "
                    "that the chart endpoint's dividend-adjusted units must not be mixed with RAW "
                    "OHLCV. adjustedClose is omitted because this table has no separate adjusted "
                    "series."
                ),
            },
            {
                "id": "financecharts-xagg-history",
                "publisher": "FinanceCharts",
                "title": "Eaton Vance Income Opportunities ETF historical prices",
                "url": FINANCECHARTS_XAGG_HISTORY_URL,
                "accessedOn": "2026-08-19",
                "note": (
                    "Audited fallback for XAGG on 2025-11-11, when Yahoo emitted an all-null "
                    "row despite an open NYSE Arca session. OHLCV 50.09/50.15/50.05/50.08 and "
                    "115,644 shares matches the adjacent raw price unit; adjustedClose is "
                    "omitted because this source does not provide Yahoo's total-return series."
                ),
            },
            {
                "id": "samsung-biologics-equity-spinoff",
                "publisher": "Samsung Biologics / Korea Exchange KIND",
                "title": "Samsung Biologics equity spin-off and surviving-company consolidation",
                "url": SAMSUNG_BIO_SPINOFF_URL,
                "accessedOn": "2026-08-19",
                "note": (
                    "Official filing confirms one 0.6503913 surviving-company share "
                    "consolidation. Used to remove Yahoo's duplicate 2025-09-29 marker while "
                    "retaining the revised 2025-10-30 trading-halt boundary marker."
                ),
            },
            {
                "id": "stockanalysis-soxs-history",
                "publisher": "StockAnalysis",
                "title": "Direxion Daily Semiconductor Bear 3X ETF historical prices",
                "url": SOXS_US_HISTORY_URL,
                "accessedOn": "2026-08-19",
                "note": (
                    "U.S. SOXS cross-check used to remove a 15x same-ticker Canadian "
                    "corporate-action contamination from Yahoo's pre-2026-05-26 rows."
                ),
            },
            {
                "id": "global-x-canada-soxs-consolidation",
                "publisher": "Global X Investments Canada",
                "title": "Global X ETFs Announces Share Consolidations and Splits",
                "url": SOXS_CANADA_CONSOLIDATION_URL,
                "accessedOn": "2026-08-19",
                "note": (
                    "Confirms that the 1:15 SOXS consolidation belonged to the Canadian "
                    "BetaPro -3x Semiconductor Daily Leveraged Bear Alternative ETF, not "
                    "the U.S. Direxion fund in this catalog."
                ),
            },
        ],
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, TypeError, ValueError, KeyError, IndexError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1) from error
