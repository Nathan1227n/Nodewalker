#!/usr/bin/env python3
"""Three sequential A/B pairs; alternates engine order. Keep the desktop idle."""
import argparse
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--simple', action='store_true')
args = parser.parse_args()
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=False)
runner = Path(__file__).with_name('run.py')
for round_number in range(1, 4):
    order = ['javafx', 'jme'] if round_number % 2 else ['jme', 'javafx']
    for engine in order:
        command = [sys.executable, str(runner), engine, '--benchmark', '--warmup=10', '--duration=60',
                   '--output=' + str(root / f'{engine}-{round_number}')]
        if args.simple:
            command.append('--simple')
        subprocess.run(command, check=True)
