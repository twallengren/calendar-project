#!/usr/bin/env python3
"""Maintain source tables from canonical sources/<MARKET>/register.json.

--import-readme performs the one-time text migration and refuses to overwrite a register.
Free-text coverage notes are preserved, never converted into verified support intervals.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

FIELDS = ['id', 'title', 'publisher', 'location', 'retrieved', 'covers', 'notes']
HEADER = '| id | title | publisher | url / file | retrieved | covers | notes |'
DIVIDER = '|----|-------|-----------|------------|-----------|--------|-------|'


def table_bounds(text):
    lines = text.splitlines(keepends=True)
    start = next(i for i, line in enumerate(lines) if line.strip() == HEADER)
    end = start + 2
    while end < len(lines) and lines[end].lstrip().startswith('|'):
        end += 1
    return lines, start, end


def render(register):
    rows = [HEADER, DIVIDER]
    for entry in register['entries']:
        values = [entry.get(field, '') for field in FIELDS]
        values[0] = '`' + values[0] + '`'
        if any('\n' in value or '|' in value for value in values):
            raise ValueError('Source table cells must not contain newlines or pipes')
        rows.append('| ' + ' | '.join(values) + ' |')
    return '\n'.join(rows) + '\n'


def migrate(readme, root):
    lines, start, end = table_bounds(readme.read_text())
    entries = []
    for line in lines[start + 2:end]:
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if len(cells) != len(FIELDS):
            raise ValueError(f'{readme}: malformed table row')
        entry = dict(zip(FIELDS, cells))
        entry['id'] = entry['id'].strip('`')
        entry['local_files'] = []
        for candidate in re.findall(r'`([^`]+)`', entry['location']):
            path = root.parent / candidate if candidate.startswith('sources/') else readme.parent / candidate
            if path.is_file():
                entry['local_files'].append({'path': path.relative_to(root).as_posix(),
                    'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        entry['support_intervals'] = []
        entries.append(entry)
    return {'schema_version': '1.0', 'entries': entries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources-dir', type=Path, default=Path('sources'))
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--import-readme', action='store_true')
    args = parser.parse_args()
    failures = []
    for readme in sorted(args.sources_dir.glob('*/README.md')):
        path = readme.with_name('register.json')
        if args.import_readme:
            with path.open('x') as target:
                json.dump(migrate(readme, args.sources_dir), target, indent=2, ensure_ascii=False)
                target.write('\n')
        if not path.is_file():
            failures.append(str(path) + ': missing canonical register')
            continue
        register = json.loads(path.read_text())
        lines, start, end = table_bounds(readme.read_text())
        expected = ''.join(lines[:start]) + render(register) + ''.join(lines[end:])
        if args.check:
            if readme.read_text() != expected:
                failures.append(str(readme) + ': table differs from canonical register')
        else:
            readme.write_text(expected)
    if failures:
        raise SystemExit('\n'.join(failures))


if __name__ == '__main__':
    main()
