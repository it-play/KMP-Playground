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

NAVER Finance is used only for the documented Yahoo gaps/corrections below.
Its endpoint does not expose a separate adjusted-close series, so adjustedClose
is omitted on NAVER-only bars rather than fabricating one.

This script uses only the Python standard library. It writes deterministic JSON
(stable catalog/date order and no retrieval timestamp) and reports hashes plus
the source-catalog entries that the scenario manifest must reference.
"""

from __future__ import annotations

import argparse
import ast
import concurrent.futures
import hashlib
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


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = (
    REPO_ROOT
    / "composeApp/src/commonMain/composeResources/files/instruments/market_instrument_catalog_v6.json"
)
DEFAULT_DAILY_OUTPUT = (
    REPO_ROOT
    / "composeApp/src/commonMain/composeResources/files/scenarios/august_2026/daily_bars_v1.json"
)
DEFAULT_ACTION_OUTPUT = (
    REPO_ROOT
    / "composeApp/src/commonMain/composeResources/files/scenarios/august_2026/corporate_actions_v1.json"
)

PERIOD1 = 1_769_904_000  # 2026-02-01T00:00:00Z
PERIOD2 = 1_787_184_000  # 2026-08-20T00:00:00Z (exclusive)
FIRST_LOCAL_DATE = date(2026, 2, 1)
LAST_LOCAL_DATE = date(2026, 8, 18)
ACTION_FIRST_INSTANT = int(datetime(2026, 8, 1, tzinfo=timezone.utc).timestamp())
ACTION_LAST_INSTANT = int(datetime(2026, 8, 18, 20, tzinfo=timezone.utc).timestamp())

YAHOO_SOURCE_ID = "yahoo-finance-chart-api"
NAVER_SOURCE_ID = "naver-finance-daily-chart"
YAHOO_ENDPOINT = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"
NAVER_ENDPOINT = "https://api.finance.naver.com/siseJson.naver"
KIND_310970_URL = (
    "https://kind.krx.co.kr/disclosure/etfisudetail.do?"
    "method=searchEtfIsuSummary&strIsurCd=31097"
)
RISE_477080_URL = "https://www.riseetf.co.kr/prod/finderDetail/44G4"

EXPECTED_INSTRUMENT_COUNT = 554
EXPECTED_DAILY_RECORD_COUNT = 74_100
EXPECTED_PERIOD_SPLIT_COUNT = 11
EXPECTED_GAME_ACTION_COUNT = 111
EXPECTED_GAME_DIVIDEND_COUNT = 110
MAX_RESOURCE_BYTES = 32 * 1024 * 1024
MAX_DAILY_RECORDS = 100_000

KOREAN_MARKETS = frozenset({"KOSPI", "KOSDAQ"})
CORRECTED_012450_DATES = frozenset(
    {date(2026, 3, 27), date(2026, 4, 1), date(2026, 6, 10)}
)
EXPECTED_012450_CORRECTIONS = {
    date(2026, 3, 27): (1_349_000, 1_349_000, 1_287_000, 1_335_000, 205_178),
    date(2026, 4, 1): (1_323_000, 1_364_000, 1_283_000, 1_333_000, 243_803),
    date(2026, 6, 10): (1_038_000, 1_066_000, 1_004_000, 1_031_000, 199_694),
}


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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--daily-output", type=Path, default=DEFAULT_DAILY_OUTPUT)
    parser.add_argument("--actions-output", type=Path, default=DEFAULT_ACTION_OUTPUT)
    parser.add_argument(
        "--raw-cache-dir",
        type=Path,
        help=(
            "Optional cache for fixed-window Yahoo JSON and NAVER text. Cache "
            "filenames include the fixed period to prevent accidental range reuse."
        ),
    )
    parser.add_argument(
        "--indexed-yahoo-cache-dir",
        type=Path,
        help=(
            "Optional read-only cache whose files are <catalog-order>.json. Each "
            "entry is accepted only after its Yahoo meta.symbol is validated."
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


def cache_safe(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", value)


def read_validated_index_cache(path: Path, expected_symbol: str) -> str | None:
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    try:
        payload = json.loads(text)
        result = payload["chart"]["result"][0]
        actual_symbol = result["meta"]["symbol"]
    except (KeyError, IndexError, TypeError, json.JSONDecodeError):
        return None
    return text if actual_symbol == expected_symbol else None


def load_or_fetch_yahoo(
    instrument: Instrument,
    raw_cache_dir: Path | None,
    indexed_cache_dir: Path | None,
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
        cached = read_validated_index_cache(canonical, instrument.yahoo_symbol)
        if cached is not None:
            return cached
    if indexed_cache_dir is not None:
        indexed = indexed_cache_dir / f"{instrument.order}.json"
        cached = read_validated_index_cache(indexed, instrument.yahoo_symbol)
        if cached is not None:
            if canonical is not None:
                atomic_write_text(canonical, cached)
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
    for index, timestamp_value in enumerate(timestamps):
        timestamp = int(timestamp_value)
        trading_date = datetime.fromtimestamp(timestamp, tz=local_zone).date()
        if not FIRST_LOCAL_DATE <= trading_date <= LAST_LOCAL_DATE:
            continue
        values = {key: arrays[key][index] for key in arrays}
        if any(value is None for value in values.values()):
            raise ValueError(
                f"Yahoo null OHLCV for {instrument.instrument_id} on {trading_date.isoformat()}"
            )
        adjusted_value = adjusted_values[index] if adjusted_values else None
        if adjusted_values and adjusted_value is None:
            raise ValueError(
                f"Yahoo null adjustedClose for {instrument.instrument_id} on {trading_date}"
            )
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
        for event in series.splits:
            if bar.timestamp < event.timestamp:
                factor *= event.ratio
                affected_by_event[event.timestamp] += 1
        if factor == 1:
            reconstructed[trading_date] = bar
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
    naver_by_symbol: dict[str, dict[date, DailyBar]],
) -> list[dict[str, Any]]:
    details: list[dict[str, Any]] = []

    missing = series_by_id["KOSPI:310970"]
    replacement = naver_by_symbol["310970"]
    if len(replacement) != 132:
        raise ValueError(f"310970 NAVER fallback expected 132 bars, got {len(replacement)}")
    missing.bars = dict(replacement)
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
    naver_rise = naver_by_symbol["477080"]
    overlap_dates = sorted(set(rise.bars) & set(naver_rise))
    mismatches = [
        item.isoformat()
        for item in overlap_dates
        if not same_ohlcv(rise.bars[item], naver_rise[item])
    ]
    if mismatches:
        raise ValueError(f"477080 Yahoo/NAVER overlap mismatch: {mismatches[:5]}")
    yahoo_first = min(rise.bars)
    if yahoo_first != date(2026, 7, 16):
        raise ValueError(f"477080 unexpected Yahoo first date: {yahoo_first}")
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
    naver_hanwha = naver_by_symbol["012450"]
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
    record.update(
        {
            "volume": bar.volume,
            "priceBasis": "RAW",
            "sourceId": bar.source_id,
        }
    )
    return record


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
    if total != EXPECTED_DAILY_RECORD_COUNT:
        raise ValueError(f"expected {EXPECTED_DAILY_RECORD_COUNT} daily bars, got {total}")

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
    }


def encode_resource(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def hash_file_bytes(text: str) -> dict[str, Any]:
    encoded = text.encode("utf-8")
    return {"bytes": len(encoded), "sha256": hashlib.sha256(encoded).hexdigest()}


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
                args.indexed_yahoo_cache_dir,
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
    split_reconstruction: list[dict[str, Any]] = []
    for instrument in instruments:
        split_reconstruction.extend(
            reconstruct_raw_split_units(series_by_id[instrument.instrument_id])
        )
    if len(split_reconstruction) != EXPECTED_PERIOD_SPLIT_COUNT:
        raise ValueError(
            f"expected {EXPECTED_PERIOD_SPLIT_COUNT} split events in window, "
            f"got {len(split_reconstruction)}"
        )

    naver_text_by_symbol: dict[str, str] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        future_by_symbol = {
            executor.submit(
                load_or_fetch_naver,
                symbol,
                args.raw_cache_dir,
                args.retries,
                context,
            ): symbol
            for symbol in ("310970", "477080", "012450")
        }
        for future in concurrent.futures.as_completed(future_by_symbol):
            symbol = future_by_symbol[future]
            naver_text_by_symbol[symbol] = future.result()
    naver_by_symbol = {
        symbol: parse_naver_bars(text, symbol)
        for symbol, text in naver_text_by_symbol.items()
    }
    fallback_details = apply_naver_fallbacks(series_by_id, naver_by_symbol)

    validation = validate_bars(instruments, series_by_id)
    records = [
        daily_record(instrument, series_by_id[instrument.instrument_id].bars[trading_date])
        for instrument in instruments
        for trading_date in sorted(series_by_id[instrument.instrument_id].bars)
    ]
    if len(records) > MAX_DAILY_RECORDS:
        raise ValueError(f"daily record count exceeds parser limit: {len(records)}")

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

    daily_text = encode_resource({"schemaVersion": 1, "records": records})
    action_text = encode_resource({"schemaVersion": 1, "records": action_records})
    if len(daily_text.encode("utf-8")) > MAX_RESOURCE_BYTES:
        raise ValueError("daily resource exceeds the desktop parser's 32 MiB limit")
    if len(action_text.encode("utf-8")) > MAX_RESOURCE_BYTES:
        raise ValueError("corporate-action resource exceeds the parser limit")
    atomic_write_text(args.daily_output.resolve(), daily_text)
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
        "fallbacksAndCorrections": fallback_details,
        "rawInputManifest": {
            "entryCount": len(raw_entries),
            "sha256": raw_manifest_hash,
        },
        "outputs": {
            str(args.daily_output.resolve()): hash_file_bytes(daily_text),
            str(args.actions_output.resolve()): hash_file_bytes(action_text),
        },
        "requiredSourceCatalogEntries": [
            {
                "id": YAHOO_SOURCE_ID,
                "publisher": "Yahoo Finance",
                "title": "Yahoo Finance Chart API historical daily bars and corporate actions",
                "url": "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}",
                "accessedOn": "2026-08-19",
                "note": (
                    f"fixed period1={PERIOD1}, period2={PERIOD2}, interval=1d, "
                    "events=div,splits,capitalGains, includeAdjustedClose=true; market-local "
                    "date <=2026-08-18. All 11 in-window splits were reversed to RAW OHLCV "
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
                    "Fallback for 310970, 477080 before 2026-07-16, and three independently "
                    "cross-checked 012450 anomalies. NAVER-only bars omit adjustedClose because "
                    "the endpoint exposes no separate adjusted-close series."
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
