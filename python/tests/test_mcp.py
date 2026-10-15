"""
Exercises the ``bdc-calendars-mcp`` stdio server through a real MCP client.

The server is launched as a subprocess (``python -m bdc_calendars.mcp.server``)
using the SDK's own stdio transport, so this is an end-to-end check of the
same surface an AI agent talks to, not just the tool functions in isolation.
Skipped entirely when the optional ``mcp`` extra is not installed.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest
import bdc_calendars as bdc

mcp_client = pytest.importorskip("mcp")
from mcp import ClientSession  # noqa: E402
from mcp.client.stdio import StdioServerParameters, stdio_client  # noqa: E402

SERVER_PARAMS = StdioServerParameters(
    command=sys.executable,
    args=["-m", "bdc_calendars.mcp.server"],
    env={"PYTHONPATH": str(Path(__file__).resolve().parents[1])},
)

EXPECTED_TOOLS = {
    "list_calendars",
    "calendar_info",
    "assess_day",
    "is_business_day",
    "is_trading_day",
    "special_sessions",
    "next_business_day",
    "previous_business_day",
    "add_business_days",
    "adjust_date",
    "business_day_offset",
    "advance_months",
    "last_business_day_of_month",
    "member_closes",
    "business_days_between",
    "holidays_in_range",
    "is_early_close",
    "joint_settlement_date",
    "status",
    "add_tenor",
    "year_fraction",
    "generate_schedule",
}


async def _call(session: ClientSession, name: str, arguments: dict) -> dict:
    result = await session.call_tool(name, arguments)
    assert not result.isError, result.content
    (content,) = result.content
    return json.loads(content.text)


@pytest.mark.anyio
async def test_lists_the_expected_tools():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            listed = await session.list_tools()
            names = {tool.name for tool in listed.tools}
            assert names == EXPECTED_TOOLS
            # Descriptions come from the tool docstrings and must not be empty.
            assert all(tool.description for tool in listed.tools)


@pytest.mark.anyio
async def test_is_business_day_us_nyse():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(
                session, "is_business_day", {"calendar": "US-NYSE", "date": "2021-12-31"}
            )
            assert payload["calendar_id"] == "US-NYSE"
            assert payload["is_business_day"] is True
            assert "data_version" in payload
            assert "reason" not in payload


@pytest.mark.anyio
async def test_financial_operation_shape_and_member_timezone():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            adjusted = await _call(
                session,
                "adjust_date",
                {"calendar": "US-NYSE", "date": "2025-12-25", "convention": "FOLLOWING"},
            )
            assert adjusted["operation"] == "ADJUST"
            assert adjusted["original_date"] == "2025-12-25"
            assert adjusted["result_date"] == "2025-12-26"
            assert adjusted["examined_dates"] == ["2025-12-25", "2025-12-26"]
            assert adjusted["effective_confidence"] in {"CONFIRMED", "PROJECTED"}

            closes = await _call(
                session,
                "member_closes",
                {"calendars": ["US-NYSE"], "date": "2026-11-27"},
            )
            assert closes["member_closes"] == [
                {
                    "calendar_id": "US-NYSE",
                    "timezone": "America/New_York",
                    "local_time": "13:00",
                }
            ]


@pytest.mark.anyio
async def test_assess_day_exposes_completeness_contract():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(
                session, "assess_day", {"calendar": "US-NYSE", "date": "2021-12-31"}
            )
            assert payload["state"] == "OPEN"
            assert payload["scheduled_state"] == "OPEN"
            expected = "PROJECTED" if bdc.get_calendar("US-NYSE").coverage_intervals else "CONFIRMED"
            assert payload["effective_confidence"] == expected
            # NYSE 2008-2026 is reconciled against index series (VERIFIED closures); the
            # recurring early closes past the 2007 history remain a projection.
            assert payload["completeness"]["SCHEDULED_CLOSURES"] == "VERIFIED"
            assert payload["completeness"]["EARLY_CLOSES"] == "PROJECTED"
            assert payload["business_day_confidence"] == "CONFIRMED"


@pytest.mark.anyio
@pytest.mark.skipif("IL-TASE" not in bdc.list_calendars(), reason="native TASE data not bundled yet")
async def test_native_tase_assessment_and_unknown_error_through_stdio():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(session, "assess_day", {"calendar": "IL-TASE", "date": "2025-09-23"})
            assert payload["state"] == "UNKNOWN"
            assert payload["scheduled_state"] == "CLOSED"
            event = next(event for event in payload["events"] if event["nominal_native_date"])
            assert event["nominal_native_date"] == dict(chronology_id="HEBREW", year=5786, month_code="TISHRI", day=1)
            assert event["evidence_ids"]
            # Closures and unscheduled exceptions are verified for 2025, so the business-day
            # question answers; the 2025 close times are unsourced, so the session question fails.
            assert payload["business_day_state"] == "CLOSED"
            closed = await _call(session, "is_business_day", {"calendar": "IL-TASE", "date": "2025-09-23"})
            assert closed["is_business_day"] is False
            error = await _call(session, "is_early_close", {"calendar": "IL-TASE", "date": "2025-09-23"})
            assert error["error"] == "UnresolvedDateError"


@pytest.mark.anyio
@pytest.mark.skipif(not bdc.get_calendar("HK-HKEX").coverage_intervals,
                    reason="native HKEX data not bundled yet")
async def test_hkex_native_and_authoritative_override_through_stdio():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            native = await _call(session, "assess_day", {"calendar": "HK-HKEX", "date": "2025-01-29"})
            assert native["state"] == "CLOSED"
            event = next(event for event in native["events"] if event["nominal_native_date"])
            assert event["nominal_native_date"] == dict(chronology_id="CHINESE_HK", year=2025, month_code="M01", day=1)
            assert event["chronology_provider"] == "ICU4J 78.3"
            override = await _call(session, "assess_day", {"calendar": "HK-HKEX", "date": "2027-02-09"})
            assert override["state"] == "CLOSED"
            assert override["events"]
            for event in override["events"]:
                assert event["nominal_native_date"] is None
                assert {"hko-gregorian-lunar-conversion", "govhk-general-holidays", "hkex-calendar"}.issubset(event["evidence_ids"])
            following = await _call(session, "is_business_day", {"calendar": "HK-HKEX", "date": "2027-02-10"})
            assert following["is_business_day"] is True


@pytest.mark.anyio
async def test_is_early_close_thanksgiving_friday_2027():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(
                session, "is_early_close", {"calendar": "US-NYSE", "date": "2027-11-26"}
            )
            assert payload["is_early_close"] is True
            assert payload["close_time"] == "13:00:00"
            # 2027-11-26 is after US-NYSE's verified_through (2026-12-31), so per
            # the status contract in spec/SPEC.md this is PROJECTED, not CONFIRMED,
            # whatever the row itself says.
            assert payload["status"] == "PROJECTED"


@pytest.mark.anyio
async def test_trading_day_and_special_sessions_through_stdio():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            christmas = await _call(
                session, "is_trading_day", {"calendar": "XNYS", "date": "2025-12-25"}
            )
            assert christmas["calendar_id"] == "US-NYSE"
            assert christmas["is_trading_day"] is False
            assert christmas["is_business_day"] is False
            joint = await _call(
                session, "is_trading_day", {"calendar": "US-NYSE,GB-LSE", "date": "2025-12-24"}
            )
            assert joint["calendar_id"] == "US-NYSE+GB-LSE"
            assert joint["is_trading_day"] is True
            sessions = await _call(
                session, "special_sessions", {"calendar": "US-NYSE", "date": "2025-12-25"}
            )
            assert sessions["special_sessions"] == []
            assert sessions["status"] in {"CONFIRMED", "PROJECTED"}
            outside = await _call(
                session, "special_sessions", {"calendar": "US-NYSE", "date": "1700-01-01"}
            )
            assert outside["error"] == "OutsideCoverageError"


@pytest.mark.anyio
async def test_special_sessions_tool_serialises_member_sessions(monkeypatch):
    """No bundled calendar has a special session yet, so drive the tool in-process."""
    from bdc_calendars._loader import CalendarData, _parse_events
    from bdc_calendars.mcp.server import create_server

    def synthetic(calendar_id, timezone, rows):
        header = "date,type,description,key,source_module,observed_from,close_time,status,open_time"
        metadata = {
            "range_start": "2024-01-01",
            "range_end": "2024-12-31",
            "timezone": timezone,
            "weekend_policy": {"days": ["SATURDAY", "SUNDAY"]},
        }
        events = _parse_events("\n".join([header] + rows) + "\n")
        return bdc.SingleCalendar(CalendarData(calendar_id, metadata, events))

    calendars = {
        "IN-TEST": synthetic("IN-TEST", "Asia/Kolkata", [
            "2024-11-01,CLOSED,Diwali,diwali,m,,,CONFIRMED,",
            "2024-11-01,SPECIAL_SESSION,Muhurat trading,muhurat,m,,19:00,PROJECTED,18:00",
        ]),
        "OPEN-TEST": synthetic("OPEN-TEST", "Europe/London", []),
    }
    import bdc_calendars.calendar as calendar_module

    monkeypatch.setattr(bdc, "get_calendar", calendars.__getitem__)
    monkeypatch.setattr(calendar_module, "get_calendar", calendars.__getitem__)
    app = create_server()

    async def call(name, arguments):
        result = await app.call_tool(name, arguments)
        content = result[0] if isinstance(result, tuple) else result
        return json.loads(content[0].text)

    sessions = await call("special_sessions", {"calendar": "OPEN-TEST,IN-TEST", "date": "2024-11-01"})
    assert sessions["calendar_id"] == "OPEN-TEST+IN-TEST"
    assert sessions["special_sessions"] == [
        {
            "calendar_id": "IN-TEST",
            "timezone": "Asia/Kolkata",
            "open_time": "18:00",
            "close_time": "19:00",
            "key": "muhurat",
            "description": "Muhurat trading",
            "status": "PROJECTED",
        }
    ]
    trading = await call("is_trading_day", {"calendar": "IN-TEST", "date": "2024-11-01"})
    assert trading["is_trading_day"] is True
    assert trading["is_business_day"] is False
    business = await call("is_business_day", {"calendar": "IN-TEST", "date": "2024-11-01"})
    session_event = [e for e in business["events"] if e["type"] == "SPECIAL_SESSION"][0]
    assert session_event["open_time"] == "18:00"
    assert "open_time" not in [e for e in business["events"] if e["type"] == "CLOSED"][0]


@pytest.mark.anyio
async def test_joint_settlement_date_us_nyse_sa_tadawul():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(
                session,
                "joint_settlement_date",
                {
                    "calendars": ["US-NYSE", "SA-TADAWUL"],
                    "trade_date": "2026-02-25",
                    "t_plus": 2,
                },
            )
            assert payload["settlement_date"] == "2026-03-02"
            assert payload["calendar_id"] == "US-NYSE+SA-TADAWUL"
            assert payload["effective_confidence"] in {"CONFIRMED", "PROJECTED"}
            assert payload["examined_dates"][0] == "2026-02-26"
            closures = {d["date"]: d["closed_members"] for d in payload["intervening_days"]}
            assert closures["2026-02-27"] == ["SA-TADAWUL"]
            assert closures["2026-02-28"] == ["US-NYSE", "SA-TADAWUL"]
            assert closures["2026-03-01"] == ["US-NYSE"]
            assert closures["2026-03-02"] == []


@pytest.mark.anyio
async def test_unknown_calendar_is_a_structured_error_not_a_raise():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            payload = await _call(
                session, "is_business_day", {"calendar": "NOT-A-CALENDAR", "date": "2021-12-31"}
            )
            assert payload["error"] == "CalendarNotFoundError"
            assert "NOT-A-CALENDAR" in payload["message"]


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.mark.anyio
@pytest.mark.skipif("EU-TARGET" not in bdc.list_calendars(), reason="payment data not bundled yet")
async def test_payment_operations_and_joint_conventions_through_stdio():
    async with stdio_client(SERVER_PARAMS) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            for calendar, source, expected in (
                ("EU-TARGET", "2026-04-02", "2026-04-07"),
                ("US-FEDWIRE", "2026-07-02", "2026-07-03"),
                ("GB-CHAPS", "2026-12-24", "2026-12-29"),
                ("EU-TARGET,GB-CHAPS", "2026-04-02", "2026-04-07"),
            ):
                result = await _call(session, "business_day_offset", {
                    "calendar": calendar, "date": source, "offset": 1,
                })
                assert result["result_date"] == expected
                assert result["effective_confidence"] == "PROJECTED"
                assert result["examined_dates"][-1] == expected
            result = await _call(session, "adjust_date", {
                "calendar": "EU-TARGET,GB-CHAPS", "date": "2026-01-31",
                "convention": "MODIFIED_FOLLOWING",
            })
            assert result["result_date"] == "2026-01-30"
            assert result["examined_dates"] == ["2026-01-31", "2026-02-01", "2026-02-02", "2026-01-30"]
            result = await _call(session, "advance_months", {
                "calendar": "GB-CHAPS", "date": "2026-01-30", "months": 1,
                "convention": "FOLLOWING", "preserve_end_of_month": True,
            })
            assert result["result_date"] == "2026-02-27"
            assert result["effective_confidence"] == "PROJECTED"
            result = await _call(session, "last_business_day_of_month", {
                "calendar": "EU-TARGET,GB-CHAPS", "date": "2026-02-10",
            })
            assert result["result_date"] == "2026-02-27"
            error = await _call(session, "business_day_offset", {
                "calendar": "US-FEDWIRE", "date": "2027-12-31", "offset": 1,
            })
            assert error["error"] == "OutsideCoverageError"


@pytest.mark.anyio
async def test_finance_tools_round_trip():
    async with stdio_client(SERVER_PARAMS) as (read, write), ClientSession(read, write) as session:
        await session.initialize()
        tenor = await _call(
            session,
            "add_tenor",
            {"calendar": "US-NYSE", "date": "2024-01-31", "tenor": "3M", "convention": "MODIFIED_FOLLOWING"},
        )
        assert tenor["result_date"] == "2024-04-30" and tenor["tenor"] == "3M"
        assert tenor["operation"] == "ADD_TENOR" and tenor["month_offset"] == 3

        settlement = await _call(session, "add_tenor", {"calendar": "US-NYSE,GB-LSE", "date": "2024-05-24", "tenor": "T+2"})
        assert settlement["result_date"] == "2024-05-29", "Monday 27 May is a UK bank holiday"
        assert settlement["convention"] is None

        fraction = await _call(
            session,
            "year_fraction",
            {"calendar": "US-NYSE", "start": "2024-01-01", "end": "2024-02-01", "day_count": "BUS_252"},
        )
        assert fraction["days"] == 21 and fraction["year_fraction"] == 21 / 252
        assert fraction["effective_confidence"] in ("CONFIRMED", "PROJECTED")

        schedule = await _call(
            session,
            "generate_schedule",
            {
                "calendar": "US-NYSE",
                "start": "2024-01-15",
                "end": "2024-12-01",
                "frequency": "QUARTERLY",
                "stub": "SHORT_INITIAL",
            },
        )
        assert [p["unadjusted_end"] for p in schedule["periods"]] == ["2024-03-01", "2024-06-01", "2024-09-01", "2024-12-01"]
        assert schedule["periods"][0]["stub"] is True and schedule["periods"][0]["start"] == "2024-01-16"
        assert schedule["frequency"] == "QUARTERLY" and schedule["roll"] == "DAY_OF_MONTH"

        bad = await _call(session, "add_tenor", {"calendar": "US-NYSE", "date": "2024-01-31", "tenor": "3X"})
        assert "error" in bad
