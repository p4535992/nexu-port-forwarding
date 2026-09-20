# Nexu Port Forwarding

[English](README.md) | [Italiano](README.it.md)

Desktop SSH TCP tunnel manager for **Windows and Linux**, built with Java 21, JavaFX and Dorkbox SystemTray integration. Nexu Port Forwarding is an independent tunnel manager; it does not include digital-signature services or a local web server.

English is the primary documentation language; Italian translations are maintained in parallel. The current graphical interface uses Italian labels; the [getting-started guide](docs/GETTING-STARTED.md) lists the exact menu names.

## Features

- Searchable, sortable and scrollable tunnel table.
- SSH profiles with hostname/IP, custom SSH port, username, password or private-key authentication.
- **REMOTE (-R)** and **LOCAL (-L)** TCP forwarding.
- Independent start/stop lifecycle for each row.
- Duplicate, edit, delete, per-tunnel activity log and OpenSSH command export.
- Explicit SSH host-key verification.
- Keepalive and optional bounded reconnects.
- Native Windows/Linux window decorations with minimize, maximize/restore and close controls.
- Local encrypted credential vault and encrypted backup import/export.

Green means the SSH connection and forwarding listener were established; it does **not** mean the destination application is healthy.

Each profile owns its own SSH connection. No tunnel starts automatically when the application opens. Authentication, host-key and bind failures are not automatically retried. Apache MINA SSHD is used directly, so passwords are not passed through BAT files, PowerShell commands or external-process arguments.

## Release 1.0.0

Download **[v1.0.0](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v1.0.0)**.

| Platform | Packages |
| --- | --- |
| Windows x64 | Portable ZIP, EXE installer, MSI installer |
| Linux x64 | Portable TAR.GZ, DEB, RPM |
| Java | Platform archive containing the JAR and its `lib/` directory |

Native packages include a Java 21 runtime. Portable packages must be extracted as a complete directory.

Start:
- Windows: `NexuPortForwarding.exe`
- Linux: `bin/NexuPortForwarding`

The release is published as a stable version, but the binaries are not code-signed. Windows SmartScreen or Linux package tooling may therefore show an unknown-publisher warning.

## Window behavior

The main window uses the native desktop decoration supplied by Windows or the Linux window manager.

- **—** minimizes normally.
- **maximize/restore** is handled by the operating system.
- **X** asks whether to minimize the application or exit.
- With a system tray, minimizing from the close dialog hides the application to the notification area.
- Without a tray, the same choice falls back to normal minimization.
- If tunnels are active, the close dialog clearly states that exiting will stop them.

Normal position, size and maximized state are stored in `window.properties`. If the display, scaling or monitor layout changes and the stored geometry is no longer visible, the application brings the window back inside an available screen.

## Local passwords and passphrases

The **Password** menu can create/unlock the local vault, lock it, change the master password and import/export encrypted backups.

Saved SSH passwords and private-key passphrases are stored locally in:

`credentials.npfvault`

The vault uses AES-256-GCM and PBKDF2-HMAC-SHA256 with 600,000 iterations, random salt and a fresh nonce. The master password must contain at least 12 characters and is **never stored**. If it is lost, it cannot be recovered.

Credentials are bound to the profile ID and SSH identity. Changing server, port, username or authentication method does not silently reuse a previously saved credential. Duplicating a profile does not implicitly duplicate its secret.

Encryption at rest does not protect an already unlocked process from malware or an administrator with process access.

## Local data, logs and upgrades

Normal launch uses a version-independent application-data directory:

```text
Windows: %LOCALAPPDATA%\nexu-port-forwarding\
Linux:   ${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/

profiles.properties      Tunnel profiles, without passwords
credentials.npfvault     Encrypted passwords/passphrases
host-keys.properties     Accepted SSH host-key fingerprints
window.properties        Window position, size and maximized state
logs/nexu-0.log          Current rotating diagnostic log
app.lock                 Single-instance data-directory lock
```

Logs rotate across five files of about 2 MB each. They record application lifecycle plus tunnel UUID/state, not passwords, private keys, traffic payloads or arbitrary SSH exception text.

Because data is stored separately from the installation, normal application upgrades reuse the same profiles, vault, host keys, logs and window state.

Portable launchers `start-portable.bat` and `start-portable.sh` instead use a `data/` directory next to the launcher. Preserve that entire directory when replacing a portable release.

`NEXU_PF_HOME` or `-Dnexu.home` can override the data directory.

## Backup and migration

**Profiles → Export configuration** creates a credential-free `.properties` export.

**Password → Export encrypted backup** creates a password-protected `.npfbackup` containing profiles and credentials that were explicitly saved in the local vault.

Encrypted import appends copies with new IDs; it does not overwrite existing profiles and does not start tunnels.

Backups deliberately exclude private-key files, host-key trust and logs. When moving to another computer, copy private keys separately, update paths and verify SSH host fingerprints again.

## Remote-forward example

The built-in example uses documentation-only endpoints. Replace them with your own server and destination before connecting:

```powershell
ssh.exe -N -T -p 22 -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -R 127.0.0.1:8989:maven.example.com:8081 utente@203.0.113.10
```

For `-R`, `127.0.0.1:8989` listens on the SSH server and the destination `maven.example.com:8081` is reached from the local computer through the tunnel. For `-L`, the sides are reversed.

The effective remote bind also depends on SSH-server policy such as `GatewayPorts`.

## Development

Requirements:
- JDK 21
- Maven 3.9+

```text
mvn --batch-mode clean verify
java -jar target/app/nexu-port-forwarding.jar
```

Helpers are included as `build.bat`, `run.bat`, `build.sh` and `run.sh`.

Packaging also requires Python 3.10+ for the offline documentation and inventory step:

```bash
python scripts/prepare-distribution.py
```

Then package on the target operating system:

```powershell
./scripts/package-windows.ps1 -Version 1.0.0
```

```bash
bash scripts/package-linux.sh 1.0.0
```

Windows packaging requires WiX 3.x. Linux packaging requires the DEB/RPM and desktop libraries installed by the release workflow.

## Verification and documentation

Release checks also scan reachable Git history and the application JAR for the two retired endpoints, without storing those values in the rules. This is a targeted check, not a complete security audit. The exact runtime JAR inventory is included as `dependency-inventory.json`.

The release workflow builds and tests both operating systems, creates native packages and smoke-tests the packaged JavaFX application. The release is published only after both platform jobs succeed.

Documentation:

- [Getting started](docs/GETTING-STARTED.md)
- [Verification evidence](docs/VERIFICATION.md)
- [Release pipeline](docs/RELEASE-PIPELINE.md)
- [Release notes](docs/RELEASE-NOTES.md)
- [Local data and logs](docs/DATA-AND-LOGS.txt)
- [Documentation index](docs/README.md)

Not implemented: SSH agent, ProxyJump, MFA/keyboard-interactive authentication, automatic tunnel startup, automatic application updates or cloud synchronization.

## License

Nexu Port Forwarding application code and documentation are licensed under the [MIT License](LICENSE). Dependencies and the bundled Java runtime retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Native packages include the MIT license and third-party notices.
