# Nexu Port Forwarding verification

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

## Recreated repository baseline

The public repository was checked on 2026-09-20. Its initial `main` commit was `7dfd1e28368b534847f1673d5c1f89c39e3c3496`, with no parents. The only branch and the initial tag pointed to that same commit. Its complete source tree, `349a7bc1ab28174a914104aadae7a5511e0a16ab`, contained 62 files and matched the locally reconstructed snapshot byte for byte, including file modes.

Neither of the two retired infrastructure values was found in those files, including UTF-8 and UTF-16 encodings. No matches were found for the common private-key/access-token patterns checked. Synthetic test passwords are not production credentials. This is a targeted check, not a guarantee that no other sensitive data exists.

The baseline passed the 62-case core and 30-case POSIX storage offline suites. These results are not a substitute for building the final release source on both target systems.

## Release 1.0.0 gates

The [release workflow](../.github/workflows/release.yml) publishes `v1.0.0` only when Windows 2022 x64 and Ubuntu 22.04 x64 both pass compilation, Maven tests, package creation, packaged JavaFX startup/exit and persistent log checks. It also checks version consistency, local documentation links, full project naming, MIT inclusion, the application JAR and reachable Git history for retired endpoints. The endpoint rules store hashes, not the retired values.

The published release notes record the exact source commit and workflow run. Platform diagnostic ZIPs include Maven reports and release-check results. No test result from the deleted repository is claimed as evidence for the new release.

Maven runs 12 JUnit methods: two aggregate suites (62 core cases and 30 POSIX storage cases, or 28 storage cases on Windows), six SSH integration methods, one forwarding-policy method and three window-state persistence methods. The six integration tests use loopback-only SSH/echo fixtures for local/remote transfer, password rejection, host-key rejection, changed host pins and bind conflicts. The packaged smoke test disables tray initialization and opens no SSH tunnel.

## Reproduction

```bash
mvn --batch-mode --no-transfer-progress clean verify
python scripts/prepare-distribution.py
python scripts/verify-release.py --history --app target/app
```

Offline core/storage tests: `bash scripts/test-core-offline.sh`. The release checker requires Python 3.10+ and Git; `--history` inspects HEAD, branch and tag history, not PR refs. It is not a general-purpose secret scanner.

## Limits

Binaries are unsigned. Actual installer upgrade/uninstall, Linux tray/window-manager variants, full interactive workflows, real SSH server policies, private-key formats, sleep/resume and network transitions still require validation in the target environment. These checks are not an exhaustive security or license audit.

Individual configuration/vault writes use owner-restricted temporary files and atomic replacement where supported. Backup import is not a multi-file transaction: credentials are saved before profiles, so a second-write failure can leave orphan encrypted credentials without replacing previous profiles. Keep backups. Configuration files are capped at 2 MB and encrypted envelopes at 8 MB. Encryption does not protect an unlocked process from malware or administrators; immediate erasure of library-managed strings is not guaranteed.

Repository checks do not establish that other people's old clones, downloaded binaries or service-side caches have been erased. Do not copy the old `.git` directory or old binaries into this repository.
