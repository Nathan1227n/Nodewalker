#!/usr/bin/env python3
"""Sequential real-window audits. A Robot attempt is recorded separately from human validation."""
import argparse
import datetime
import json
import os
import pathlib
import signal
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
CASES = [
    ('world-3d', []), ('world-fallback', ['--software-auto']),
    ('operational-3d', ['--operational']), ('operational-2d', ['--operational', '--software-2d']),
    ('operational-fallback', ['--operational', '--software-auto']),
    ('transitions-3d', ['--transitions']), ('transitions-2d', ['--transitions', '--software-2d']),
    ('weights-3d', ['--weights']), ('weights-2d', ['--weights', '--software-2d']),
    ('weights-fallback', ['--weights', '--software-auto']),
    ('playback-3d', ['--playback']), ('playback-2d', ['--playback', '--software-2d']),
    ('results-3d', ['--results']), ('results-2d', ['--results', '--software-2d']),
    ('robot-attempt', ['--native-input']),
]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=pathlib.Path, required=True)
    p.add_argument('--checkout', type=pathlib.Path, default=ROOT, help='Run from an isolated clone to avoid IDE/compiler changes to target/')
    p.add_argument('--maven-repo', type=pathlib.Path)
    p.add_argument('--settings', type=pathlib.Path)
    a = p.parse_args()
    a.output.mkdir(parents=True, exist_ok=True)
    records = []
    for name, flags in CASES:
        command = ['python3', 'tools/review_world.py', *flags, '--output', str((a.output/name).resolve())]
        env = os.environ.copy()
        if a.maven_repo:
            command += ['--maven-repo', str(a.maven_repo.resolve())]
            env['MAVEN_OPTS'] = '-Dmaven.repo.local=' + str(a.maven_repo.resolve())
        if a.settings:
            env['MAVEN_ARGS'] = '-s ' + str(a.settings.resolve()) + ' -gs ' + str(a.settings.resolve())
        start = time.monotonic()
        started = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with (a.output/(name+'.txt')).open('w') as log:
            process = subprocess.Popen(command, cwd=a.checkout.resolve(), env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            timed_out = False
            try:
                code = process.wait(timeout=180)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                code = process.wait(timeout=10)
                timed_out = True
        record = dict(name=name, command=command, checkout=str(a.checkout.resolve()), startedUtc=started,
                      durationSeconds=round(time.monotonic()-start, 3), exitCode=code, timeout=timed_out,
                      method='Robot automation' if name=='robot-attempt' else 'JavaFX API/synthetic events; real window',
                      humanPhysicalValidation='PENDENTE')
        records.append(record)
        (a.output/'commands.json').write_text(json.dumps(records, indent=2)+'\n')
        print(json.dumps(record), flush=True)
    failures = [r for r in records if r['exitCode'] != 0 and r['name'] != 'robot-attempt']
    if failures:
        raise SystemExit('Graphical failures: '+','.join(r['name'] for r in failures))


if __name__ == '__main__':
    main()
