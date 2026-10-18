#!/usr/bin/env python3
"""Fedora/Linux runner: separate JVM, stdout/stderr, /proc RSS samples; no asset downloads.
Run from any directory. Maven builds must have populated target/classes and target/dependency.
"""
import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
import sys
import time

SPIKE = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('engine', choices=['javafx', 'jme'])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--benchmark', action='store_true')
    parser.add_argument('--capture-only', action='store_true')
    parser.add_argument('--simple', action='store_true')
    parser.add_argument('--warmup', type=int, default=10)
    parser.add_argument('--duration', type=int, default=60)
    args = parser.parse_args()
    if args.benchmark and args.capture_only:
        parser.error('Capture in a separate run from benchmarks')
    if not 0 <= args.warmup <= 120 or not 1 <= args.duration <= 300:
        parser.error('warmup must be 0..120; duration must be 1..300 seconds')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)  # Never overwrite another observation.
    module = SPIKE / ('javafx-prototype' if args.engine == 'javafx' else 'jme-prototype')
    if not (module / 'target/classes').is_dir() or not (module / 'target/dependency').is_dir():
        parser.error('Build the prototype first; see docs/SPIKE_RENDERIZADORES.md')
    classpath = os.pathsep.join([str(module / 'target/classes'), str(module / 'target/dependency/*')])
    launch = ('io.github.nathan1227n.nodewalker.spike.fx.FxLauncher' if args.engine == 'javafx'
              else 'io.github.nathan1227n.nodewalker.spike.jme.JmePrototype')
    command = ['java', '-Xms64m', '-Xmx512m']
    if args.engine == 'javafx':
        command += ['-Dprism.verbose=true', '-Dprism.vsync=true']
    command += ['-cp', classpath, launch, '--output=' + str(output),
                '--warmup=' + str(args.warmup), '--duration=' + str(args.duration)]
    for flag in ['benchmark', 'capture_only', 'simple']:
        if getattr(args, flag):
            command.append('--' + flag.replace('_', '-'))
    metadata = {'command': command, 'rssMethod': '/proc/PID/status VmRSS; 250 ms polling; excludes VRAM',
                'display': os.environ.get('DISPLAY'), 'waylandDisplay': os.environ.get('WAYLAND_DISPLAY'),
                'sessionType': os.environ.get('XDG_SESSION_TYPE'), 'samplePeriodSeconds': .25}
    samples = []
    started = time.monotonic()
    watchdog = args.warmup + args.duration + 30 if args.benchmark else 30 if args.capture_only else None
    timed_out = False
    with (output / 'console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=SPIKE, stdout=log, stderr=subprocess.STDOUT)
        metadata['pid'] = process.pid
        try:
            while process.poll() is None:
                if watchdog is not None and time.monotonic() - started > watchdog:
                    timed_out = True
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    break
                try:
                    lines = Path(f'/proc/{process.pid}/status').read_text().splitlines()
                    rss = next(int(line.split()[1]) * 1024 for line in lines if line.startswith('VmRSS:'))
                    samples.append((time.monotonic() - started, rss))
                except (FileNotFoundError, StopIteration, PermissionError):
                    pass
                time.sleep(.25)  # Sampling cadence, never a test success condition.
        except KeyboardInterrupt:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    metadata['exitCode'] = process.returncode
    metadata['watchdogSeconds'] = watchdog
    metadata['timedOut'] = timed_out
    metadata['launchToExitSeconds'] = time.monotonic() - started
    metadata['sampledPeakRssBytes'] = max((rss for _, rss in samples), default=None)
    with (output / 'rss.csv').open('w', newline='') as file:
        writer = csv.writer(file); writer.writerow(['seconds_since_launch', 'rss_bytes']); writer.writerows(samples)
    (output / 'process.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps({'output': str(output), 'exitCode': process.returncode,
                      'sampledPeakRssBytes': metadata['sampledPeakRssBytes']}, indent=2))
    if timed_out:
        print('Watchdog terminated the JVM: reject this run and inspect console.log.', file=sys.stderr)
        return 1
    if not (output / 'summary.properties').exists():
        print('No application summary: inspect console.log; this is not a successful graphical run.', file=sys.stderr)
        return process.returncode or 1
    summary = (output / 'summary.properties').read_text()
    if 'renderingError=' in summary or 'scene3d=false' in summary:
        return 1
    if args.benchmark and ('samplingComplete=true' not in summary or 'unmodifiedBenchmark=true' not in summary):
        print('Incomplete or modified benchmark: reject as a comparison sample.', file=sys.stderr)
        return 1
    return process.returncode


if __name__ == '__main__':
    sys.exit(main())
