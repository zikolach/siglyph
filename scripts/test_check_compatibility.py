import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('compat', Path(__file__).with_name('check-compatibility.py'))
COMPAT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPAT)


class CompatibilityGateTests(unittest.TestCase):
    def test_removed_descriptor_and_static_changes_are_detected(self):
        before = COMPAT.surface('public class Fixture {\npublic int value();\ndescriptor: ()I\n}', 'Fixture')
        after = COMPAT.surface('public class Fixture {\npublic static long value();\ndescriptor: ()J\n}', 'Fixture')
        self.assertTrue(COMPAT.compare(before, after, ['*']))
        self.assertEqual(COMPAT.compare(before, before, ['*']), [])
        self.assertTrue(COMPAT.compare(before, before, ['missing']))

    def test_new_abstract_obligations_are_detected(self):
        old = COMPAT.surface('public interface Fixture {\n}', 'Fixture')
        new = COMPAT.surface('public interface Fixture {\npublic abstract void added();\ndescriptor: ()V\n}', 'Fixture')
        self.assertTrue(COMPAT.compare(old, new, ['*']))
        abstract_old = COMPAT.surface('public abstract class Fixture {\n}', 'Fixture')
        abstract_new = COMPAT.surface('public abstract class Fixture {\npublic abstract void added();\ndescriptor: ()V\n}', 'Fixture')
        self.assertTrue(COMPAT.compare(abstract_old, abstract_new, ['*']))
        protected_old = COMPAT.surface('public class Fixture {\nprotected void hook();\ndescriptor: ()V\n}', 'Fixture')
        protected_new = COMPAT.surface('public class Fixture {\n}', 'Fixture')
        self.assertTrue(COMPAT.compare(protected_old, protected_new, ['*']))

    def test_repository_selection_is_explicit_in_consumer_commands(self):
        command = COMPAT.scala_command('run', Path('Consumer.scala'), '0.11.0', ['https://repo.maven.apache.org/maven2'])
        self.assertEqual(command[:2], ['env', 'COURSIER_REPOSITORIES=https://repo.maven.apache.org/maven2'])
        self.assertNotIn('file:', command[1])

    def test_inventory_reports_new_and_removed_source_files(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'compatibility').mkdir()
            (root / 'core/src').mkdir(parents=True)
            (root / 'compatibility/api-inventory.json').write_text(json.dumps({'files': [{'path': 'core/src/Old.scala'}]}))
            (root / 'core/src/New.scala').write_text('object New')
            errors = COMPAT.inventory_errors(root)
            self.assertEqual(len(errors), 2)
            self.assertTrue(any('unclassified' in error for error in errors))
            self.assertTrue(any('removed' in error for error in errors))

    def test_disposable_binary_fixture_really_fails_after_method_removal(self):
        with tempfile.TemporaryDirectory(prefix='siglyph-negative-') as temporary:
            root = Path(temporary)
            old = root / 'old'
            new = root / 'new'
            old.mkdir()
            new.mkdir()
            (old / 'Fixture.java').write_text('public class Fixture { public int value() { return 1; } }')
            (old / 'Client.java').write_text('public class Client { public static void main(String[] a) { new Fixture().value(); } }')
            (new / 'Fixture.java').write_text('public class Fixture {}')
            subprocess.run(['javac', '-d', str(old), str(old / 'Fixture.java'), str(old / 'Client.java')], check=True)
            subprocess.run(['javac', '-d', str(new), str(new / 'Fixture.java')], check=True)
            old_api = subprocess.check_output(['javap', '-protected', '-s', '-classpath', str(old), 'Fixture'], text=True)
            new_api = subprocess.check_output(['javap', '-protected', '-s', '-classpath', str(new), 'Fixture'], text=True)
            self.assertTrue(COMPAT.compare(COMPAT.surface(old_api, 'Fixture'), COMPAT.surface(new_api, 'Fixture'), ['*']))
            import os
            failed = subprocess.run(['java', '-cp', str(new) + os.pathsep + str(old), 'Client'], capture_output=True, text=True)
            self.assertNotEqual(failed.returncode, 0)
            self.assertIn('NoSuchMethodError', failed.stderr)


if __name__ == '__main__':
    unittest.main()
