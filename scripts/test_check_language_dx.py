import contextlib
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('language_dx', Path(__file__).with_name('check-language-dx.py'))
DX = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DX)


class LanguageDxCheckerTests(unittest.TestCase):
    def rejected(self, arguments):
        with patch('sys.argv', ['check-language-dx.py'] + arguments), patch.object(DX.compat, 'fetch') as fetch:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                DX.main()
            self.assertEqual(error.exception.code, 2)
            fetch.assert_not_called()

    def test_published_rejects_custom_repository_before_resolution(self):
        self.rejected(['--version', '1.0.0', '--published', '--repository', 'file:///tmp/repo'])

    def test_published_rejects_snapshots_and_prereleases(self):
        for version in ['1.0.0-SNAPSHOT', '1.0.0-dx-SNAPSHOT', '1.0.0-rc1', '1.0.0.rc1']:
            with self.subTest(version=version):
                self.rejected(['--version', version, '--published'])

    def test_versions_are_explicit_and_not_coordinates_or_shell(self):
        for version in ['latest.release', '1.+', 'group:artifact:1.0.0', '1.0.0;echo bad', '']:
            with self.subTest(version=version):
                self.rejected(['--version', version])

    def exercise(self, arguments):
        with patch('sys.argv', ['check-language-dx.py'] + arguments), \
                patch.object(DX.compat, 'fetch', return_value='artifact-classpath') as fetch, \
                patch.object(DX.compat, 'run', return_value='/tmp/jdk') as run, \
                patch.object(DX.compat, 'scala_command', return_value=['scala-cli']) as scala, \
                contextlib.redirect_stdout(io.StringIO()):
            DX.main()
            return fetch.call_args_list, run.call_args_list, scala.call_args_list

    def test_candidate_repositories_reach_resolution_and_scala_consumers(self):
        fetch, run, scala = self.exercise(['--version', '1.0.0-dx-SNAPSHOT', '--repository', 'file:///tmp/staged', '--native'])
        repositories = ['file:///tmp/staged', 'https://repo.maven.apache.org/maven2']
        self.assertEqual(fetch[0].args[1], repositories)
        self.assertEqual(len(scala), 3)
        for call in scala:
            self.assertEqual(call.args[2], '1.0.0-dx-SNAPSHOT')
            self.assertEqual(call.args[3], repositories)
        self.assertTrue(scala[-1].kwargs['native'])
        self.assertTrue(any('artifact-classpath' in str(call) for call in run))
        self.assertFalse(any('out/core' in str(call) for call in run))

    def test_published_uses_only_central(self):
        fetch, _, scala = self.exercise(['--version', '1.0.0', '--published'])
        for call in fetch:
            self.assertEqual(call.args[1], ['https://repo.maven.apache.org/maven2'])
        for call in scala:
            self.assertEqual(call.args[3], ['https://repo.maven.apache.org/maven2'])


if __name__ == '__main__':
    unittest.main()
