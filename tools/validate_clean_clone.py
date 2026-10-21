#!/usr/bin/env python3
"""Opt-in MVP audit: remote clone + empty Maven repository; never edits the source workspace."""
import argparse
import datetime
import json
import os
import pathlib
import subprocess
import time

SOURCE = pathlib.Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=pathlib.Path, required=True)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--phase', choices=['build', 'gui'], default='build')
    args = parser.parse_args()
    root = args.root.resolve()
    root.mkdir(parents=True, exist_ok=True)
    repo, m2 = root / 'repo', root / 'm2'
    manifest = root / 'commands.json'
    records = json.loads(manifest.read_text()) if manifest.exists() else []
    env = os.environ.copy()
    for name in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS']:
        env.pop(name, None)

    def run(name, command, cwd=root, extra_env=None, timeout=600):
        started = datetime.datetime.now(datetime.timezone.utc).isoformat()
        begin = time.monotonic()
        with (root / (name + '.txt')).open('w') as log:
            try:
                result = subprocess.run(command, cwd=cwd, env=extra_env or env,
                                        stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
                code, timed_out = result.returncode, False
            except subprocess.TimeoutExpired:
                code, timed_out = None, True
        record = dict(name=name, command=list(map(str, command)), cwd=str(cwd), startedUtc=started,
                      durationSeconds=round(time.monotonic()-begin, 3), exitCode=code, timeout=timed_out)
        records.append(record)
        manifest.write_text(json.dumps(records, indent=2) + '\n')
        print(json.dumps(record), flush=True)
        if code != 0:
            raise SystemExit('Failed: ' + name + '; see ' + str(root / (name + '.txt')))

    settings = root / 'empty-settings.xml'
    if args.phase == 'build':
        if repo.exists() or m2.exists():
            raise SystemExit('Build requires a new root without repo/ or m2/.')
        settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n')
        m2.mkdir()
        run('01-clone', ['git', '-c', 'credential.helper=', '-c', 'credential.helper=!gh auth git-credential',
                         'clone', 'https://github.com/Nathan1227n/Nodewalker.git', str(repo)])
        run('02-checkout', ['git', 'checkout', '--detach', args.sha], repo)
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    if actual != args.sha:
        raise SystemExit('Clone SHA mismatch')
    mvn = ['mvn', '--batch-mode', '--no-transfer-progress', '-s', str(settings), '-gs', str(settings),
           '-Dmaven.repo.local=' + str(m2)]
    if args.phase == 'build':
        run('03-verify', mvn + ['clean', 'verify'], repo)
        run('04-install', mvn + ['clean', 'install'], repo)
        run('05-cli', ['java', '-jar', 'tsp-cli/target/nodewalker-cli.jar', '--n', '4', '--seed', '657',
                       '--solver', 'brute-force', '--warmup', '3', '--runs', '5'], repo)
        cli = (root / '05-cli.txt').read_text()
        for literal in ['Custo: 2799', 'Rota: 0 -> 2 -> 1 -> 3 -> 0', 'Benchmark: CONCLUIDO']:
            if literal not in cli:
                raise SystemExit('CLI reference mismatch: ' + literal)
    else:
        # The agent drives the actual default main class launched by javafx:run; no replacement app/solver.
        jars = sorted(m2.glob('org/openjfx/javafx-*/21.0.12/*-linux.jar'))
        classpath = os.pathsep.join(map(str, [repo/'tsp-core/target/classes', repo/'tsp-app/target/classes', *jars]))
        driver = root / 'driver-classes'
        driver.mkdir(exist_ok=True)
        run('06-driver-compile', ['javac', '-cp', classpath, '-d', str(driver),
                                 str(SOURCE/'tools/validation/CloneSmokeAgent.java'),
                                 str(SOURCE/'tools/validation/CloneSmokeDriver.java')])
        agent_manifest = root / 'agent-manifest.mf'
        agent_manifest.write_text('Premain-Class: CloneSmokeAgent\n\n')
        agent = root / 'clone-smoke-agent.jar'
        run('07-driver-jar', ['jar', 'cfm', str(agent), str(agent_manifest), '-C', str(driver), '.'])
        for name, options in [('08-javafx-default', []), ('09-javafx-2d', ['-Djavafx.args=--2d'])]:
            output = root / name
            output.mkdir(exist_ok=True)
            gui_env = dict(env, JAVA_TOOL_OPTIONS='-javaagent:' + str(agent) + '=' + str(output))
            run(name, mvn + ['-f', 'tsp-app/pom.xml', 'javafx:run', *options], repo, gui_env, 120)
            if not (output/'PASS.txt').exists():
                raise SystemExit('GUI exited without functional verification: ' + name)
    run('10-clean-status-' + args.phase, ['git', 'status', '--porcelain=v1'], repo)
    if (root/('10-clean-status-' + args.phase + '.txt')).read_text().strip():
        raise SystemExit('Tracked/untracked unexpected files in clone')


if __name__ == '__main__':
    main()
