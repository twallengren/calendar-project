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
            assert payload["completeness"]["SCHEDULED_CLOSURES"] == "PROJECTED"


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
            error = await _call(session, "is_business_day", {"calendar": "IL-TASE", "date": "2025-09-23"})
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
