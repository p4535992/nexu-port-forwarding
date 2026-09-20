# Release pipeline

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

Every application-source push to main or manual workflow dispatch creates a uniquely numbered release candidate. Builds run on Windows 2022 and Ubuntu 22.04 x64.

A prepare job creates a **draft**, tied to the exact source commit. Each build runs Maven tests, native packaging and the packaged JavaFX UI/log smoke test. The smoke test also verifies that the primary window is native-decorated, non-full-screen and visible on a detected screen. Only successfully tested native packages are uploaded to that draft. Test reports and build logs are attached as diagnostics archives, including when a build fails.

The publish job runs only after BOTH OS builds succeed. It checks that all eight expected distributions exist, adds `SHA256SUMS.txt` and changes the draft to a prerelease with curated notes. Failed attempts remain drafts marked as not validated; they are not usable releases. Nothing automatically deletes old releases, artifacts or user data. Existing assets are not overwritten.

Release assets are transferred directly through GitHub Releases rather than actions/upload-artifact because the account previously reported exhausted Actions artifact storage. This avoids making delivery depend on that quota and does not require deleting another repository's artifacts. No account billing or quota settings are changed.

The default native app version is **0.2.1**; the distribution tag adds `rc.<workflow run number>`. The stable Windows upgrade UUID is declared in `scripts/package-windows.ps1`. Update `APP_VERSION`, `RELEASE_TAG`, `pom.xml`, launcher/UI version labels and release notes together for the next application version.

No generated release notes, pull-request triggers, automatic merging, cloud password sync, self-updater or real external SSH connections are involved. The GUI smoke test deliberately disables tray initialization in CI; real tray variants and installer upgrade behavior still require operator validation.
