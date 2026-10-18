#!/usr/bin/env python3
"""Summarize three A/B pairs without calling callback cadence GPU FPS."""
import argparse
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    print('| Rodada | Callbacks/s | Mediana / p95 (ms) | Max (ms) | >100 ms | JVM→callback (ms) | Heap / RSS (MiB) | Foco (callbacks) |')
    print('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
    valid = True
    for engine in ['javafx', 'jme']:
        for index in range(1, 4):
            name = f'{engine}-{index}'
            folder = args.directory / name
            try:
                # Only selected scalar keys are read; no attempt to implement the Java Properties format.
                props = dict(line.split('=', 1) for line in (folder / 'summary.properties').read_text().splitlines()
                             if '=' in line and not line.startswith('#'))
                process = json.loads((folder / 'process.json').read_text())
                if (props.get('samplingComplete') != 'true' or props.get('unmodifiedBenchmark') != 'true'
                        or int(props['sampleCount']) == 0 or process['exitCode'] != 0 or process.get('timedOut')):
                    raise ValueError('incomplete, modified or failed run')
                median = float(props['medianIntervalMs'])
                p95 = float(props['p95IntervalMs'])
                maximum = float(props['maxIntervalMs'])
                heap = int(props['sampledPeakHeapBytes']) / 1024**2
                rss = process['sampledPeakRssBytes'] / 1024**2 if process['sampledPeakRssBytes'] is not None else None
                rss_text = f'{rss:.1f}' if rss is not None else 'N/D'
                focus = f"{props.get('focusedCallbacks', '?')}/{props.get('windowCallbacks', '?')}"
                print(f"| {name} | {props['callbackHz']} | {median:.3f} / {p95:.3f} | {maximum:.3f} | "
                      f"{props['intervalsOver100ms']} | {props['jvmStartToFirstCallbackMillis']} | "
                      f"{heap:.1f} / {rss_text} | {focus} |")
            except (OSError, ValueError, KeyError, TypeError) as error:
                valid = False
                print(f'| {name} | INDISPONIVEL | | | | | | |')
                print(f'{name}: {error}', file=sys.stderr)
    print('\nCallbacks are not GPU presentations. Focus counts include warmup and are not time proportions.')
    return 0 if valid else 1


if __name__ == '__main__':
    sys.exit(main())
