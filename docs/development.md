# Repository layout and development

[Documentation index](README.md)

## Repository structure

```text
core/                    shared core APIs, components, editing, terminal abstractions, tests
terminalJvm/             JVM Unix/stty backend
terminalNative/          Scala Native POSIX backend
markdown/                Markdown parser/renderer module
image/                   Image component module
extras/                  Optional reusable helper widgets
demo/                    non-interactive stream-render demo
asciinemaDemo/           deterministic asciinema recording scenarios
interactiveDemo/         shared interactive demo UI/logic
interactiveJvmDemo/      JVM interactive demo launcher
interactiveNativeDemo/   Native interactive demo launcher
fullscreenDemo/          shared fullscreen transcript example
fullscreenJvmDemo/       JVM fullscreen example launcher
fullscreenNativeDemo/    Native fullscreen example launcher
keyTester/               JVM terminal key tester
docs/                    usage notes, porting notes, smoke-test notes
scripts/                 generation and local recording scripts
openspec/                active and promoted OpenSpec change/spec artifacts
```

## Development

### Explicit BSP installation

```bash
mill --bsp-install
```

### IntelliJ IDEA XML Support

```bash
mill mill.idea/
```

### Project build and validation

Use the canonical [contributor validation checklist](../CONTRIBUTING.md#validation), including focused tests and local documentation checks.
