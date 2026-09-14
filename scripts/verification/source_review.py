#!/usr/bin/env python3
"""Report evidence review age and coverage expiry; never infer an exchange announcement."""
import argparse
import datetime as dt
import json
from pathlib import Path


def review(root, today, horizon):
    for path in sorted((root / 'sources').glob('*/register.json')):
        for entry in json.loads(path.read_text())['entries']:
            try:
                retrieved = dt.date.fromisoformat(entry['retrieved'])
            except ValueError:
                continue
            if (today - retrieved).days > 180:
                yield (
                    f'{path.parent.name}/{entry["id"]}: '
                    f'source review is older than 180 days ({retrieved})'
                )
    for path in sorted((root / 'blessed').glob('*/metadata.json')):
        coverage = json.loads(path.read_text()).get('coverage') or {}
        for field in ('to', 'verified_through'):
            value = coverage.get(field)
            if value and dt.date.fromisoformat(value) <= today + dt.timedelta(days=horizon):
                yield f'{path.parent.name}: {field} expires {value}; review evidence and projections'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--as-of', type=dt.date.fromisoformat, default=dt.date.today())
    parser.add_argument('--horizon-days', type=int, default=90)
    args = parser.parse_args()
    warnings = list(review(args.root, args.as_of, args.horizon_days))
    for warning in warnings:
        print('::warning::' + warning)
    print(f'{len(warnings)} evidence/coverage items need review. This check does not confirm future schedules.')


if __name__ == '__main__':
    main()
