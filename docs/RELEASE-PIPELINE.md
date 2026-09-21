# Nexu Port Forwarding release pipeline

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

Application-source pushes to `main` or a manual workflow dispatch prepare the version declared in `pom.xml` and `APP_VERSION`. For this development cycle the packaged application version is **1.1.0** and the release tag is **v1.1.0-rc.1**.

A per-run draft stages the exact source commit. Windows 2022 x64 and Ubuntu 22.04 x64 compile and test independently, collect the runtime JAR inventory and embedded legal notices, and build their native packages. Checks validate version consistency, documentation links, full project name, retired endpoints in reachable history/application JAR, MIT inclusion, import tests, dynamic forwarding and packaged GUI startup.

Only when both platform jobs succeed does publication verify every expected package, add a source ZIP without Git history, compute `SHA256SUMS.txt`, combine release notes with English first and Italian second, and publish the draft as **v1.1.0-rc.1**, `prerelease=true` and **not Latest**. The stable `v1.0.0` release is not modified. Existing published versions are never overwritten.

Packages and diagnostics use GitHub Releases rather than Actions artifact storage. Failed attempts remain unpublished drafts. The workflow does not delete old releases, rewrite Git history, access PR resources, connect to real SSH servers or change repository visibility.
