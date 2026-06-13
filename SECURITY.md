# Security Policy

## Supported versions

siglyph is currently pre-1.0. Security fixes are expected to land on `main` and
in the latest published release only, unless otherwise announced in release
notes.

| Version | Supported |
| --- | --- |
| `0.x` latest | Yes |
| older `0.x` | No |

## Reporting a vulnerability

Please do **not** report security vulnerabilities in public issues.

Use GitHub's private vulnerability reporting / Security Advisory flow for this
repository when available. If that is not available, contact the maintainer
privately through GitHub and include enough detail to reproduce or assess the
issue.

Helpful information includes:

- affected version or commit;
- affected module (`core`, `terminalJvm`, `terminalNative`, `markdown`, `image`, etc.);
- reproduction steps or proof of concept;
- impact and any known mitigations.

We aim to acknowledge valid reports within 7 days and coordinate disclosure once
a fix is available.

## Scope

Examples of in-scope issues:

- terminal escape handling that can cause unintended terminal behavior;
- unsafe file or image handling in modules that process untrusted paths or bytes;
- denial-of-service behavior from malformed terminal input or rendered content;
- dependency or packaging issues that expose consumers to known vulnerabilities.

General application-level misuse of terminal UI APIs is usually out of scope
unless it is caused by unsafe library defaults.
