#!/usr/bin/env python3
"""Check one generated native occurrence across artifact CLI, Python, v2 and HTML.

Run with the repository's Python test environment (including the MCP extra).
Artifacts and site are scratch outputs from generate --include-specs and site.
"""
import argparse
import json
from pathlib import Path
import subprocess

from bdc_calendars._loader import CalendarData, _parse_events
from bdc_calendars.calendar import SingleCalendar
from bdc_calendars.mcp.server import _assessment_dict


def verify(args):
    metadata = json.loads((args.site / 'v1/calendars' / args.calendar / 'manifest.json').read_text())
    artifact = args.artifacts / args.calendar
    rows = [event for event in _parse_events((artifact / 'events.csv').read_text()) if event.type != 'WEEKEND']
    calendar = SingleCalendar(CalendarData(args.calendar, metadata, rows))
    python = _assessment_dict(calendar.assessment(args.date))
    result = subprocess.run([str(args.tools), 'query', args.calendar, '--as-of', 'blessed',
                             '--blessed-dir', str(args.artifacts), '--assess-day', args.date],
                            check=True, capture_output=True, text=True)
    java = json.loads(result.stdout)
    for key in ('date', 'state', 'scheduled_state', 'effective_confidence', 'completeness', 'evidence_ids'):
        assert python[key] == java[key], key
    assert len(python['events']) == len(java['events'])
    for py_event, java_event in zip(python['events'], java['events']):
        for key in ('raw_status', 'effective_status', 'nominal_native_date', 'chronology_profile',
                    'chronology_provider', 'evidence_ids', 'observation_lineage'):
            assert py_event[key] == java_event[key], key
    native = [event['nominal_native_date'] for event in java['events'] if event['nominal_native_date']]
    if args.expect_iso:
        assert java['events'] and not native, 'Expected cited ISO overrides without native identity'
        for event in java['events']:
            assert set(args.evidence_id).issubset(event['evidence_ids'])
    else:
        assert native, 'Expected a native event occurrence'
    wire = json.loads((args.site / 'v2/calendars' / args.calendar / (args.date[:4] + '.json')).read_text())
    assert next(day for day in wire['days'] if day['date'] == args.date) == java
    page = (args.site / args.calendar / args.date / 'index.html').read_text()
    for date in native:
        assert '{} {} {} {}'.format(date['chronology_id'], date['year'], date['month_code'], date['day']) in page
    if java['state'] == 'UNKNOWN':
        assert 'Actual trading state is unknown' in page
    print('{} {}: native artifact/Java CLI/Python/MCP representation/v2/HTML parity passed'.format(args.calendar, args.date))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools', type=Path, default=Path('tools/build/install/tools/bin/tools'))
    parser.add_argument('--artifacts', type=Path, required=True)
    parser.add_argument('--site', type=Path, required=True)
    parser.add_argument('--calendar', default='IL-TASE')
    parser.add_argument('--date', default='2025-09-23')
    parser.add_argument('--expect-iso', action='store_true')
    parser.add_argument('--evidence-id', action='append', default=[])
    verify(parser.parse_args())
