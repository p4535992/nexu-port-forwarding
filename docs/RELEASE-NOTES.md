# NexU Port Forwarding 0.2.1 release candidate

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

Desktop SSH local (-L) and remote (-R) TCP forwarding manager for Windows and Linux.

## Window behavior
This release explicitly uses the native decorated desktop window. On startup the window is sized and centered inside the current screen work area instead of assuming a fixed large desktop. The normal size and position and the maximized/restored state are saved locally in `window.properties`; invalid or off-screen geometry is clamped back to a visible monitor.

The native **minimize** and **maximize/restore** controls remain managed by Windows or the Linux window manager. Pressing **X** now asks whether to keep the application running minimized (notification area when available, otherwise normal minimization) or exit. If tunnels are active the dialog states that exiting will stop them while minimizing keeps them running.

## Packages
Windows x64: portable ZIP, EXE installer and MSI installer.
Linux x64: portable TAR.GZ, DEB and RPM.
Java 21 runtime is included in native packages. Java-only archives contain the platform-specific application JAR and its dependency directory.

## Local data and passwords
Version-independent local data directory, optional portable launcher, rotating diagnostic logs, encrypted local password/passphrase vault and encrypted backup import/export remain unchanged. Window state contains no credentials.

## Automated release gates
Both OS builds must compile and pass Maven tests, SSH loopback integration tests, packaging and a packaged JavaFX smoke test. The smoke test now additionally requires a visible, native `DECORATED`, non-full-screen window. Artifacts are published only after both platforms pass, with SHA-256 checksums.

## Limitations
Unsigned release candidate. Linux notification-area behavior still depends on the desktop environment; when a tray is unavailable the close dialog falls back to normal minimization. Validate real desktop/window-manager behavior and real SSH servers before production use.
