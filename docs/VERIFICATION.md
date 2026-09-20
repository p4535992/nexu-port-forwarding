# Verification and release evidence

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

## Published and verified release

**Release:** [v0.2.1-rc.9](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v0.2.1-rc.9), published on 2026-09-20 at 07:56:25 UTC.

**Application source commit:** `0fae8a332f9f96f5b91a9c281a0b2ee8a330ad15`.

**Successful workflow:** [run 35498023726](https://github.com/p4535992/nexu-port-forwarding/actions/runs/35498023726).

Both platform jobs and the final publication job completed successfully. Release metadata was checked after publication: `draft=false`, `prerelease=true`, and the target commit matches the application source above. Later documentation-only commits do not change these binaries.

| Automated gate | Windows 2022 x64 | Ubuntu 22.04 x64 |
| --- | --- | --- |
| Compile application and tests with Java 21 | Passed | Passed |
| Core, storage, window-state, forwarding-policy and SSH integration tests | Passed | Passed |
| Native packaging | ZIP, EXE, MSI generated | TAR.GZ, DEB, RPM generated |
| Packaged JavaFX startup and clean exit | Passed | Passed under Xvfb |
| Native decorated, non-full-screen visible window smoke check | Passed | Passed |
| Persistent diagnostic log and readiness marker | Passed | Passed |
| Upload distribution and diagnostic archives | Passed | Passed |

The release contains eight application distributions: six native packages and two platform-specific Java archives containing the JAR plus `lib/`. Native packages include their runtime. Both diagnostic archives and `SHA256SUMS.txt` are attached. The final publication gate downloaded the staged assets and verified that every expected package existed before publishing.

## Window behavior verified in 0.2.1

The primary stage explicitly uses JavaFX `StageStyle.DECORATED`, is never started in full-screen mode and is placed inside a visible screen work area. Default dimensions adapt to the current monitor rather than assuming a fixed 1310×850 desktop.

Normal bounds and the maximized flag are stored in the local `window.properties`. Invalid or off-screen saved geometry is ignored or clamped to a visible monitor. The packaged smoke test refuses the build if the window is not decorated, is full-screen or is outside all detected screens.

The native minimize and maximize/restore controls are therefore supplied by Windows or the Linux window manager. The application intercepts only **X** to offer minimization versus application exit. With a tray, minimization hides to the notification area; without a tray it falls back to ordinary iconification.

## Test coverage and counting

Maven executes **12 JUnit methods**: two aggregate suites, six SSH integration methods, one forwarding-policy method and three window-state persistence methods. The core aggregate contains **62 named cases**. The storage aggregate contains **30 cases on POSIX**, or **28 when POSIX file permissions are unavailable**, as on the Windows runner.

Core/storage coverage includes profile validation and roundtrips, command quoting, host pins, cancellation/retry/independent connection lifecycle, encryption/decryption, wrong passwords, tampered archives, identity binding, master-password rotation, backup append/import, persistence across reopening and secret-free disk logging.

The window-state tests cover missing state, persistence roundtrip and rejection of corrupt/unsafe geometry.

The six Apache MINA integration tests exercise local and remote TCP data transfer, wrong-password rejection, host-key rejection before password authentication, changed-pin rejection and conflicting remote binds. Forwarding listener shutdown is verified with a bounded eventual-close check to avoid depending on nondeterministic TCP teardown timing. SSH and echo fixtures bind exclusively to `127.0.0.1`; they do not contact the user's SSH host or Maven service.

The forwarding-policy test checks that remote channels can reach only the configured host and port, denies arbitrary direct channels and extra listeners, and denies new connections after cancellation. The client combines this policy with disabled agent and X11 forwarding.

The packaged smoke test starts the actual JavaFX application, writes `UI_READY 0.2.1 DECORATED WINDOWED` and the local diagnostic log, then exits without opening SSH tunnels. Tray initialization is deliberately disabled in smoke-test mode. This verifies packaged GUI/runtime startup and window mode, not every interactive workflow.

## Reproduction

Full build, including integration tests:

```text
mvn --batch-mode --no-transfer-progress clean verify
```

Dependency-free core and storage verification:

```bash
bash scripts/test-core-offline.sh
```

## Still requires operator validation

The packages are unsigned release candidates. Actual EXE/MSI installation, upgrade and uninstall, DEB/RPM installation/removal on target distributions, individual Linux tray/window-manager variants, interactive close-dialog behavior, supported private-key formats against real servers, sleep/resume and network transitions require validation in the target environment. The automated smoke test is not a complete desktop end-to-end test or a security audit.

## Storage failure model and limits

Each individual configuration/vault write uses a temporary owner-restricted file, flush and atomic replacement where supported. Backup import validates first and allocates new IDs. It saves encrypted credentials before profiles: if the second write fails, previous profiles stay unchanged but encrypted orphan credentials may remain. This is not a cross-file transaction. Preserve backups before upgrades and use an owner-controlled local filesystem.

Profile input is bounded to 1,000 rows and a 2 MB configuration file. Encrypted envelopes are bounded to 8 MB. The vault does not protect a running, unlocked process from malware or an administrator. JavaFX/library-managed strings cannot be guaranteed to be immediately erased.
