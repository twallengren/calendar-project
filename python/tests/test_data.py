"""The bundled data: versioning, completeness and agreement with ``blessed/``."""

from __future__ import annotations

import json
import os
import subprocess
import sys

import pytest

import bdc_calendars as bdc
from bdc_calendars import _loader

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BLESSED_MANIFEST = os.path.join(REPO_ROOT, "blessed", "manifest.json")
SYNC_SCRIPT = os.path.join(REPO_ROOT, "python", "scripts", "sync_data.py")


def test_package_version_comes_from_independent_version_stream():
    versions_path = os.path.join(REPO_ROOT, "release", "versions.json")
    with open(versions_path, encoding="utf-8") as handle:
        versions = json.load(handle)
    assert bdc.__version__ == versions["python"]


def test_version_matches_the_bundled_manifest():
    manifest = _loader.manifest()
    assert bdc.data_version == manifest["data_version"]
    assert bdc.data_git_sha == manifest["data_git_sha"]
    assert bdc.data_generation_date == manifest["generation_date"]


def test_every_manifest_calendar_loads():
    for calendar_id in bdc.list_calendars():
        calendar = bdc.get_calendar(calendar_id)
        assert calendar.calendar_id == calendar_id
        assert calendar.range.start <= calendar.range.end
        assert calendar.kind in ("market", "payment", "base")


def test_manifest_ranges_match_the_calendars():
    manifest = _loader.manifest()
    for calendar_id, entry in manifest["calendars"].items():
        calendar = bdc.get_calendar(calendar_id)
        assert calendar.range.start.isoformat() == entry["range_start"]
        assert calendar.range.end.isoformat() == entry["range_end"]


def test_no_weekend_rows_are_shipped():
    for calendar_id in bdc.list_calendars():
        data = _loader.load_calendar(calendar_id)
        rows = [e for rows in data.events_by_date.values() for e in rows]
        assert rows, calendar_id
        assert not [e for e in rows if e.type == "WEEKEND"], calendar_id


@pytest.mark.skipif(
    not os.path.exists(BLESSED_MANIFEST),
    reason="blessed/ is not available (running from an installed wheel)",
)
def test_bundled_data_is_in_sync_with_blessed():
    """``sync_data.py --check`` re-derives the data and compares it byte for byte."""
    result = subprocess.run(
        [sys.executable, SYNC_SCRIPT, "--check"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.skipif(
    not os.path.exists(BLESSED_MANIFEST),
    reason="blessed/ is not available (running from an installed wheel)",
)
def test_data_version_matches_blessed():
    with open(BLESSED_MANIFEST, encoding="utf-8") as handle:
        blessed = json.load(handle)
    assert bdc.data_version == blessed["release_version"]["semantic"]
    assert bdc.data_git_sha == blessed["release_version"]["git_sha"]
