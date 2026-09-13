# Sources

Authoritative material that the calendar data is transcribed from. Every event source in
`modules/` and `calendars/` carries a `source:` citation whose `id` refers to a row in the
`README.md` of the market directory below (`validate --strict` fails on an event source
without one).

| Directory | Market |
|-----------|--------|
| `US-NYSE/` | New York Stock Exchange (also backs `US-MARKET-BASE` and `US-CORP-IN-VISIBILITY`) |
| `SA-TADAWUL/` | Saudi Exchange (Tadawul) |

Citation shape in YAML:

```yaml
source:
  - id: nyse-history-2008          # row id in sources/<MARKET>/README.md
    ref: "special closings list"   # optional pointer inside the document
```

or, for a one-off external reference: `{title, publisher, url, retrieved, note}`.

Regenerate the third-party cross-validation data with `scripts/reference/export_reference_calendars.py`
(see `tools/src/test/resources/reference/README.md`).
