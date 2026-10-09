# Nexu Port Forwarding release pipeline

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

Application-source pushes to `main` or manual workflow dispatch prepare the version declared in `pom.xml` and `APP_VERSION`. For this cycle the application version is **1.2.2** and the release tag is **v1.2.2**.

A draft release stages the exact source commit. Windows 2022 x64 and Ubuntu 22.04 x64 independently compile and test, collect dependency inventory and legal notices, and build native packages. Checks validate version consistency, documentation links, retired endpoints in reachable history/application JAR, MIT inclusion, import tests, SSH forwarding and packaged GUI startup.

Only after both platform jobs succeed does the pipeline verify the packages, add a source ZIP without Git history, generate `SHA256SUMS.txt`, combine English/Italian release notes and publish **v1.2.2** as a **stable Latest** release. Published older releases are not overwritten.

Packages and diagnostics use GitHub Releases rather than Actions artifact storage. Failed attempts remain unpublished drafts. The workflow does not delete older releases, rewrite Git history, access PR resources, connect to real SSH servers or change repository visibility.
