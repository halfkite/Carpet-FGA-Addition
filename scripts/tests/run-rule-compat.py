"""Run an opt-in test mod on an isolated dedicated-server world; retain logs/world for diagnosis."""
import argparse
import datetime
import json
import os
import re
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--version', required=True, choices=['1.21.1', '26.2'])
parser.add_argument('--suite', required=True)
parser.add_argument('--world', help='Reuse a named test world to verify a real process restart')
parser.add_argument('--priority', choices=['900', '1100'], default='1100')
args = parser.parse_args()
if not re.fullmatch(r'[a-z0-9-]+', args.suite):
    parser.error('suite must contain only lowercase letters, digits and hyphens')
if args.world and not re.fullmatch(r'rule-compat-[a-zA-Z0-9-]+', args.world):
    parser.error('world must start with rule-compat- and contain only letters, digits and hyphens')
root = Path(__file__).resolve().parents[2]
stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
run = root / 'versions' / args.version / 'run'
run.mkdir(parents=True, exist_ok=True)
report = root / 'scripts' / 'logs' / f'{args.suite}-{args.version}-{stamp}'
report.mkdir(parents=True)
log = report / 'server.log'
backups = {name: (run / name).read_bytes() if (run / name).exists() else None
           for name in ['eula.txt', 'server.properties']}
env = dict(os.environ)
env['JAVA_HOME'] = 'C:/Program Files/Java/' + ('jdk-25.0.3' if args.version == '26.2' else 'jdk-21.0.11')
try:
    (run / 'eula.txt').write_text('eula=true\n')
    (run / 'server.properties').write_text(
        f'online-mode=false\nserver-port=0\nview-distance=2\nsimulation-distance=2\n'
        f'level-name={args.world or ("rule-compat-" + args.suite + "-" + stamp)}\n')
    command = [str(root / 'gradlew.bat'), f':{args.version}:runServer', '-I',
               f'scripts/tests/{args.suite}.gradle', f'-PcompatPriority={args.priority}',
               '--no-daemon', '--configure-on-demand', '--max-workers=2']
    with log.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(command, cwd=root, env=env, stdin=subprocess.PIPE,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            code = process.wait(timeout=480)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            raise
    text = log.read_text(encoding='utf-8', errors='replace')
    marker = 'FGA_' + args.suite.upper().replace('-', '_') + '_PASS'
    passed = code == 0 and marker in text and 'COMPAT_FAIL' not in text
    result = dict(version=args.version, suite=args.suite, priority=args.priority, passed=passed, exitCode=code, log=str(log))
    (report / 'summary.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
    if not passed:
        print(text[-7000:])
        raise SystemExit(1)
finally:
    for name, content in backups.items():
        if content is None:
            (run / name).unlink(missing_ok=True)
        else:
            (run / name).write_bytes(content)
