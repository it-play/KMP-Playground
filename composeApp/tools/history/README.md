# Historical Scenario Tools

These scripts are development-time generators for the historical scenario
resources under `composeApp/src/commonMain/composeResources`. They are not
called by the application at runtime and are not bundled as Compose resources.

- `fetch_historical_scenario_data.py` downloads, normalizes, audits, and shards
  the five-year OHLCV history, then builds the historical corporate-action
  resource.
- `build_historical_event_bundle.py` validates researched event candidates,
  resolves reviewed duplicates, checks source and instrument references, and
  writes deterministic source and event resources.

Both tools use only the Python standard library. Run either script with
`--help` to inspect its inputs before regenerating checked-in resources.
