# Coverage assessment snapshot

This table describes the prepared local data v12.0.0 candidate. It is not publication evidence.
Counts include every covered civil date and use the same effective confidence as the query API.
UNKNOWN dates preserve their scheduled state in assessments but fail business-day decisions.
PROJECTED dates remain usable with their stated uncertainty. Sources and scope intervals are linked
in each calendar’s compiled metadata; the canonical evidence registers are under `sources/`.

| Calendar | Covered interval | CONFIRMED dates | PROJECTED dates | UNKNOWN dates |
|---|---|---:|---:|---:|
| [BE-EURONEXT-BRUSSELS](../../blessed/BE-EURONEXT-BRUSSELS/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7664 | 6 |
| [CA-TSX](../../blessed/CA-TSX/metadata.json) | 2000-01-01 – 2030-12-31 | 0 | 8034 | 3289 |
| [DE-XETRA](../../blessed/DE-XETRA/metadata.json) | 2003-01-01 – 2030-12-31 | 0 | 2557 | 7670 |
| [EU-EURONEXT](../../blessed/EU-EURONEXT/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7670 | 0 |
| [EU-TARGET](../../blessed/EU-TARGET/metadata.json) | 2026-01-01 – 2027-12-31 | 0 | 730 | 0 |
| [FR-EURONEXT-PARIS](../../blessed/FR-EURONEXT-PARIS/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7664 | 6 |
| [GB-CHAPS](../../blessed/GB-CHAPS/metadata.json) | 2026-01-01 – 2027-12-31 | 0 | 730 | 0 |
| [GB-LSE](../../blessed/GB-LSE/metadata.json) | 2000-01-01 – 2030-12-31 | 0 | 11311 | 12 |
| [HK-HKEX](../../blessed/HK-HKEX/metadata.json) | 2018-01-01 – 2027-12-31 | 0 | 1195 | 2457 |
| [IL-TASE](../../blessed/IL-TASE/metadata.json) | 2025-01-01 – 2027-12-31 | 0 | 0 | 1095 |
| [JP-JPX](../../blessed/JP-JPX/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7670 | 0 |
| [NL-EURONEXT-AMSTERDAM](../../blessed/NL-EURONEXT-AMSTERDAM/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7664 | 6 |
| [PT-EURONEXT-LISBON](../../blessed/PT-EURONEXT-LISBON/metadata.json) | 2010-01-01 – 2030-12-31 | 0 | 7664 | 6 |
| [SA-TADAWUL](../../blessed/SA-TADAWUL/metadata.json) | 2020-01-01 – 2030-12-31 | 0 | 3652 | 366 |
| [US-CORP-IN-VISIBILITY](../../blessed/US-CORP-IN-VISIBILITY/metadata.json) | 1900-01-01 – 2030-12-31 | 0 | 47847 | 0 |
| [US-FEDWIRE](../../blessed/US-FEDWIRE/metadata.json) | 2026-01-01 – 2027-12-31 | 0 | 730 | 0 |
| [US-MARKET-BASE](../../blessed/US-MARKET-BASE/metadata.json) | 1900-01-01 – 2030-12-31 | 0 | 47847 | 0 |
| [US-NYSE](../../blessed/US-NYSE/metadata.json) | 1900-01-01 – 2030-12-31 | 39438 | 8401 | 8 |

TASE actual-day coverage is explicitly incomplete throughout its initial interval. Payment calendars
have verified scheduled holidays and projected ordinary early-close/exception baselines; their
operating-date answers are therefore PROJECTED throughout 2026–2027. Calendar coverage is not a
claim about intraday sessions, cutoffs, religious sunset instants or settlement eligibility.
