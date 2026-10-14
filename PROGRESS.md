# AR Injection Progress

## Finished

- Added a dedicated GitHub Actions workflow to build and publish only the multi-file web ZIP on a large runner; it includes `music.epk` and compressed variants.
- Added an AR start/exit control and a persisted settings panel in a WebXR DOM overlay.
- Added immersive-session lifecycle, XR frame pacing, per-eye camera pose/projection, and per-eye framebuffer presentation.
- Added floor hit testing, horizontal-surface filtering, a green ground reticle, and plane placement gated by ground detection.
- Added anchor-relative X/Y/Z buttons, full-turn yaw, 0.01-1.0 scale, and sun angle/intensity controls.
- Added tracked-world and passthrough modes. Passthrough leaves only blocks/entities and the AR DOM controls visible; sky, game UI, hand/effect overlays, clouds, weather, borders, particles, and selection outlines are suppressed.
- Mapped the real floor anchor to Minecraft X/Z zero and the current player's Y elevation.
- Verified `:game:compileJava` and `:platform-teavm:compileJava` succeed.

## Ongoing

- Build, deployment, and device verification remain. The root-level served files still contain the previous Wasm image until a precise target link succeeds.
- The web-files workflow has passed YAML parsing but has not yet run on a GitHub larger runner.

## Remaining

- Verify immersive AR, DOM overlay, hit testing, camera transparency, and session exit on Android Chrome with an AR-capable phone.
- Rebuild all required Wasm-GC client/worker outputs, assemble `target_teavm_wasm_gc/build/web`, and copy the completed web files to the workspace root.

## Build Blocker

- `:platform-teavm:compileJava` passes.
- The normal and incremental precise Wasm-GC linkers both failed in TeaVM dependency analysis under this container's available memory. A 1.8 GiB incremental attempt reports `OutOfMemoryError: Java heap space`; larger heaps caused the Gradle daemon to disappear. Fast-link mode is not a release option and also reports unresolved Netty kqueue/epoll classes.
- No complete new Wasm image was produced, and no root-level web files were replaced or deployed.

## Compatibility Notes

- WebXR requires a secure context (HTTPS or localhost), a compatible browser/device, and user activation to request an immersive session.
- Plane detection and hit-test support vary by browser/runtime; unsupported features must be reported rather than silently treated as available.
- `immersive-ar` supplies camera passthrough through the XR compositor. The game must render into the XR projection layer for virtual content to appear anchored in the room.

## Suggested Follow-Up

- Add a recenter/reset-anchor action and persist a small set of named anchors; both help recover from tracking drift without changing the requested axis controls.
- Keep an obvious one-tap exit and a non-AR fallback, since browser tracking can be interrupted by permission changes, thermal limits, or device sleep.