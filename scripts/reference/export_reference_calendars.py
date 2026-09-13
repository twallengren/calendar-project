#!/usr/bin/env python3
"""Exports third-party calendar data as reference CSVs for cross-validation.

Run manually (never in CI) and commit the outputs under
tools/src/test/resources/reference/<CALENDAR>/<source>.csv:

    python3 -m venv .venv && .venv/bin/pip install -r scripts/reference/requirements.txt
    .venv/bin/python scripts/reference/export_reference_calendars.py

Each file lists closures and early closes as `date,type,close_time` (close_time is the
local wall-clock close, empty for full closures). Weekends are not listed. A leading
`# generated-by:` comment records the library versions used; ReferenceCrossValidationTest
skips comment lines.
"""
from __future__ import annotations

import datetime as dt
import os
import platform
import sys

import exchange_calendars as xc
import pandas as pd

try:
    import QuantLib as ql
except ImportError:  # pragma: no cover
    ql = None

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "tools", "src", "test", "resources", "reference")

EXPORTS = [
    # (calendar id in this repo, exchange_calendars code, start, end)
    # exchange_calendars' regular XNYS holiday rules are complete only from 1971; earlier years
    # would only report the ad-hoc closures it happens to know about.
    ("US-NYSE", "XNYS", "1971-01-01", "2030-12-31"),
    ("SA-TADAWUL", "XSAU", "2020-01-01", "2030-12-31"),
    ("GB-LSE", "XLON", "2000-01-01", "2030-12-31"),
    ("DE-XETRA", "XETR", "2003-01-01", "2030-12-31"),
]


def header(lib: str, version: str) -> str:
    return (
        f"# generated-by: {lib} {version}, python {platform.python_version()}, "
        f"{dt.date.today().isoformat()}\n"
        "# columns: date,type,close_time  (weekends omitted; close_time is local wall-clock)\n"
    )


def regular_close_for(cal, day: dt.date):
    """The regular close time in effect on `day` (close_times is ((from_date, time), ...))."""
    regular = None
    for from_date, t in cal.close_times:
        if from_date is None or pd.Timestamp(from_date).date() <= day:
            regular = t
    return regular


def export_exchange_calendars(cal_id: str, code: str, start: str, end: str) -> None:
    probe = xc.get_calendar(code)  # default bounds
    bound_min = probe.bound_min()
    bound_max = probe.bound_max()
    lo = dt.date.fromisoformat(start)
    hi = dt.date.fromisoformat(end)
    if bound_min is not None:
        lo = max(lo, bound_min.date())
    if bound_max is not None:
        hi = min(hi, bound_max.date())
    cal = xc.get_calendar(code, start=lo.isoformat(), end=hi.isoformat())
    tz = cal.tz
    first = cal.first_session.date()
    last = cal.last_session.date()
    lo = max(lo, first)
    hi = min(hi, last)
    rows: dict[dt.date, tuple[str, str]] = {}
    weekend_days = {i for i, flag in enumerate(cal.weekmask) if flag == "0"}
    session_dates = {ts.date() for ts in cal.sessions}
    closes = cal.closes  # Series indexed by session, values are UTC close timestamps

    for ts in pd.date_range(lo, hi, freq="D"):
        day = ts.date()
        if ts.weekday() in weekend_days:
            continue
        if day not in session_dates:
            rows[day] = ("CLOSED", "")
            continue
        close_utc = closes[pd.Timestamp(day)]
        if close_utc.tzinfo is None:
            close_utc = close_utc.tz_localize("UTC")
        close_local = close_utc.tz_convert(tz).time()
        regular = regular_close_for(cal, day)
        if regular is not None and close_local < regular:
            rows[day] = ("EARLY_CLOSE", close_local.strftime("%H:%M"))

    path = os.path.join(OUT, cal_id, f"exchange_calendars-{code}.csv")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(header("exchange_calendars", xc.__version__))
        f.write(f"# range: {lo.isoformat()} to {hi.isoformat()} (calendar bounds {first} to {last})\n")
        f.write("# types: CLOSED,EARLY_CLOSE\n")
        f.write("date,type,close_time\n")
        for day in sorted(rows):
            typ, close_time = rows[day]
            f.write(f"{day.isoformat()},{typ},{close_time}\n")
    print(f"wrote {path} ({len(rows)} rows, {lo} to {hi})")


def export_quantlib_nyse(start: str, end: str) -> None:
    if ql is None:
        print("QuantLib not installed; skipping")
        return
    cal = ql.UnitedStates(ql.UnitedStates.NYSE)
    s = dt.date.fromisoformat(start)
    e = dt.date.fromisoformat(end)
    rows = []
    day = s
    while day <= e:
        if day.weekday() < 5:
            qd = ql.Date(day.day, day.month, day.year)
            if not cal.isBusinessDay(qd):
                rows.append(day)
        day += dt.timedelta(days=1)
    path = os.path.join(OUT, "US-NYSE", "quantlib-nyse.csv")
    with open(path, "w") as f:
        f.write(header("QuantLib", ql.__version__))
        f.write("# types: CLOSED  (QuantLib models full closures only)\n")
        f.write("date,type,close_time\n")
        for day in rows:
            f.write(f"{day.isoformat()},CLOSED,\n")
    print(f"wrote {path} ({len(rows)} rows)")


def export_quantlib_uk_exchange(start: str, end: str) -> None:
    if ql is None:
        print("QuantLib not installed; skipping")
        return
    cal = ql.UnitedKingdom(ql.UnitedKingdom.Exchange)
def export_quantlib_germany(start: str, end: str) -> None:
    if ql is None:
        print("QuantLib not installed; skipping")
        return
    cal = ql.Germany(ql.Germany.Xetra)
    s = dt.date.fromisoformat(start)
    e = dt.date.fromisoformat(end)
    rows = []
    day = s
    while day <= e:
        if day.weekday() < 5:
            qd = ql.Date(day.day, day.month, day.year)
            if not cal.isBusinessDay(qd):
                rows.append(day)
        day += dt.timedelta(days=1)
    path = os.path.join(OUT, "GB-LSE", "quantlib-uk-exchange.csv")
    path = os.path.join(OUT, "DE-XETRA", "quantlib-germany-xetra.csv")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(header("QuantLib", ql.__version__))
        f.write("# types: CLOSED  (QuantLib models full closures only)\n")
        f.write("date,type,close_time\n")
        for day in rows:
            f.write(f"{day.isoformat()},CLOSED,\n")
    print(f"wrote {path} ({len(rows)} rows)")


def main() -> int:
    for cal_id, code, start, end in EXPORTS:
        export_exchange_calendars(cal_id, code, start, end)
    # QuantLib's NYSE calendar is documented from 1980 onward
    export_quantlib_nyse("1980-01-01", "2030-12-31")
    export_quantlib_uk_exchange("2000-01-01", "2030-12-31")
    export_quantlib_germany("2003-01-01", "2030-12-31")
    return 0


if __name__ == "__main__":
    sys.exit(main())
