#!/usr/bin/env python3
"""Record repeatable CLI generation/query timings, including JVM startup.

Run from the repository root after :tools:installDist. Compare reports produced
on the same machine; these are observations, not machine-dependent CI limits.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import platform
import statistics
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools', type=Path, default=Path('tools/build/install/tools/bin/tools'))
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--out', type=Path, default=Path('build/benchmark.json'))
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error('--repeats must be positive')
    executable = str(args.tools.resolve())
    results = {}
    with tempfile.TemporaryDirectory(prefix='bdc-benchmark-') as scratch:
        cases = {
            'hebrew_generation_2025_2027': ['generate', 'IL-TASE', '--from', '2025-01-01', '--to', '2027-12-31', '--out', scratch + '/hebrew'],
            'chinese_generation_2018_2027': ['generate', 'HK-HKEX', '--from', '2018-01-01', '--to', '2027-12-31', '--out', scratch + '/chinese'],
            'nyse_generation_1900_2030': ['generate', 'US-NYSE', '--from', '1900-01-01', '--to', '2030-12-31', '--out', scratch + '/nyse'],
            'artifact_count_year': ['query', 'US-NYSE', '--as-of', 'blessed', '--business-days-from', '2025-01-01', '--business-days-to', '2025-12-31'],
            'native_assessment': ['query', 'IL-TASE', '--assess-day', '2025-09-23'],
            'joint_offset': ['query', 'US-NYSE,SA-TADAWUL', '--as-of', 'blessed', '--from', '2026-02-25', '--nth-business-day', '20'],
        }
        for name, command in cases.items():
            samples = []
            for _ in range(args.repeats):
                start = time.perf_counter()
                subprocess.run([executable] + command, check=True, capture_output=True)
                samples.append(time.perf_counter() - start)
            results[name] = {'seconds': samples, 'median_seconds': statistics.median(samples)}
            print('{}: {:.3f}s median'.format(name, results[name]['median_seconds']))
    report = {
        'measured_at': dt.datetime.now(dt.timezone.utc).isoformat(),
        'source_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
        'platform': platform.platform(),
        'includes_jvm_startup': True,
        'results': results,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
