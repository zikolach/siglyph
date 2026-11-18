#!/usr/bin/env python3
"""Compile and execute unreleased DX consumers against artifacts, never workspace class roots."""
import argparse
import importlib.util
import os
from pathlib import Path
import re
import tempfile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('compatibility', ROOT / 'scripts/check-compatibility.py')
compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compat)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--repository', action='append', default=[])
    parser.add_argument('--published', action='store_true', help='Resolve only Maven Central after a real release')
    parser.add_argument('--native', action='store_true', help='Also rebuild/run the shared Scala consumer on Native')
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?', args.version):
        parser.error('version must be explicit')
    if args.published and args.repository:
        parser.error('--published cannot use a local/custom repository')
    if args.published and not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', args.version):
        parser.error('--published requires a release version')
    central = ['https://repo.maven.apache.org/maven2']
    repositories = args.repository + central
    cp = compat.fetch(compat.jvm_coordinates(args.version), repositories)
    jdk = Path(compat.run(['cs', 'java-home', '--jvm', compat.POLICY['jvm']], capture=True).strip()) / 'bin'
    kotlin = compat.fetch([f'org.jetbrains.kotlin:kotlin-compiler-embeddable:{compat.POLICY["kotlin"]}'], central)
    runtime = compat.fetch([f'org.jetbrains.kotlin:kotlin-stdlib:{compat.POLICY["kotlin"]}'], central)
    examples = ROOT / 'examples/language-dx'
    with tempfile.TemporaryDirectory(prefix='siglyph-language-dx-', dir=os.environ.get('EMPRYO_TMP')) as directory:
        work = Path(directory)
        java = work / 'java'
        java.mkdir()
        compat.run([jdk / 'javac', '--release', '21', '-cp', cp, '-d', java, examples / 'java/DxApp.java'])
        compat.run([jdk / 'java', '-cp', str(java) + os.pathsep + cp, 'DxApp'])
        kt = work / 'kotlin'
        kt.mkdir()
        compat.run([jdk / 'java', '-cp', kotlin, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-jvm-target', '21', '-classpath', cp + os.pathsep + runtime,
                    '-d', kt, examples / 'kotlin/SiglyphDsl.kt', examples / 'kotlin/DxApp.kt'])
        compat.run([jdk / 'java', '-cp', str(kt) + os.pathsep + cp + os.pathsep + runtime, 'DxAppKt'])
        compat.run(compat.scala_command('run', examples / 'scala/DxApp.scala', args.version, repositories))
        compat.run(compat.scala_command('compile', examples / 'scala/DxInteractive.scala', args.version, repositories))
        if args.native:
            compat.run(compat.scala_command('run', examples / 'scala/DxApp.scala', args.version, repositories, native=True))
    print(f'Language DX artifact consumers passed: {args.version}; published={args.published}; native={args.native}')


if __name__ == '__main__':
    main()
