import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('local_release', Path(__file__).with_name('check-local-release.py'))
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class LocalReleaseTests(unittest.TestCase):
    def stage(self, repository, version='0.13.0-test'):
        for artifact in CHECK.ARTIFACTS:
            directory = repository / 'io/github/zikolach' / artifact / version
            directory.mkdir(parents=True)
            stem = directory / (artifact + '-' + version)
            Path(str(stem) + '.pom').write_text(
                '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                f'<groupId>io.github.zikolach</groupId><artifactId>{artifact}</artifactId>'
                f'<version>{version}</version></project>')
            for suffix in ('.jar', '-sources.jar', '-javadoc.jar'):
                with zipfile.ZipFile(str(stem) + suffix, 'w') as archive:
                    archive.writestr('fixture.nir' if '_native' in artifact else 'fixture.class', b'fixture')

    def test_all_payloads_and_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.stage(root)
            result = CHECK.validate(root, '0.13.0-test')
            self.assertEqual((result['coordinates'], result['payloadCount']), (10, 40))
            self.assertTrue(all(len(item['sha256']) == 64 for item in result['payloads']))

    def test_release_or_path_versions_rejected(self):
        for version in ('0.13.0', '../candidate', '0.13.0-test/other'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                CHECK.validate(Path('/unused'), version)

    def test_missing_payload_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, self.assertRaises(FileNotFoundError):
            CHECK.validate(Path(temporary), '0.13.0-test')

    def test_wrong_coordinate_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.stage(root)
            pom = next(root.rglob('*.pom'))
            pom.write_text(pom.read_text().replace('<version>0.13.0-test</version>', '<version>0.12.0</version>'))
            with self.assertRaisesRegex(ValueError, 'incorrect version'):
                CHECK.validate(root, '0.13.0-test')

    def test_native_ir_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.stage(root)
            jar = next(root.rglob('siglyph-core_native0.5_3-0.13.0-test.jar'))
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('fixture.class', b'fixture')
            with self.assertRaisesRegex(ValueError, 'Native IR missing'):
                CHECK.validate(root, '0.13.0-test')


if __name__ == '__main__':
    unittest.main()
