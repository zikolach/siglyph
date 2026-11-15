#!/usr/bin/env python3
"""Offline public-doc checks; --compile also checks published Scala examples.

Checks root Markdown, docs/, and examples/scala-cli/README.md. Historical
versions remain valid outside the primary installation path. External URLs
are deliberately not fetched. Uses only the Python standard library.
"""
import argparse
import re
import subprocess
import tempfile
import unicodedata
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
CANONICAL = re.compile(
    r"<!-- canonical-example: ([^\n]+?) -->\s*```scala\n(.*?)\n```\s*<!-- /canonical-example -->",
    re.S,
)
RECIPE = re.compile(r"<!-- compile-example -->\s*```scala\n(.*?)\n```", re.S)


def prose(text):
    """Remove fenced code before looking for headings and links."""
    result = []
    fence = None
    for line in text.splitlines():
        match = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if fence:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                fence = None
        elif match:
            fence = match[1]
        else:
            result.append(line)
    return "\n".join(result)


def anchors(text):
    text = prose(text)
    result = set(re.findall(r'<(?:a|h[1-6])\b[^>]*\bid=["\']([^"\']+)', text))
    lines = text.splitlines()
    for index, line in enumerate(lines):
        match = re.match(r"^ {0,3}#{1,6}\s+(.+?)(?:\s+#+)?$", line)
        if match:
            title = match[1]
        elif index and re.fullmatch(r" {0,3}(?:=+|-+)\s*", line) and lines[index - 1].strip():
            title = lines[index - 1].strip()
        else:
            continue
        title = re.sub(r"<[^>]+>", "", title)
        title = re.sub(r"!?\[([^]]*)\]\([^)]+\)", r"\1", title).lower()
        slug = "".join(c for c in title if c.isalnum() or c in " _-" or unicodedata.category(c).startswith("M"))
        slug = slug.replace(" ", "-")
        candidate, suffix = slug, 0
        while candidate in result:
            suffix += 1
            candidate = f"{slug}-{suffix}"
        result.add(candidate)
    return result


def targets(text):
    text = re.sub(r"`+[^`\n]*`+", "", prose(text))
    inline = []
    for match in re.finditer(r"!?\[[^]\n]*\]\(\s*", text):
        start = match.end()
        if text[start:start + 1] == "<":
            end = text.find(">", start + 1)
            if end != -1:
                inline.append(text[start + 1:end])
            continue
        value, depth, escaped = [], 0, False
        for char in text[start:]:
            if escaped:
                value.append(char)
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == ")" and depth == 0 or char.isspace():
                break
            else:
                depth += (char == "(") - (char == ")")
                value.append(char)
        inline.append("".join(value))
    references = re.findall(r"^ {0,3}\[[^]\n]+\]:\s*(<[^>]+>|\S+)", text, re.M)
    images = re.findall(r'<img\b[^>]*\bsrc=["\']([^"\']+)', text)
    return [value.strip("<>") for value in inline + references + images]


def check_links(root, path):
    errors = []
    for target in targets(path.read_text()):
        url = urlsplit(target)
        if url.scheme or url.netloc:
            continue
        dest = (path.parent / unquote(url.path)).resolve() if url.path else path
        if not dest.is_relative_to(root):
            errors.append(f"{path.relative_to(root)}: target escapes repository: {target}")
        elif not dest.exists():
            errors.append(f"{path.relative_to(root)}: missing target: {target}")
        elif url.fragment and dest.suffix.lower() == ".md" and unquote(url.fragment) not in anchors(dest.read_text()):
            errors.append(f"{path.relative_to(root)}: missing anchor: {target}")
    return errors


def check_versions(text, version):
    versions = re.findall(r"io\.github\.zikolach:+siglyph-[\w.-]+:+([\w.+-]+)", text)
    versions += re.findall(r'"siglyph-[\w.-]+"\s*%\s*"([^"]+)"', text)
    versions += re.findall(r"<artifactId>siglyph-[^<]+</artifactId>\s*<version>([^<]+)</version>", text)
    errors = [f"installation version {v} differs from latest release {version}" for v in versions if v != version]
    if re.search(r"\bVERSION\b|<gist-id>", text):
        errors.append("unresolved installation placeholder")
    return errors


def check_canonical(root, text):
    errors = []
    matches = list(CANONICAL.finditer(text))
    if len(matches) != text.count("<!-- canonical-example:") or not matches:
        errors.append("missing or malformed canonical-example block")
    for match in matches:
        source = (root / match[1]).resolve()
        if not source.is_relative_to(root) or not source.is_file():
            errors.append(f"invalid canonical source: {match[1]}")
            continue
        expected = source.read_text()
        if expected.startswith("#!"):
            expected = expected.split("\n", 1)[1]
        if match[2].strip() != expected.strip():
            errors.append(f"canonical block differs from {match[1]}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compile", action="store_true", dest="compile_examples")
    args = parser.parse_args()
    docs = sorted(set(ROOT.glob("*.md")) | set((ROOT / "docs").rglob("*.md")) | {ROOT / "examples/scala-cli/README.md"})
    errors = [error for path in docs for error in check_links(ROOT, path)]
    version = re.search(r"^## \[(\d+\.\d+\.\d+)\]", (ROOT / "CHANGELOG.md").read_text(), re.M)[1]
    primary = [ROOT / p for p in ("README.md", "docs/getting-started.md", "examples/scala-cli/README.md")]
    primary += sorted((ROOT / "examples/scala-cli").glob("*.scala"))
    for path in primary:
        errors.extend(f"{path.relative_to(ROOT)}: {error}" for error in check_versions(path.read_text(), version))
    readme = (ROOT / "README.md").read_text()
    errors.extend(check_canonical(ROOT, readme))
    if f"**{version} ·" not in readme:
        errors.append("README release summary differs from latest release")
    recipes = []
    for path in docs:
        text = path.read_text()
        snippets = RECIPE.findall(text)
        if len(snippets) != len(re.findall(r"^<!-- compile-example -->$", text, re.M)):
            errors.append(f"{path.relative_to(ROOT)}: malformed compile-example block")
        recipes.extend(snippets)
    if errors:
        print("\n".join(errors))
        return 1
    print(f"Documentation checks passed: {len(docs)} Markdown files; release {version}; {len(recipes)} marked recipes.", flush=True)
    if args.compile_examples:
        for name in ("hello.scala", "hello-native.scala"):
            subprocess.run(["scala-cli", "compile", str(ROOT / "examples/scala-cli" / name), "--jvm", "21"], check=True)
        with tempfile.TemporaryDirectory(prefix="siglyph-docs-") as directory:
            source = Path(directory) / "Recipes.scala"
            directives = "\n".join(line for line in (ROOT / "examples/scala-cli/hello.scala").read_text().splitlines() if line.startswith("//>"))
            directives += f"\n//> using dep io.github.zikolach::siglyph-image:{version}\n"
            bodies = [f"object DocumentationRecipe{i}:\n" + "\n".join("  " + line for line in snippet.splitlines()) for i, snippet in enumerate(recipes)]
            source.write_text(directives + "\n" + "\n\n".join(bodies) + "\n")
            subprocess.run(["scala-cli", "compile", str(source), "--jvm", "21"], check=True)
        print("Published JVM/Native quickstarts and marked guide recipes compiled successfully.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
