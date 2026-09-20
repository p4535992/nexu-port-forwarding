# Nexu Port Forwarding 1.0.0

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

First stable release of Nexu Port Forwarding from the recreated public repository.

### Included

SSH local (`-L`) and remote (`-R`) TCP forwarding; independent tunnel profiles; searchable and sortable grid; start/stop controls; status indicators; password or private-key authentication; explicit host-key verification; keepalive and optional bounded reconnects.

Native decorated Windows/Linux window with minimize and maximize/restore controls. The close dialog offers minimization, exit or cancellation and warns that exit stops active tunnels. Without a tray, minimization remains a normal desktop operation. Normal window bounds and maximized state are saved locally.

Local AES-256-GCM credential vault protected by a master password, encrypted backup import/export, separate credential-free profile exports, rotating local logs and a version-independent data directory. There is no cloud synchronization and no automatic tunnel startup.

### Downloads

Windows x64: portable ZIP, EXE installer and MSI installer. Linux x64: portable TAR.GZ, DEB and RPM. Native packages include Java 21. Java-only archives include the application JAR and its `lib/` directory and require Java 21. Extract portable archives completely.

Source ZIP, SHA-256 checksums, platform diagnostics and the resolved runtime JAR inventory are attached. MIT covers the application code; dependencies and Java retain their own licenses and notices.

### Verification and limitations

Publication requires both platform builds, all Maven tests, SSH loopback integration tests, packaged GUI startup/exit, local log checks, retired-endpoint checks and package completeness checks to pass. The documentation now uses the full name **Nexu Port Forwarding**, with English primary and Italian in parallel.

Binaries are not code-signed. The UI currently uses Italian labels. Tray availability depends on the Linux desktop. Installer upgrade/uninstall, real server policies, sleep/resume and every interactive workflow are not exhaustively tested. Green indicates an established tunnel, not destination-service health. A lost vault/backup password cannot be recovered. This release is not a security audit.
