"""Regression tests for the dependency-free documentation checks."""
import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("check_docs", Path(__file__).with_name("check-docs.py"))
DOCS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DOCS)


class DocumentationChecks(unittest.TestCase):
    def test_headings_duplicates_and_explicit_ids(self):
        text = '# Intro\n## API: `run()`\n## Intro\n<a id="custom"></a>\n'
        self.assertEqual(DOCS.anchors(text), {"intro", "api-run", "intro-1", "custom"})

    def test_fenced_examples_are_not_links_or_headings(self):
        text = '````md\n# Fake\n[bad](missing.md)\n```\n````\n# Real\n'
        self.assertEqual(DOCS.anchors(text), {"real"})
        self.assertEqual(DOCS.targets(text), [])

    def test_inline_reference_and_image_targets(self):
        text = '[guide](guide.md#intro)\n[ref]: <other.md>\n<img src="image.png">\n'
        self.assertEqual(DOCS.targets(text), ["guide.md#intro", "other.md", "image.png"])
        self.assertEqual(DOCS.targets('[guide](guide(v2).md) [escaped](guide\\(v2\\).md)'), ["guide(v2).md", "guide(v2).md"])
        self.assertEqual(DOCS.anchors('Intro\n=====\nDetails\n-------\n'), {"intro", "details"})

    def test_missing_file_and_anchor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            page = root / "README.md"
            page.write_text('# Intro\n[ok](#intro) [bad](#absent) [missing](no.md)\n')
            errors = DOCS.check_links(root, page)
            self.assertEqual(len(errors), 2)
            self.assertIn("missing anchor", errors[0])
            self.assertIn("missing target", errors[1])

    def test_external_urls_are_not_fetched(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            page = root / "README.md"
            page.write_text('[external](https://invalid.example/path#anchor)\n')
            self.assertEqual(DOCS.check_links(root, page), [])

    def test_release_versions_and_placeholders(self):
        self.assertEqual(DOCS.check_versions('io.github.zikolach::siglyph-core:0.11.0', '0.11.0'), [])
        for text in ('io.github.zikolach::siglyph-core::0.10.0', '"siglyph-core" % "0.10.0"', '<artifactId>siglyph-core_3</artifactId><version>0.10.0</version>', '<gist-id>', 'VERSION'):
            self.assertTrue(DOCS.check_versions(text, '0.11.0'), text)

    def test_canonical_source_and_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / "hello.scala").write_text('#!/usr/bin/env scala-cli\nprintln("hello")\n')
            block = '<!-- canonical-example: hello.scala -->\n```scala\nprintln("hello")\n```\n<!-- /canonical-example -->'
            self.assertEqual(DOCS.check_canonical(root, block), [])
            self.assertTrue(DOCS.check_canonical(root, block.replace('println("hello")', 'println("changed")')))
            self.assertTrue(DOCS.check_canonical(root, ''))


    def test_dynamic_versions_are_not_pinned_releases(self):
        for version in ("latest.release", "0.11.0-SNAPSHOT"):
            self.assertTrue(DOCS.check_versions(f"io.github.zikolach::siglyph-core:{version}", "0.11.0"))

    def fixture(self, root, malformed=False):
        (root / "docs").mkdir()
        (root / "docs/release-status.json").write_text('{"publishedVersion":"0.11.0","preparedVersion":"0.12.0"}')
        examples = root / "examples/scala-cli"
        examples.mkdir(parents=True)
        source = '//> using scala 3.9.0\n//> using dep io.github.zikolach::siglyph-core:0.11.0\nprintln("hello")\n'
        for name in ("hello.scala", "hello-native.scala"):
            (examples / name).write_text(source)
        (examples / "README.md").write_text('# Examples\n')
        (root / "CHANGELOG.md").write_text('## [Unreleased]\n## [0.11.0]\n')
        (root / "README.md").write_text('**0.11.0 · Scala**\n<!-- canonical-example: examples/scala-cli/hello.scala -->\n```scala\n' + source + '```\n<!-- /canonical-example -->\n')
        (root / "docs/getting-started.md").write_text('# Install\n')
        recipe = '<!-- compile-example -->\n'
        if not malformed:
            recipe += '```scala\nval answer = 42\n```\n'
        (root / "docs/recipe.md").write_text(recipe)

    def test_compile_command_checks_both_platforms_and_recipes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.fixture(root)
            with patch.object(DOCS, "ROOT", root), patch.object(sys, "argv", ["check-docs.py", "--compile"]), patch.object(DOCS.subprocess, "run") as run, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(DOCS.main(), 0)
                commands = [call.args[0] for call in run.call_args_list]
                self.assertEqual([Path(command[2]).name for command in commands], ["hello.scala", "hello-native.scala", "Recipes.scala"])
                for command in commands:
                    self.assertEqual(command[:2], ["scala-cli", "compile"])
                    self.assertEqual(command[-2:], ["--jvm", "21"])

    def test_offline_mode_does_not_spawn_compiler(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.fixture(root)
            with patch.object(DOCS, "ROOT", root), patch.object(sys, "argv", ["check-docs.py"]), patch.object(DOCS.subprocess, "run") as run, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(DOCS.main(), 0)
                run.assert_not_called()

    def test_prepared_changelog_does_not_change_published_installation_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.fixture(root)
            (root / "CHANGELOG.md").write_text('## [Unreleased]\n## [0.12.0] - 2026-11-16\n## [0.11.0]\n')
            with patch.object(DOCS, "ROOT", root), patch.object(sys, "argv", ["check-docs.py"]), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(DOCS.main(), 0)

    def test_malformed_recipe_fails_before_compiling(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.fixture(root, malformed=True)
            with patch.object(DOCS, "ROOT", root), patch.object(sys, "argv", ["check-docs.py", "--compile"]), patch.object(DOCS.subprocess, "run") as run, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(DOCS.main(), 1)
                run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
