#!/usr/bin/env python3
"""Validate ten locally staged coordinates; never publishes or fetches artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ARTIFACTS = [
    'siglyph-core_3', 'siglyph-terminal-jvm_3', 'siglyph-markdown_3',
    'siglyph-image_3', 'siglyph-extras_3', 'siglyph-core_native0.5_3',
    'siglyph-terminal-native_native0.5_3', 'siglyph-markdown_native0.5_3',
    'siglyph-image_native0.5_3', 'siglyph-extras_native0.5_3',
]


def validate(repository, version):
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z0-9.-]+', version):
        raise ValueError('an explicit non-release candidate version is required')
    payloads = []
    for artifact in ARTIFACTS:
        directory = repository / 'io/github/zikolach' / artifact / version
        for suffix in ('.pom', '.jar', '-sources.jar', '-javadoc.jar'):
            path = directory / (artifact + '-' + version + suffix)
            data = path.read_bytes()
            if not data:
                raise ValueError(f'empty payload: {path}')
            if suffix == '.pom':
                root = ET.fromstring(data)
                namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
                for key, expected in (('groupId', 'io.github.zikolach'), ('artifactId', artifact), ('version', version)):
                    if root.findtext('m:' + key, namespaces=namespace) != expected:
                        raise ValueError(f'incorrect {key}: {path}')
                for dependency in root.findall('m:dependencies/m:dependency', namespace):
                    if dependency.findtext('m:groupId', namespaces=namespace) == 'io.github.zikolach':
                        if dependency.findtext('m:version', namespaces=namespace) != version:
                            raise ValueError(f'mixed Siglyph dependency version: {path}')
            else:
                with zipfile.ZipFile(path) as archive:
                    if archive.testzip() is not None or not archive.namelist():
                        raise ValueError(f'invalid/empty ZIP: {path}')
                    if suffix == '.jar' and '_native' in artifact and not any(name.endswith('.nir') for name in archive.namelist()):
                        raise ValueError(f'Native IR missing: {path}')
            payloads.append({'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    return {'version': version, 'coordinates': len(ARTIFACTS), 'payloadCount': len(payloads),
            'scope': 'local only; hashes recorded, not remote checksum or signer-trust verification',
            'payloads': payloads}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    args = parser.parse_args()
    print(json.dumps(validate(args.repository, args.version), indent=2))


if __name__ == '__main__':
    main()
