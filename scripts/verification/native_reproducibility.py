#!/usr/bin/env python3
"""Compare native artifacts and query JSON emitted by independent JVM processes."""
import argparse
from pathlib import Path
import subprocess
import tempfile


def verify(tools, repetitions):
    executable = str(tools.resolve())
    baseline = None
    with tempfile.TemporaryDirectory(prefix='bdc-native-repro-') as directory:
        root = Path(directory)
        for iteration in range(repetitions):
            outputs = {}
            for calendar, first, last in [('IL-TASE', '2026-04-21', '2026-04-22'),
                                           ('HK-HKEX', '2025-01-29', '2025-02-03')]:
                destination = root / str(iteration) / calendar
                subprocess.run([executable, 'generate', calendar, '--from', first, '--to', last,
                                '--out', str(destination), '--include-specs', '--source-version', '0' * 40,
                                '--release-version', '0.0.0', '--generated-at', '2026-09-14T00:00:00Z'],
                               check=True, capture_output=True)
                for path in sorted(destination.rglob('*')):
                    if path.is_file():
                        outputs[calendar + '/' + str(path.relative_to(destination))] = path.read_bytes()
                resolved = destination / 'cli-resolved.yaml'
                subprocess.run([executable, 'resolve', calendar, '--out', str(resolved)],
                               check=True, capture_output=True)
                outputs[calendar + '/cli-resolved.yaml'] = resolved.read_bytes()
                outputs[calendar + '/assessment.json'] = subprocess.check_output(
                    [executable, 'query', calendar, '--assess-day', first], stderr=subprocess.DEVNULL)
            outputs['conversion.json'] = subprocess.check_output(
                [executable, 'convert', '--from-chronology', 'HEBREW', '--year', '5786',
                 '--month-code', 'TISHRI', '--day', '1', '--to-chronology', 'ISO', '--format', 'json'])
            if baseline is not None:
                assert outputs.keys() == baseline.keys(), 'Output membership differs between JVMs'
                for name in outputs:
                    assert outputs[name] == baseline[name], 'Non-reproducible output: ' + name
            baseline = outputs
    print('{} native outputs are byte-identical across {} independent JVM runs per command'.format(
        len(baseline), repetitions))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools', type=Path, default=Path('tools/build/install/tools/bin/tools'))
    parser.add_argument('--repetitions', type=int, default=3)
    args = parser.parse_args()
    if args.repetitions < 2:
        parser.error('--repetitions must be at least two')
    verify(args.tools, args.repetitions)
