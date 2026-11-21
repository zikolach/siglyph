#!/usr/bin/env python3
"""Local compatibility gate. Does not publish, tag, or claim whole-core/Native ABI compatibility."""
import argparse
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POLICY = json.loads((ROOT / 'compatibility/policy.json').read_text())


def run(args, capture=False):
    return subprocess.run([str(arg) for arg in args], check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def surface(text, class_name):
    header = None
    members = {}
    declaration = None
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith('public ') and line.endswith('{'):
            header = line
        elif line.startswith(('public ', 'protected ')) and line.endswith(';'):
            declaration = line
        elif line.startswith('descriptor:') and declaration:
            token = declaration.split('(', 1)[0].split()[-1].rstrip(';')
            name = '<init>' if token == class_name else token
            descriptor = line.split(':', 1)[1].strip()
            key = (name, descriptor, ' static ' in ' ' + declaration)
            members[key] = {'abstract': ' abstract ' in ' ' + declaration,
                            'final': ' final ' in ' ' + declaration}
            declaration = None
    if header is None:
        raise ValueError(f'javap did not describe {class_name}')
    return header, members


def compare(before, after, selected):
    errors = []
    if before[0] != after[0]:
        errors.append(f'class shape changed: {before[0]} -> {after[0]}')
    all_members = '*' in selected
    for name in selected:
        if name != '*' and not any(key[0] == name for key in before[1]):
            errors.append(f'policy names absent baseline member: {name}')
    for key, flags in before[1].items():
        if not all_members and key[0] not in selected:
            continue
        if key not in after[1]:
            errors.append(f'missing/changed member: {key}')
        elif not flags['abstract'] and after[1][key]['abstract']:
            errors.append(f'concrete member became abstract: {key}')
        elif not flags['final'] and after[1][key]['final']:
            errors.append(f'member became final: {key}')
    if ' interface ' in before[0] or ' abstract class ' in before[0]:
        for key, flags in after[1].items():
            if flags['abstract'] and key not in before[1]:
                errors.append(f'new abstract obligation: {key}')
    return errors


def fetch(coordinates, repositories):
    command = ['env', 'COURSIER_REPOSITORIES=' + '|'.join(repositories), 'cs', 'fetch', '--classpath']
    for repository in repositories:
        command += ['--repository', repository]
    return run(command + coordinates, capture=True).strip()


def jvm_coordinates(version):
    return [f'io.github.zikolach:siglyph-core_3:{version}',
            f'io.github.zikolach:siglyph-terminal-jvm_3:{version}']


def scala_command(action, source, version, repositories, native=False, scala_version=None):
    command = ['env', 'COURSIER_REPOSITORIES=' + '|'.join(repositories), 'scala-cli']
    if action == 'package':
        command += ['--power']
    command += [action, str(source), '--scala', scala_version or POLICY['scala'], '--jvm', '21']
    for repository in repositories:
        command += ['--repository', repository]
    if native:
        command += ['--native', '--native-version', POLICY['native']]
        command += ['--dependency', f'io.github.zikolach::siglyph-core::{version}',
                    '--dependency', f'io.github.zikolach::siglyph-terminal-native::{version}']
    else:
        command += ['--dependency', f'io.github.zikolach::siglyph-core:{version}',
                    '--dependency', f'io.github.zikolach::siglyph-terminal-jvm:{version}']
    return command


def inventory_errors(root):
    recorded = json.loads((root / 'compatibility/api-inventory.json').read_text())
    expected = {entry['path'] for entry in recorded['files']}
    actual = {str(path.relative_to(root)) for module in ('core', 'terminalJvm', 'terminalNative', 'markdown', 'image', 'extras')
              for path in (root / module / 'src').rglob('*.scala')}
    return ([f'unclassified source file: {path}' for path in sorted(actual - expected)] +
            [f'inventory points to removed source: {path}' for path in sorted(expected - actual)])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate')
    parser.add_argument('--inventory-only', action='store_true')
    parser.add_argument('--repository', action='append', default=[])
    parser.add_argument('--skip-native', action='store_true')
    args = parser.parse_args()
    inventory = inventory_errors(ROOT)
    if inventory:
        raise SystemExit('\n'.join(inventory))
    if args.inventory_only:
        print('API source-file inventory is current')
        return
    if not args.candidate:
        parser.error('--candidate is required unless --inventory-only is used')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?', args.candidate):
        parser.error('candidate must be an explicit version, not a dynamic version or coordinate')
    repositories = args.repository + ['https://repo.maven.apache.org/maven2']
    central = ['https://repo.maven.apache.org/maven2']
    baseline = POLICY['baseline']
    jdk = Path(run(['cs', 'java-home', '--jvm', POLICY['jvm']], capture=True).strip()) / 'bin'
    old_cp = fetch(jvm_coordinates(baseline), central)
    candidate_cp = fetch(jvm_coordinates(args.candidate), repositories)
    errors = []
    for name, selected in POLICY['classes'].items():
        old = surface(run([jdk / 'javap', '-protected', '-s', '-classpath', old_cp, name], capture=True), name)
        new = surface(run([jdk / 'javap', '-protected', '-s', '-classpath', candidate_cp, name], capture=True), name)
        errors += [f'{name}: {error}' for error in compare(old, new, selected)]
    if errors:
        raise SystemExit('\n'.join(errors))
    fixtures = ROOT / 'compatibility/consumers'
    kotlin_cp = fetch([f'org.jetbrains.kotlin:kotlin-compiler-embeddable:{POLICY["kotlin"]}'], central)
    kotlin_runtime = fetch([f'org.jetbrains.kotlin:kotlin-stdlib:{POLICY["kotlin"]}'], central)
    with tempfile.TemporaryDirectory(prefix='siglyph-compat-', dir=os.environ.get('EMPRYO_TMP')) as temporary:
        work = Path(temporary)
        for version, cp, label in ((baseline, old_cp, 'baseline'), (args.candidate, candidate_cp, 'candidate')):
            selected_repositories = central if label == 'baseline' else repositories
            scala_version = POLICY['baselineScala'] if label == 'baseline' else POLICY['scala']
            java_classes = work / ('java-' + label)
            java_classes.mkdir()
            run([jdk / 'javac', '--release', '21', '-classpath', cp, '-d', java_classes, fixtures / 'JavaConsumer.java'])
            run([jdk / 'java', '-cp', str(java_classes) + os.pathsep + cp, 'JavaConsumer'])
            if label == 'baseline':
                run([jdk / 'java', '-cp', str(java_classes) + os.pathsep + candidate_cp, 'JavaConsumer'])
            kotlin_classes = work / ('kotlin-' + label)
            kotlin_classes.mkdir()
            run([jdk / 'java', '-cp', kotlin_cp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                 '-no-stdlib', '-no-reflect', '-jvm-target', '21', '-classpath', cp + os.pathsep + kotlin_runtime,
                 '-d', kotlin_classes, fixtures / 'KotlinConsumer.kt'])
            run([jdk / 'java', '-cp', str(kotlin_classes) + os.pathsep + cp + os.pathsep + kotlin_runtime, 'KotlinConsumerKt'])
            if label == 'baseline':
                run([jdk / 'java', '-cp', str(kotlin_classes) + os.pathsep + candidate_cp + os.pathsep + kotlin_runtime, 'KotlinConsumerKt'])
            run(scala_command('run', fixtures / 'ScalaConsumer.scala', version, selected_repositories, scala_version=scala_version))
            if not args.skip_native:
                run(scala_command('run', fixtures / 'NativeConsumer.scala', version, selected_repositories, native=True, scala_version=scala_version))
        binary = work / 'baseline-scala.jar'
        run(scala_command('package', fixtures / 'ScalaConsumer.scala', baseline, central,
                          scala_version=POLICY['baselineScala']) + ['--library', '-o', str(binary)])
        run([jdk / 'java', '-cp', str(binary) + os.pathsep + candidate_cp, 'ScalaConsumer'])
    print(json.dumps({'baseline': baseline, 'candidate': args.candidate,
                      'baselineScala': POLICY['baselineScala'], 'candidateScala': POLICY['scala'],
                      'selectedJvmAbi': 'passed', 'baselineBinariesOnCandidate': ['Scala', 'Java', 'Kotlin'],
                      'sourceConsumers': ['Scala JVM', 'Java', 'Kotlin'] + ([] if args.skip_native else ['Scala Native']),
                      'nativeSkipped': args.skip_native, 'repositories': repositories}))


if __name__ == '__main__':
    main()
