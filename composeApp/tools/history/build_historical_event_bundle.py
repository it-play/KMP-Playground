#!/usr/bin/env python3
"""Build deterministic August 2026 historical source and event resources.

The research candidate files intentionally carry annotations that are useful
while auditing the material but are not part of the runtime JSON contract.
This builder whitelists the runtime fields, merges any number of candidate
files with the existing scenario resources, and validates all cross-resource
references before replacing either output file.

Only the Python standard library is required.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import os
import re
import sys
import tempfile
import unicodedata
import urllib.parse
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any, Iterable, NoReturn


COMPOSE_APP_ROOT = Path(__file__).resolve().parents[2]
SCENARIO_DIR = (
    COMPOSE_APP_ROOT
    / "src/commonMain/composeResources/files/scenarios/august_2026"
)
DEFAULT_BASE_SOURCES = SCENARIO_DIR / "sources_v2.json"
DEFAULT_BASE_EVENTS = SCENARIO_DIR / "events_v2.json"
DEFAULT_CATALOG = (
    COMPOSE_APP_ROOT
    / "src/commonMain/composeResources/files/instruments/market_instrument_catalog_v6.json"
)
DEFAULT_SOURCES_OUTPUT = SCENARIO_DIR / "sources_v2.json"
DEFAULT_EVENTS_OUTPUT = SCENARIO_DIR / "events_v2.json"

SCHEMA_VERSION = 1
PUBLISHED_AT_START = datetime(2026, 7, 1, tzinfo=timezone.utc)
HISTORICAL_THROUGH = datetime(2026, 8, 18, 20, tzinfo=timezone.utc)

SOURCE_FIELDS = (
    "id",
    "publisher",
    "title",
    "url",
    "publishedOn",
    "accessedOn",
    "note",
)
EVENT_FIELDS = (
    "id",
    "title",
    "summary",
    "type",
    "severity",
    "occurredAt",
    "publishedAt",
    "priceEffectPolicy",
    "affectedMarkets",
    "affectedInstrumentIds",
    "sourceIds",
    "reportedFacts",
    "marketReactions",
)
REPORTED_FACT_FIELDS = ("label", "actual", "comparison")
MARKET_REACTION_FIELDS = ("market", "priceDiscoveryAt", "observedTradingDate")

MARKETS = (
    "KOSPI",
    "KOSDAQ",
    "NASDAQ",
    "NYSE",
    "NYSE_ARCA",
    "CBOE_BZX",
    "NYSE_AMERICAN",
)
MARKET_ORDER = {value: index for index, value in enumerate(MARKETS)}
EVENT_TYPES = frozenset(
    {
        "ECONOMIC_INDICATOR",
        "CENTRAL_BANK",
        "GEOPOLITICAL",
        "REGULATION_POLICY",
        "EARNINGS",
        "CORPORATE_ACTION",
        "PRODUCT_TECHNOLOGY",
        "INDUSTRY_SUPPLY_DEMAND",
        "CURRENCY",
        "COMMODITY",
        "NATURAL_DISASTER",
        "HEALTH_CRISIS",
        "MARKET_SENTIMENT",
        "FUND_OPERATION",
    }
)
EVENT_SEVERITIES = frozenset({"MINOR", "MODERATE", "MAJOR", "CRITICAL"})
PRICE_EFFECT_POLICIES = frozenset(
    {"EMBEDDED_WHERE_ANCHORED", "INFORMATION_ONLY"}
)

SOURCE_ID_PATTERN = re.compile(r"[a-z0-9][a-z0-9._:-]{2,127}\Z")
EVENT_ID_PATTERN = re.compile(r"[a-z0-9][a-z0-9._:-]{2,191}\Z")
HISTORY_SOURCE_OVERRIDE_IDS = frozenset(
    {"yahoo-finance-chart-api", "naver-finance-daily-chart"}
)
CORPORATE_READY_STATES = frozenset({"READY", "NEEDS_EXACT_PUBLICATION_TIME"})
CORPORATE_TIMESTAMP_PRECISIONS = frozenset(
    {"SECOND", "MINUTE", "DATE_ONLY_NORMALIZED_NOON_UTC"}
)
CORPORATE_INFORMATION_ONLY_RECOMMENDATION = (
    "INFORMATION_ONLY_WITH_HISTORICAL_DAILY_BAR_ANCHOR"
)
CORPORATE_SOURCE_PUBLISHERS = {
    "PRIMARY_SEC_8_K": "U.S. Securities and Exchange Commission",
    "PRIMARY_SEC_6_K": "U.S. Securities and Exchange Commission",
    "PRIMARY_KRX_KIND": "Korea Exchange KIND",
    "PRIMARY_US_TREASURY": "U.S. Department of the Treasury",
    "PRIMARY_US_TREASURY_BULLETIN": "U.S. Department of the Treasury",
    "PRIMARY_US_TREASURY_OFAC": (
        "U.S. Department of the Treasury, Office of Foreign Assets Control"
    ),
    "PRIMARY_OPEC": "OPEC",
    "PRIMARY_INDEX_PROVIDER": "S&P Global",
    "PRIMARY_GOVERNMENT_WIRE": "Saudi Press Agency",
    "PRIMARY_AUTHORIZED_DISTRIBUTION": "PR Newswire",
}
CORPORATE_HOST_PUBLISHER_KINDS = frozenset(
    {"PRIMARY_COMPANY_IR", "PRIMARY_COMPANY_NEWSROOM", "PRIMARY_COMPANY_RSS"}
)

# Cross-research duplicates reviewed using publication-time proximity, shared
# instruments/markets, and normalized-title similarity. The corporate bundle
# owns the canonical IDs because it carries exact primary-source timestamps;
# the gap record's sources, facts, coverage, and higher severity are merged in.
# SK Hynix's aggregate 54-trillion-won item is the sole one-to-many exception:
# its two separately timed investment decisions remain as distinct events.
SEMANTIC_DUPLICATE_TARGETS: dict[str, tuple[str, ...]] = {
    "tesla-deliveries": ("tesla-q2-deliveries-production-2026-07-02",),
    "nvda-transition": ("nvidia-field-operations-leadership-2026-07-02",),
    "avgo-apple-asic": ("broadcom-apple-custom-asic-agreements-2026-07-06",),
    "samsung-guidance": ("samsung-q2-preliminary-2026-07-07",),
    "o-euro-notes": ("realty-income-euro-notes-2026-07-07",),
    "orc-div-july": ("orc-july-distribution-2026-07-08",),
    "tsm-june-revenue": ("tsmc-june-revenue-2026-07-13",),
    "o-revolver": ("realty-income-revolver-upsize-2026-07-13",),
    "orc-prelim": ("orc-june-book-value-estimate-2026-07-13",),
    "jpm-q2": ("jpmorgan-q2-results-2026-07-14",),
    "unh-q2": ("unitedhealth-q2-results-2026-07-16",),
    "tsm-q2": ("tsmc-q2-results-2026-07-16",),
    "ko-ransomware": ("coca-cola-fairlife-ransomware-2026-07-16",),
    "sambio-acquisition": ("samsung-biologics-polypeptide-offer-2026-07-20",),
    "att-q2": ("att-q2-results-2026-07-22",),
    "goog-q2": ("alphabet-q2-results-2026-07-22",),
    "tsla-q2": ("tesla-q2-results-2026-07-22",),
    "sambio-q2": ("samsung-biologics-q2-results-2026-07-23",),
    "hyundai-q2": ("hyundai-motor-q2-results-2026-07-23",),
    "orc-q2": ("orc-q2-results-2026-07-23",),
    "ko-q2": ("coca-cola-q2-results-2026-07-28",),
    "visa-q3": ("visa-fq3-results-dividend-2026-07-28",),
    "sk-q2": ("skhynix-q2-results-2026-07-29",),
    "meta-q2": ("meta-q2-results-2026-07-29",),
    "msft-q4": ("microsoft-fq4-results-2026-07-29",),
    "lg-q2": ("lges-q2-detailed-2026-07-30",),
    "samsung-q2": ("samsung-q2-detailed-2026-07-30",),
    "amzn-q2": ("amazon-q2-results-2026-07-30",),
    "aapl-q3": ("apple-fq3-results-2026-07-30",),
    "xom-q2": ("exxon-q2-results-2026-07-31",),
    "sk-54t": (
        "skhynix-cheongju-m17-investment-2026-08-07",
        "skhynix-yongin-fab2-investment-2026-08-07",
    ),
    "goog-notes": ("alphabet-usd-notes-2026-08-10",),
    "orc-div-aug": ("orc-august-distribution-2026-08-12",),
    "nvda-ai-campus": ("nvidia-sb-energy-ports-ai-campus-2026-08-17",),
    "att-notes": ("att-floating-notes-2026-08-17",),
}
SEMANTIC_AGGREGATE_EXCLUSIONS = frozenset({"sk-54t"})
SEVERITY_ORDER = {"MINOR": 0, "MODERATE": 1, "MAJOR": 2, "CRITICAL": 3}

MAX_EVENT_LINKS = 2_048
MAX_EVENT_SOURCES = 32
MAX_EVENT_FACTS = 64
MAX_SOURCE_RECORDS = 10_000
MAX_EVENT_RECORDS = 20_000


class BundleError(ValueError):
    """Raised when an input cannot produce a valid runtime bundle."""


def fail(message: str) -> NoReturn:
    raise BundleError(message)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--candidate",
        dest="candidates",
        action="append",
        required=True,
        type=Path,
        help="research candidate JSON; repeat for every independently researched bundle",
    )
    parser.add_argument("--base-sources", type=Path, default=DEFAULT_BASE_SOURCES)
    parser.add_argument("--base-events", type=Path, default=DEFAULT_BASE_EVENTS)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument(
        "--history-build-report",
        type=Path,
        help="optional daily-history report containing requiredSourceCatalogEntries",
    )
    parser.add_argument("--sources-output", type=Path, default=DEFAULT_SOURCES_OUTPUT)
    parser.add_argument("--events-output", type=Path, default=DEFAULT_EVENTS_OUTPUT)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="validate and hash the prospective resources without writing either output",
    )
    return parser.parse_args(argv)


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as error:
        fail(f"cannot read {path}: {error}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path}: {error}")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{label} must be a JSON object")
    if not all(isinstance(key, str) for key in value):
        fail(f"{label} contains a non-string property name")
    return value


def require_array(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        fail(f"{label} must be a JSON array")
    return value


def require_string(
    value: Any,
    label: str,
    *,
    max_length: int | None = None,
    min_length: int = 1,
    trimmed: bool = True,
) -> str:
    if not isinstance(value, str):
        fail(f"{label} must be a string")
    if any(ord(character) < 0x20 or 0x7F <= ord(character) <= 0x9F for character in value):
        fail(f"{label} must not contain ISO control characters")
    if trimmed and value != value.strip():
        fail(f"{label} must not have leading or trailing whitespace")
    if len(value) < min_length:
        fail(f"{label} must contain at least {min_length} character(s)")
    if max_length is not None and len(value) > max_length:
        fail(f"{label} exceeds {max_length} characters")
    return value


def require_optional_string(
    value: Any,
    label: str,
    *,
    max_length: int,
) -> str | None:
    if value is None:
        return None
    return require_string(value, label, max_length=max_length)


def require_enum(value: Any, allowed: Iterable[str], label: str) -> str:
    result = require_string(value, label)
    allowed_values = frozenset(allowed)
    if result not in allowed_values:
        fail(f"{label} has unsupported value {result!r}")
    return result


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        fail(f"{label} must be a boolean")
    return value


def require_date(value: Any, label: str) -> date:
    text = require_string(value, label)
    try:
        parsed = date.fromisoformat(text)
    except ValueError as error:
        fail(f"{label} must be an ISO-8601 calendar date: {error}")
    if parsed.isoformat() != text:
        fail(f"{label} must use YYYY-MM-DD form")
    return parsed


def require_instant(value: Any, label: str) -> tuple[datetime, str]:
    text = require_string(value, label, max_length=40)
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as error:
        fail(f"{label} must be an ISO-8601 instant with a UTC offset: {error}")
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        fail(f"{label} must include a UTC offset")
    parsed = parsed.astimezone(timezone.utc)
    canonical = parsed.isoformat(timespec="microseconds" if parsed.microsecond else "seconds")
    if parsed.microsecond:
        head, suffix = canonical.split("+", 1)
        head = head.rstrip("0").rstrip(".")
        canonical = f"{head}+{suffix}"
    return parsed, canonical.replace("+00:00", "Z")


def required(record: dict[str, Any], field: str, label: str) -> Any:
    if field not in record:
        fail(f"{label} is missing required field {field!r}")
    return record[field]


def ordered_projection(record: dict[str, Any], fields: Iterable[str]) -> dict[str, Any]:
    return {field: record[field] for field in fields if field in record}


def sanitize_source(value: Any, label: str) -> dict[str, Any]:
    record = require_object(value, label)
    source_id = require_string(required(record, "id", label), f"{label}.id")
    if SOURCE_ID_PATTERN.fullmatch(source_id) is None:
        fail(f"{label}.id does not match the runtime source ID format: {source_id!r}")
    publisher = require_string(
        required(record, "publisher", label), f"{label}.publisher", max_length=256
    )
    title = require_string(
        required(record, "title", label), f"{label}.title", max_length=500
    )
    url = require_string(required(record, "url", label), f"{label}.url", max_length=2_048)
    if not url.startswith("https://"):
        fail(f"{label}.url must be an HTTPS URL")
    accessed_on = require_date(required(record, "accessedOn", label), f"{label}.accessedOn")
    published_value = record.get("publishedOn")
    published_on = (
        require_date(published_value, f"{label}.publishedOn")
        if published_value is not None
        else None
    )
    if published_on is not None and published_on > accessed_on:
        fail(f"{label}.publishedOn cannot be later than accessedOn")
    note = require_optional_string(record.get("note"), f"{label}.note", max_length=500)

    sanitized: dict[str, Any] = {
        "id": source_id,
        "publisher": publisher,
        "title": title,
        "url": url,
    }
    if published_on is not None:
        sanitized["publishedOn"] = published_on.isoformat()
    sanitized["accessedOn"] = accessed_on.isoformat()
    if note is not None:
        sanitized["note"] = note
    return ordered_projection(sanitized, SOURCE_FIELDS)


def sanitize_string_set(
    value: Any,
    label: str,
    *,
    maximum: int,
) -> list[str]:
    items = require_array(value, label)
    if len(items) > maximum:
        fail(f"{label} exceeds the runtime limit of {maximum}")
    result = [
        require_string(item, f"{label}[{index}]", max_length=256)
        for index, item in enumerate(items)
    ]
    if len(result) != len(set(result)):
        fail(f"{label} contains duplicate values")
    return sorted(result)


def sanitize_reported_facts(value: Any, label: str) -> list[dict[str, Any]]:
    items = require_array(value, label)
    if len(items) > MAX_EVENT_FACTS:
        fail(f"{label} exceeds the runtime limit of {MAX_EVENT_FACTS}")
    result: list[dict[str, Any]] = []
    labels: set[str] = set()
    for index, item in enumerate(items):
        item_label = f"{label}[{index}]"
        record = require_object(item, item_label)
        fact_label = require_string(
            required(record, "label", item_label),
            f"{item_label}.label",
            max_length=256,
        )
        if fact_label in labels:
            fail(f"{label} contains duplicate fact label {fact_label!r}")
        labels.add(fact_label)
        actual = require_string(
            required(record, "actual", item_label),
            f"{item_label}.actual",
            max_length=256,
        )
        comparison = require_optional_string(
            record.get("comparison"), f"{item_label}.comparison", max_length=256
        )
        fact: dict[str, Any] = {"label": fact_label, "actual": actual}
        if comparison is not None:
            fact["comparison"] = comparison
        result.append(ordered_projection(fact, REPORTED_FACT_FIELDS))
    return result


def sanitize_market_reactions(
    value: Any,
    label: str,
    *,
    published_at: datetime,
) -> list[dict[str, Any]]:
    items = require_array(value, label)
    if len(items) > len(MARKETS):
        fail(f"{label} exceeds the number of supported markets")
    result: list[dict[str, Any]] = []
    seen_markets: set[str] = set()
    for index, item in enumerate(items):
        item_label = f"{label}[{index}]"
        record = require_object(item, item_label)
        market = require_enum(
            required(record, "market", item_label), MARKETS, f"{item_label}.market"
        )
        if market in seen_markets:
            fail(f"{label} contains duplicate market {market!r}")
        seen_markets.add(market)
        discovery, discovery_text = require_instant(
            required(record, "priceDiscoveryAt", item_label),
            f"{item_label}.priceDiscoveryAt",
        )
        if discovery < published_at:
            fail(f"{item_label}.priceDiscoveryAt cannot precede event publishedAt")
        if discovery > HISTORICAL_THROUGH:
            fail(f"{item_label}.priceDiscoveryAt is later than the scenario history boundary")
        observed = require_date(
            required(record, "observedTradingDate", item_label),
            f"{item_label}.observedTradingDate",
        )
        result.append(
            ordered_projection(
                {
                    "market": market,
                    "priceDiscoveryAt": discovery_text,
                    "observedTradingDate": observed.isoformat(),
                },
                MARKET_REACTION_FIELDS,
            )
        )
    return sorted(result, key=lambda item: MARKET_ORDER[item["market"]])


def sanitize_event(
    value: Any,
    label: str,
    stats: dict[str, Any],
) -> dict[str, Any]:
    record = require_object(value, label)
    event_id = require_string(required(record, "id", label), f"{label}.id")
    if EVENT_ID_PATTERN.fullmatch(event_id) is None:
        fail(f"{label}.id does not match the runtime event ID format: {event_id!r}")
    title = require_string(
        required(record, "title", label), f"{label}.title", max_length=500
    )
    summary = require_string(
        required(record, "summary", label),
        f"{label}.summary",
        min_length=20,
        max_length=2_000,
    )
    event_type = require_enum(
        required(record, "type", label), EVENT_TYPES, f"{label}.type"
    )
    severity = require_enum(
        required(record, "severity", label), EVENT_SEVERITIES, f"{label}.severity"
    )
    occurred_at, occurred_text = require_instant(
        required(record, "occurredAt", label), f"{label}.occurredAt"
    )
    published_at, published_text = require_instant(
        required(record, "publishedAt", label), f"{label}.publishedAt"
    )
    if published_at < occurred_at:
        fail(f"{label}.publishedAt cannot precede occurredAt")
    if not PUBLISHED_AT_START <= published_at <= HISTORICAL_THROUGH:
        fail(
            f"{label}.publishedAt must be within "
            "2026-07-01T00:00:00Z..2026-08-18T20:00:00Z"
        )
    policy = require_enum(
        required(record, "priceEffectPolicy", label),
        PRICE_EFFECT_POLICIES,
        f"{label}.priceEffectPolicy",
    )

    affected_markets = require_array(
        required(record, "affectedMarkets", label), f"{label}.affectedMarkets"
    )
    markets = [
        require_enum(item, MARKETS, f"{label}.affectedMarkets[{index}]")
        for index, item in enumerate(affected_markets)
    ]
    if len(markets) != len(set(markets)):
        fail(f"{label}.affectedMarkets contains duplicate values")
    markets.sort(key=MARKET_ORDER.__getitem__)
    instrument_ids = sanitize_string_set(
        required(record, "affectedInstrumentIds", label),
        f"{label}.affectedInstrumentIds",
        maximum=MAX_EVENT_LINKS,
    )
    if not markets and not instrument_ids:
        fail(f"{label} must affect at least one market or instrument")
    source_ids = sanitize_string_set(
        required(record, "sourceIds", label),
        f"{label}.sourceIds",
        maximum=MAX_EVENT_SOURCES,
    )
    if not source_ids:
        fail(f"{label}.sourceIds must not be empty")
    facts = sanitize_reported_facts(
        required(record, "reportedFacts", label), f"{label}.reportedFacts"
    )
    reaction_value = required(record, "marketReactions", label)
    if policy == "INFORMATION_ONLY":
        # Research bundles retain proposed price-discovery annotations even when
        # an event is deliberately classified as facts-only. They are not part
        # of the runtime meaning of INFORMATION_ONLY and must never leak into
        # the scenario resource.
        discarded_reactions = require_array(reaction_value, f"{label}.marketReactions")
        stats["discardedInformationOnlyReactionCount"] += len(discarded_reactions)
        reactions = []
    else:
        reactions = sanitize_market_reactions(
            reaction_value,
            f"{label}.marketReactions",
            published_at=published_at,
        )
    reaction_markets = {item["market"] for item in reactions}
    if not reaction_markets.issubset(markets):
        fail(f"{label}.marketReactions contains a market outside affectedMarkets")
    if policy == "EMBEDDED_WHERE_ANCHORED" and reaction_markets != set(markets):
        fail(
            f"{label} must provide exactly one marketReaction for every affected market "
            "when priceEffectPolicy is EMBEDDED_WHERE_ANCHORED"
        )

    sanitized = {
        "id": event_id,
        "title": title,
        "summary": summary,
        "type": event_type,
        "severity": severity,
        "occurredAt": occurred_text,
        "publishedAt": published_text,
        "priceEffectPolicy": policy,
        "affectedMarkets": markets,
        "affectedInstrumentIds": instrument_ids,
        "sourceIds": source_ids,
        "reportedFacts": facts,
        "marketReactions": reactions,
    }
    return ordered_projection(sanitized, EVENT_FIELDS)


def read_runtime_resource(
    path: Path,
    kind: str,
    stats: dict[str, Any],
) -> list[dict[str, Any]]:
    root = require_object(read_json(path), str(path))
    allowed_root_fields = {"schemaVersion", "records"}
    unknown = set(root) - allowed_root_fields
    if unknown:
        fail(f"{path} contains unsupported root fields: {sorted(unknown)}")
    if root.get("schemaVersion") != SCHEMA_VERSION:
        fail(f"{path} has unsupported schemaVersion {root.get('schemaVersion')!r}")
    records = require_array(root.get("records"), f"{path}.records")
    if kind == "source":
        return [
            sanitize_source(item, f"{path}.records[{index}]")
            for index, item in enumerate(records)
        ]
    return [
        sanitize_event(item, f"{path}.records[{index}]", stats)
        for index, item in enumerate(records)
    ]


def read_gap_candidate(
    path: Path,
    root: dict[str, Any],
    stats: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    source_values = require_array(root.get("sources"), f"{path}.sources")
    event_values = require_array(root.get("events"), f"{path}.events")
    sources = [
        sanitize_source(item, f"{path}.sources[{index}]")
        for index, item in enumerate(source_values)
    ]
    events = [
        sanitize_event(item, f"{path}.events[{index}]", stats)
        for index, item in enumerate(event_values)
    ]
    return sources, events


def corporate_source_id(url: str) -> str:
    return f"candidate-source:{hashlib.sha256(url.encode('utf-8')).hexdigest()}"


def sanitize_corporate_source(value: Any, label: str) -> dict[str, Any]:
    record = require_object(value, label)
    title = require_string(
        required(record, "title", label), f"{label}.title", max_length=500
    )
    url = require_string(
        required(record, "url", label), f"{label}.url", max_length=2_048
    )
    source_kind = require_string(
        required(record, "kind", label), f"{label}.kind", max_length=256
    )
    if require_bool(required(record, "primary", label), f"{label}.primary") is not True:
        fail(f"{label} must be marked as a primary research source")
    publisher = CORPORATE_SOURCE_PUBLISHERS.get(source_kind)
    if publisher is None and source_kind in CORPORATE_HOST_PUBLISHER_KINDS:
        hostname = urllib.parse.urlsplit(url).hostname
        if hostname is None:
            fail(f"{label}.url has no publisher hostname")
        publisher = hostname.removeprefix("www.")
    if publisher is None:
        fail(f"{label}.kind has no supported publisher mapping: {source_kind!r}")
    return sanitize_source(
        {
            "id": corporate_source_id(url),
            "publisher": publisher,
            "title": title,
            "url": url,
            "accessedOn": required(record, "accessedAt", label),
            "note": f"Primary candidate research source ({source_kind}).",
        },
        label,
    )


def read_corporate_candidate(
    path: Path,
    root: dict[str, Any],
    stats: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    schema_version = root.get("schemaVersion")
    if type(schema_version) is not int or schema_version != SCHEMA_VERSION:
        fail(f"{path} has unsupported corporate-candidate schemaVersion {schema_version!r}")
    values = require_array(root.get("candidates"), f"{path}.candidates")
    sources: list[dict[str, Any]] = []
    events: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    excluded_ids: list[str] = []

    for index, value in enumerate(values):
        label = f"{path}.candidates[{index}]"
        record = require_object(value, label)
        event_id = require_string(required(record, "id", label), f"{label}.id")
        if EVENT_ID_PATTERN.fullmatch(event_id) is None:
            fail(f"{label}.id does not match the runtime event ID format: {event_id!r}")
        if event_id in seen_ids:
            fail(f"{path}.candidates contains duplicate event ID {event_id!r}")
        seen_ids.add(event_id)

        readiness = require_enum(
            required(record, "implementationReadiness", label),
            CORPORATE_READY_STATES,
            f"{label}.implementationReadiness",
        )
        precision = require_enum(
            required(record, "timestampPrecision", label),
            CORPORATE_TIMESTAMP_PRECISIONS,
            f"{label}.timestampPrecision",
        )
        timestamp_is_unverified = precision == "DATE_ONLY_NORMALIZED_NOON_UTC"
        readiness_requires_refinement = readiness == "NEEDS_EXACT_PUBLICATION_TIME"
        if timestamp_is_unverified != readiness_requires_refinement:
            fail(
                f"{label} has inconsistent timestampPrecision and implementationReadiness"
            )
        if readiness_requires_refinement:
            excluded_ids.append(event_id)
            continue

        if require_bool(
            required(record, "existingEventDuplicate", label),
            f"{label}.existingEventDuplicate",
        ):
            fail(f"{label} is marked as an existing event duplicate")
        recommendation = require_string(
            required(record, "priceEffectPolicyRecommendation", label),
            f"{label}.priceEffectPolicyRecommendation",
            max_length=256,
        )
        if recommendation != CORPORATE_INFORMATION_ONLY_RECOMMENDATION:
            fail(f"{label} has unsupported price-effect recommendation {recommendation!r}")

        embedded_sources = require_array(required(record, "sources", label), f"{label}.sources")
        event_sources = [
            sanitize_corporate_source(item, f"{label}.sources[{source_index}]")
            for source_index, item in enumerate(embedded_sources)
        ]
        sources.extend(event_sources)
        events.append(
            sanitize_event(
                {
                    "id": event_id,
                    "title": required(record, "title", label),
                    "summary": required(record, "summary", label),
                    "type": required(record, "type", label),
                    "severity": required(record, "severity", label),
                    "occurredAt": required(record, "occurredAt", label),
                    "publishedAt": required(record, "publishedAt", label),
                    "priceEffectPolicy": "INFORMATION_ONLY",
                    "affectedMarkets": required(record, "affectedMarkets", label),
                    "affectedInstrumentIds": required(
                        record, "affectedInstrumentIds", label
                    ),
                    "sourceIds": [source["id"] for source in event_sources],
                    "reportedFacts": required(record, "reportedFacts", label),
                    "marketReactions": [],
                },
                label,
                stats,
            )
        )

    statistics = root.get("statistics")
    if statistics is not None:
        statistics_record = require_object(statistics, f"{path}.statistics")
        declared_count = statistics_record.get("needsExactTimestampCount")
        if type(declared_count) is not int or declared_count != len(excluded_ids):
            fail(
                f"{path}.statistics.needsExactTimestampCount does not match the "
                "candidate readiness records"
            )
    stats["excludedUnverifiedPublicationTimeIds"].extend(excluded_ids)
    return sources, events


def read_candidate(
    path: Path,
    stats: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    root = require_object(read_json(path), str(path))
    has_gap_shape = "sources" in root or "events" in root
    has_corporate_shape = "candidates" in root
    if has_gap_shape and has_corporate_shape:
        fail(f"{path} ambiguously contains both supported candidate structures")
    if has_gap_shape:
        if "sources" not in root or "events" not in root:
            fail(f"{path} gap-candidate structure requires both sources and events")
        return read_gap_candidate(path, root, stats)
    if has_corporate_shape:
        return read_corporate_candidate(path, root, stats)
    fail(f"{path} is neither a sources/events nor a corporate candidates bundle")


def read_history_sources(path: Path) -> list[dict[str, Any]]:
    root = require_object(read_json(path), str(path))
    values = require_array(
        root.get("requiredSourceCatalogEntries"),
        f"{path}.requiredSourceCatalogEntries",
    )
    return [
        sanitize_source(item, f"{path}.requiredSourceCatalogEntries[{index}]")
        for index, item in enumerate(values)
    ]


def merge_by_id(
    groups: Iterable[tuple[str, Iterable[dict[str, Any]]]],
    *,
    kind: str,
) -> dict[str, dict[str, Any]]:
    merged: dict[str, dict[str, Any]] = {}
    origins: dict[str, str] = {}
    for origin, records in groups:
        for record in records:
            record_id = record["id"]
            previous = merged.get(record_id)
            if previous is None:
                merged[record_id] = record
                origins[record_id] = origin
            elif previous != record:
                fail(
                    f"conflicting {kind} ID {record_id!r} in {origins[record_id]} and {origin}"
                )
    return merged


def normalized_title(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return "".join(character for character in normalized if character.isalnum())


def merge_reported_facts(
    canonical: list[dict[str, Any]],
    duplicate: list[dict[str, Any]],
    label: str,
) -> list[dict[str, Any]]:
    result = list(canonical)
    by_label = {fact["label"]: fact for fact in result}
    for fact in duplicate:
        previous = by_label.get(fact["label"])
        if previous is None:
            result.append(fact)
            by_label[fact["label"]] = fact
        elif previous != fact:
            fail(f"{label} has conflicting reported fact {fact['label']!r}")
    return result


def semantic_duplicate_evidence(
    duplicate: dict[str, Any],
    canonical: dict[str, Any],
) -> dict[str, Any]:
    duplicate_time, _ = require_instant(duplicate["publishedAt"], "duplicate.publishedAt")
    canonical_time, _ = require_instant(canonical["publishedAt"], "canonical.publishedAt")
    delta_seconds = int(abs((duplicate_time - canonical_time).total_seconds()))
    shared_instruments = sorted(
        set(duplicate["affectedInstrumentIds"]) & set(canonical["affectedInstrumentIds"])
    )
    shared_markets = sorted(
        set(duplicate["affectedMarkets"]) & set(canonical["affectedMarkets"]),
        key=MARKET_ORDER.__getitem__,
    )
    title_similarity = difflib.SequenceMatcher(
        None,
        normalized_title(duplicate["title"]),
        normalized_title(canonical["title"]),
    ).ratio()
    if not shared_instruments or not shared_markets:
        fail(
            f"semantic duplicate {duplicate['id']!r}/{canonical['id']!r} has no "
            "shared instrument and market evidence"
        )
    if delta_seconds != 0 and not (delta_seconds <= 12 * 60 * 60 and title_similarity >= 0.25):
        fail(
            f"semantic duplicate {duplicate['id']!r}/{canonical['id']!r} no longer "
            "matches the reviewed time/title evidence"
        )
    return {
        "retainedId": canonical["id"],
        "publishedAtDeltaSeconds": delta_seconds,
        "normalizedTitleSimilarity": round(title_similarity, 6),
        "sharedAffectedInstrumentIds": shared_instruments,
        "sharedAffectedMarkets": shared_markets,
    }


def merge_semantic_duplicate(
    canonical: dict[str, Any],
    duplicate: dict[str, Any],
    stats: dict[str, Any],
) -> dict[str, Any]:
    if canonical["priceEffectPolicy"] != "INFORMATION_ONLY":
        fail(f"semantic duplicate canonical event {canonical['id']!r} must be INFORMATION_ONLY")
    severity = max(
        (canonical["severity"], duplicate["severity"]),
        key=SEVERITY_ORDER.__getitem__,
    )
    occurred_at = min(canonical["occurredAt"], duplicate["occurredAt"])
    published_at = min(canonical["publishedAt"], duplicate["publishedAt"])
    merged = {
        **canonical,
        "severity": severity,
        "occurredAt": occurred_at,
        "publishedAt": published_at,
        "affectedMarkets": sorted(
            set(canonical["affectedMarkets"]) | set(duplicate["affectedMarkets"]),
            key=MARKET_ORDER.__getitem__,
        ),
        "affectedInstrumentIds": sorted(
            set(canonical["affectedInstrumentIds"])
            | set(duplicate["affectedInstrumentIds"])
        ),
        "sourceIds": sorted(set(canonical["sourceIds"]) | set(duplicate["sourceIds"])),
        "reportedFacts": merge_reported_facts(
            canonical["reportedFacts"],
            duplicate["reportedFacts"],
            f"semantic duplicate {duplicate['id']!r}/{canonical['id']!r}",
        ),
        "marketReactions": [],
    }
    return sanitize_event(
        merged,
        f"semantic duplicate merge {duplicate['id']!r}->{canonical['id']!r}",
        stats,
    )


def apply_semantic_duplicate_policy(
    events: dict[str, dict[str, Any]],
    stats: dict[str, Any],
) -> None:
    resolutions: list[dict[str, Any]] = []
    for duplicate_id, canonical_ids in SEMANTIC_DUPLICATE_TARGETS.items():
        duplicate = events.get(duplicate_id)
        if duplicate is None:
            continue
        canonical_events = [events[item] for item in canonical_ids if item in events]
        if not canonical_events:
            continue
        if len(canonical_events) != len(canonical_ids):
            fail(
                f"semantic duplicate {duplicate_id!r} has only a partial canonical event set"
            )
        evidence = [
            semantic_duplicate_evidence(duplicate, canonical)
            for canonical in canonical_events
        ]
        if duplicate_id in SEMANTIC_AGGREGATE_EXCLUSIONS:
            policy = "EXCLUDED_AGGREGATE_SUPERSEDED_BY_COMPONENT_EVENTS"
        else:
            if len(canonical_events) != 1:
                fail(f"semantic duplicate {duplicate_id!r} requires one canonical merge target")
            canonical = canonical_events[0]
            events[canonical["id"]] = merge_semantic_duplicate(canonical, duplicate, stats)
            policy = "MERGED_INTO_REVIEWED_PRIMARY_SOURCE_CANONICAL"
        del events[duplicate_id]
        resolutions.append(
            {
                "removedId": duplicate_id,
                "retainedIds": list(canonical_ids),
                "policy": policy,
                "evidence": evidence,
            }
        )
    stats["semanticDuplicateResolutions"] = resolutions


def merge_history_sources(
    merged: dict[str, dict[str, Any]],
    origins: dict[str, str],
    history_sources: Iterable[dict[str, Any]],
    history_origin: str,
) -> None:
    for record in history_sources:
        source_id = record["id"]
        previous = merged.get(source_id)
        if previous is None or previous == record:
            merged[source_id] = record
            origins[source_id] = history_origin
        elif source_id in HISTORY_SOURCE_OVERRIDE_IDS:
            merged[source_id] = record
            origins[source_id] = history_origin
        else:
            fail(
                f"conflicting source ID {source_id!r} in {origins[source_id]} and "
                f"{history_origin}; only the Yahoo/NAVER v2 metadata may replace an existing source"
            )


def load_catalog_ids(path: Path) -> set[str]:
    root = require_object(read_json(path), str(path))
    instruments = require_array(root.get("instruments"), f"{path}.instruments")
    result: set[str] = set()
    for index, item in enumerate(instruments):
        label = f"{path}.instruments[{index}]"
        record = require_object(item, label)
        market = require_enum(required(record, "market", label), MARKETS, f"{label}.market")
        symbol = require_string(required(record, "symbol", label), f"{label}.symbol")
        instrument_id = f"{market}:{symbol}"
        if instrument_id in result:
            fail(f"{path} contains duplicate instrument identity {instrument_id!r}")
        result.add(instrument_id)
    if not result:
        fail(f"{path} contains no instruments")
    return result


def validate_references(
    events: Iterable[dict[str, Any]],
    source_ids: set[str],
    instrument_ids: set[str],
) -> None:
    for event in events:
        missing_sources = sorted(set(event["sourceIds"]) - source_ids)
        if missing_sources:
            fail(f"event {event['id']!r} references unknown sources: {missing_sources}")
        missing_instruments = sorted(set(event["affectedInstrumentIds"]) - instrument_ids)
        if missing_instruments:
            fail(f"event {event['id']!r} references unknown instruments: {missing_instruments}")


def encode_resource(records: Iterable[dict[str, Any]]) -> bytes:
    document = {"schemaVersion": SCHEMA_VERSION, "records": list(records)}
    return (
        json.dumps(document, ensure_ascii=False, indent=2, separators=(",", ": ")) + "\n"
    ).encode("utf-8")


def stage_atomic(path: Path, content: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
        )
        temporary = Path(temporary_name)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        return temporary
    except OSError as error:
        if temporary is not None:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                pass
        fail(f"cannot stage {path}: {error}")


def write_outputs(outputs: Iterable[tuple[Path, bytes]]) -> None:
    staged: list[tuple[Path, Path]] = []
    try:
        for path, content in outputs:
            staged.append((path, stage_atomic(path, content)))
        for path, temporary in staged:
            os.replace(temporary, path)
    except OSError as error:
        fail(f"cannot replace output resource: {error}")
    finally:
        for _, temporary in staged:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                pass


def sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    stats: dict[str, Any] = {
        "discardedInformationOnlyReactionCount": 0,
        "excludedUnverifiedPublicationTimeIds": [],
    }
    if args.sources_output.resolve() == args.events_output.resolve():
        fail("sources-output and events-output must be different files")

    base_sources = read_runtime_resource(args.base_sources, "source", stats)
    base_events = read_runtime_resource(args.base_events, "event", stats)
    source_groups: list[tuple[str, Iterable[dict[str, Any]]]] = [
        (str(args.base_sources), base_sources)
    ]
    event_groups: list[tuple[str, Iterable[dict[str, Any]]]] = [
        (str(args.base_events), base_events)
    ]
    for candidate_path in args.candidates:
        candidate_sources, candidate_events = read_candidate(candidate_path, stats)
        source_groups.append((str(candidate_path), candidate_sources))
        event_groups.append((str(candidate_path), candidate_events))

    excluded_ids = stats["excludedUnverifiedPublicationTimeIds"]
    if len(excluded_ids) != len(set(excluded_ids)):
        fail("candidate bundles contain duplicate excluded event IDs")

    sources = merge_by_id(source_groups, kind="source")
    source_origins = {
        record["id"]: origin
        for origin, records in source_groups
        for record in records
        if record["id"] in sources
    }
    if args.history_build_report is not None:
        history_sources = merge_by_id(
            [
                (
                    str(args.history_build_report),
                    read_history_sources(args.history_build_report),
                )
            ],
            kind="source",
        )
        merge_history_sources(
            sources,
            source_origins,
            history_sources.values(),
            str(args.history_build_report),
        )
    events = merge_by_id(event_groups, kind="event")
    apply_semantic_duplicate_policy(events, stats)
    excluded_collision = sorted(set(excluded_ids) & set(events))
    if excluded_collision:
        fail(
            "unverified-publication-time event IDs collide with included events: "
            f"{excluded_collision}"
        )

    catalog_ids = load_catalog_ids(args.catalog)
    validate_references(events.values(), set(sources), catalog_ids)

    source_records = sorted(sources.values(), key=lambda item: item["id"])
    event_records = sorted(
        events.values(),
        key=lambda item: (item["publishedAt"], item["occurredAt"], item["id"]),
    )
    if len(source_records) > MAX_SOURCE_RECORDS:
        fail(f"source record count exceeds the runtime limit of {MAX_SOURCE_RECORDS}")
    if len(event_records) > MAX_EVENT_RECORDS:
        fail(f"event record count exceeds the runtime limit of {MAX_EVENT_RECORDS}")
    source_content = encode_resource(source_records)
    event_content = encode_resource(event_records)
    if not args.dry_run:
        write_outputs(
            (
                (args.sources_output, source_content),
                (args.events_output, event_content),
            )
        )

    report = {
        "schemaVersion": SCHEMA_VERSION,
        "dryRun": args.dry_run,
        "discardedInformationOnlyReactionCount": stats[
            "discardedInformationOnlyReactionCount"
        ],
        "excludedUnverifiedPublicationTimeCount": len(excluded_ids),
        "excludedUnverifiedPublicationTimeIds": sorted(excluded_ids),
        "semanticDuplicateCount": len(stats["semanticDuplicateResolutions"]),
        "semanticDuplicateResolutions": stats["semanticDuplicateResolutions"],
        "outputs": {
            "sources": {
                "path": str(args.sources_output.resolve()),
                "recordCount": len(source_records),
                "contentSha256": sha256(source_content),
            },
            "events": {
                "path": str(args.events_output.resolve()),
                "recordCount": len(event_records),
                "contentSha256": sha256(event_content),
            },
        },
    }
    json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BundleError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(2) from error
