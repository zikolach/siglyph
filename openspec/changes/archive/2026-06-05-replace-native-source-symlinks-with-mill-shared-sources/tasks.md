## 1. Inspect Current Layout

- [x] 1.1 Verify `coreNative/src` and `interactiveDemoNative/src` are symlink mirrors
- [x] 1.2 Search repository docs, scripts, and build files for references to Native source symlink mirrors
- [x] 1.3 Identify Mill source-root override needed for `coreNative` and `interactiveDemoNative`

## 2. Build Configuration Cleanup

- [x] 2.1 Update `build.mill` so `coreNative` compiles canonical `core/src` sources without `coreNative/src`
- [x] 2.2 Update `build.mill` so `interactiveDemoNative` compiles canonical `interactiveDemo/src` sources without `interactiveDemoNative/src`
- [x] 2.3 Remove the `coreNative/src` symlink from the repository
- [x] 2.4 Remove the `interactiveDemoNative/src` symlink from the repository
- [x] 2.5 Confirm no Native shared-source module depends on the removed symlink paths

## 3. Documentation Updates

- [x] 3.1 Update `AGENTS.md` to describe Mill-configured shared source roots instead of symlink mirrors
- [x] 3.2 Update README or docs if they mention Native source symlink mirrors
- [x] 3.3 Document that `core/src` and `interactiveDemo/src` are canonical shared source trees

## 4. Validation

- [x] 4.1 Run `mill __.compile`
- [x] 4.2 Run `mill core.test`
- [x] 4.3 Run `mill interactiveNativeDemo.nativeLink`
- [x] 4.4 Run `mill scalafmtCheck`
- [x] 4.5 Run `mill scalafixCheck`
- [x] 4.6 Run `mill quality`
- [x] 4.7 Run `openspec validate --all --strict`
