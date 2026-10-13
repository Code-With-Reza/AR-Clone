# GGWP-26.2

This repository contains the Eaglercraft 26.2 project sources and the build toolchain used to assemble the browser client. The project is built with Gradle for the Java code and a Node-based packaging script for the standalone HTML/WebAssembly output.

## Requirements

Before building, install the following:

- JDK 25 (Temurin 25 is recommended)
- Git
- Node.js 18+ or newer
- Python 3 for local static hosting (optional)
- At least 10 GiB of free space for the build and 16 GiB RAM recommended

The Gradle configuration in [build.gradle.kts](build.gradle.kts) sets the Java toolchain to Java 25, and the web packaging script also hard-caps the build memory and uses Java through the Gradle wrapper.

## Project layout

- [game/](game/) — patched Minecraft game code
- [platform/](platform/) — shared platform code
- [platform-teavm/](platform-teavm/) — browser platform implementation
- [teavm-compat/](teavm-compat/) — Java compatibility layer for TeaVM
- [target_teavm_wasm_gc/](target_teavm_wasm_gc/) — browser output target
- [target_teavm/](target_teavm/) — TeaVM web assets and generated files
- [target_lwjgl_desktop/](target_lwjgl_desktop/) — desktop runtime assets
- [wasm-toolchain/](wasm-toolchain/) — standalone HTML packaging and linking scripts
- [source/](source/) — extracted source inputs and generated project files
- [build.gradle.kts](build.gradle.kts) and [settings.gradle.kts](settings.gradle.kts) — Gradle project configuration

## Quick start

### 1. Install Java 25 and set environment variables

On Linux/macOS:

```bash
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\Path\To\JDK-25"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### 2. Compile the Java sources

This checks and compiles the Java code without producing the browser client package:

```bash
./gradlew :game:compileJava --console=plain --no-daemon
```

On Windows:

```powershell
./gradlew.bat :game:compileJava --console=plain --no-daemon
```

This is the simplest sanity check that your Java toolchain and project configuration are working.

### 3. Build the standalone HTML client

After the Java sources compile, build the standalone HTML bundle with:

```bash
node wasm-toolchain/build-single-html.js --output build/eaglercraft-26.2.html
```

Optional flags:

```bash
node wasm-toolchain/build-single-html.js --output build/eaglercraft-26.2.html --with-music
node wasm-toolchain/build-single-html.js --output build/eaglercraft-26.2.html --fast-package
```

- `--with-music` embeds the optional music payload instead of using the lean client layout.
- `--fast-package` uses faster compression for larger test builds.
- `--skip-build` re-packages an already built web output without rebuilding the Wasm artifacts.

The generated web output is placed under:

- [target_teavm_wasm_gc/build/web](target_teavm_wasm_gc/build/web)

The single-file HTML output is created at the path you pass to `--output`.

## Serve the build locally

If you want to test the generated website locally:

```bash
python3 -m http.server 8000 --directory target_teavm_wasm_gc/build/web
```

Then open:

```text
http://localhost:8000/
```

## Notes and build expectations

- The repo is configured for Java 25. Do not use Java 17/21 for the main build.
- The build can consume significant memory. If the build fails from memory pressure, increase headroom or keep the build budget high.
- The custom wrapper script sets a default build cap. You can override it if needed:

```bash
EAGLER_BUILD_CAP_MIB=12000 node wasm-toolchain/build-single-html.js --output build/eaglercraft-26.2.html
```

## Troubleshooting

### Gradle fails with a Java version error

Make sure your `JAVA_HOME` points at a JDK 25 installation:

```bash
java -version
```

The output should show Java 25.

### Build fails because of memory pressure

This project can use a large heap during web linking. Close other memory-heavy programs and run the build on a machine with at least 16 GiB of RAM. You can also lower concurrency or adjust the build cap with `EAGLER_BUILD_CAP_MIB`.

### You only want a fast compile check

Use the Gradle Java compile task only:

```bash
./gradlew :game:compileJava --console=plain --no-daemon
```

This is the quickest way to validate the code compiles without generating the final browser bundle.

## Further reading

If you need the deeper project notes, see [GUIDE.md](GUIDE.md). It contains the more detailed project workflow, output choices, and packaging notes for standalone HTML and related deployment scenarios.
