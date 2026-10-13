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

## GitHub release workflow

This repo includes a GitHub Actions workflow at [.github/workflows/release.yml](.github/workflows/release.yml) that automatically builds the project and uploads the compiled ZIP targets to a GitHub Release.

You can trigger it in two ways:

1. Push a tag like `v26.2.0` or `release-26.2.0`.
2. Run the workflow manually from the GitHub Actions tab with the `tag_name` input.

The workflow does the following:

- installs Java 25
- installs Node.js
- runs the Gradle compile step
- builds the desktop asset EPK
- builds both standalone HTML variants: lean and music-enabled
- packages the outputs into multiple ZIPs
- uploads them to a GitHub Release

### Audio sources and file layout

The sound and music EPKs are checked-in build inputs in the `game/` directory. The standalone build stages them into the web output before assembling the browser client:

- [game/sounds.epk](game/sounds.epk) — source EPK for sound effects; copied to [target_teavm/build/web/sounds.epk](target_teavm/build/web/sounds.epk), then deployed to the Wasm-GC web output and included in both standalone HTML variants
- [game/music.epk](game/music.epk) — source EPK for background music and music discs; copied to [target_teavm/build/web/music.epk](target_teavm/build/web/music.epk)
- [target_teavm/build/web/assets.epk](target_teavm/build/web/assets.epk) — generated general asset archive, built from [game/src/main/resources](game/src/main/resources) by the `:target_lwjgl_desktop:buildAssetsEPK` task in [target_lwjgl_desktop/build.gradle.kts](target_lwjgl_desktop/build.gradle.kts)

During a standalone build, `wasm-toolchain/build-single-html.js` stages the two tracked audio archives from `game/`. `wasm-toolchain/deploy_wasm_web.sh` then installs `sounds.epk` into the Wasm-GC web output. The lean HTML embeds sound effects and creates a separate optional music resource-pack ZIP; the `--with-music` variant embeds both sound effects and music in the HTML.

## Manual release workflow

If you want a reproducible way to build the project and package the outputs for a GitHub release or other artifact storage, use the helper script at [release.sh](release.sh):

```bash
chmod +x ./release.sh
./release.sh
```

This script does the following:

1. Compiles the Java sources with Gradle.
2. Builds the desktop asset EPK pack under [target_lwjgl_desktop/](target_lwjgl_desktop/).
3. Produces the standalone HTML client and optional music ZIP.
4. Creates multiple release ZIPs under a local `releases/` directory.

The generated release targets are:

- `eaglercraft-26.2-web-html-<timestamp>.zip` — single-file HTML build
- `eaglercraft-26.2-web-files-<timestamp>.zip` — raw web output from [target_teavm_wasm_gc/build/web](target_teavm_wasm_gc/build/web)
- `eaglercraft-26.2-desktop-assets-<timestamp>.zip` — packaged asset archive

You can also choose a custom output directory:

```bash
./release.sh --output-dir ./artifacts
```

To reuse existing build outputs without recompiling:

```bash
./release.sh --skip-compile --output-dir ./artifacts
```

This is the easiest manual release path when you want separate ZIP artifacts instead of a single bundled download.

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
