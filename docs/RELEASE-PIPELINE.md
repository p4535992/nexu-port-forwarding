# Nexu Port Forwarding release pipeline

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

Application-source pushes to `main` or a manual workflow dispatch prepare the version declared in `pom.xml` and `APP_VERSION`. Version 1.0.0 is published as **v1.0.0**, not as a release candidate.

A per-run draft stages the exact source commit. Windows 2022 x64 and Ubuntu 22.04 x64 compile and test independently, collect the runtime JAR inventory and embedded legal notices, and build their native packages. Checks validate the release version, documentation links, full project name, retired endpoints in reachable history and the application JAR, and MIT inclusion. The real packaged JavaFX application is started and stopped, with window-mode and local-log checks.

Only when both platform jobs succeed does publication verify every expected package, add a source ZIP without Git history, compute `SHA256SUMS.txt`, combine release notes with **English first, then Italian**, and publish the draft under the stable version tag as Latest. The release notes record the source commit and workflow run. Existing published versions are never overwritten; bump the version before publishing another release.

Packages and diagnostics use GitHub Releases, not Actions artifact storage. Failed attempts remain unpublished drafts. This workflow does not delete old releases, rewrite Git history, access PR resources, run against real SSH servers or change repository visibility. Diagnostic logs contain synthetic test data; publication still requires checking their contents.

For the next version update `pom.xml`, `APP_VERSION`, `RELEASE_TAG`, launcher/UI/log version strings, script defaults and bilingual documentation together. Preserve the Windows upgrade UUID and the version-independent data directory. Stable status does not mean code signing: the binaries are unsigned.
