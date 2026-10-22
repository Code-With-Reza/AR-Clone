# AR-Clone

## Build and publish the WebXR web files

The **Web files release** workflow can be started manually from the repository's
GitHub Actions page. It builds and validates the multi-file web ZIP, then
publishes it to a GitHub Release and as a workflow artifact.

The workflow requires a runner registered with the labels `self-hosted`,
`Windows`, and `X64`, Git for Windows (including Bash), and the JDK 25 installed
at `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`. Allow at least
16 GB of RAM and 10 GiB of free disk space. Update `JAVA_HOME` in
`.github/workflows/web-files-release.yml` if JDK 25 is installed elsewhere; the
workflow uses that JDK directly and does not install a second one. The workflow
allows up to six hours for the self-hosted build. The memory-heavy TeaVM link
stages run sequentially; increasing Gradle workers is unlikely to accelerate
those stages and can exceed the runner's available memory.
Successful runs cache standalone linker outputs by source commit. The
linker's content receipts revalidate inputs and outputs before reuse, so a
repeat build of unchanged source can skip the long Wasm link stages; the first
build after a source change still performs the full links.

The default `source_repository` is `Code-With-Reza/AR-Clone`, and the default
`source_ref` is `webar`. Push the WebXR-enabled game project from the
`GGWP-26.2` directory as a branch in `AR-Clone`, then use that branch in
`source_ref`. You can change `source_repository` if the source branch is hosted
in another repository.
