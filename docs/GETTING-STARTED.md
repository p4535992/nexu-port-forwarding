# Getting started and upgrades

[English](GETTING-STARTED.md) | [Italiano](GETTING-STARTED.it.md)

Download [v1.0.0](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v1.0.0). This is a stable release with unsigned binaries for Windows x64 and Linux x64; native packages include Java.

## Windows

Download `nexu-port-forwarding-1.0.0-windows-x64.zip`, extract the entire directory and start `NexuPortForwarding.exe`. EXE and MSI installers are available in the same release. Normal launch stores data and logs under `%LOCALAPPDATA%\nexu-port-forwarding`, separately from the installation.

The application starts as a normal desktop window inside the visible screen area. Native **minimize**, **maximize/restore** and **X** controls are supplied by Windows. **X** asks whether to minimize to the notification area or exit; when tunnels are active it explains that exiting will stop them.

## Linux

Download `nexu-port-forwarding-1.0.0-linux-x64.tar.gz`, extract it and start `NexuPortForwarding/bin/NexuPortForwarding`. DEB and RPM packages are also available. Normal launch uses `${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding`.

The window uses native window-manager decorations. When a tray is available, **X** can minimize the application to the notification area. When a tray is not available, the same choice falls back to normal minimization.

## Window and monitor state

The application stores normal window size/position and maximized state in `window.properties`. These values are restored at startup. If the monitor, resolution or scaling changes and the previous position is no longer valid, the application moves the window back into a visible screen area.

The current GUI labels are Italian; the menu names below match the actual controls. The built-in example uses documentation-only endpoints: enter your own addresses before connecting.

## Create the first profile

Open **Password → Crea / sblocca archivio** (create / unlock vault) and choose a master password of at least 12 characters. It is not stored and cannot be recovered if lost.

Open **+ Nuovo tunnel** (new tunnel), then enter SSH server, port, username, authentication and forwarding parameters.

For **REMOTE (-R)**, the listening port is on the SSH server and the destination is reached from the local computer through the tunnel. For **LOCAL (-L)**, the sides are reversed.

Leave **Salva la credenziale nell’archivio locale cifrato** (save credential locally) enabled if you want to persist the SSH password or key passphrase locally.

Start the profile from its row. For a new host key, compare the displayed fingerprint with one obtained from the administrator through an independent channel before accepting it. Profiles do not start automatically when the application opens.

The **Profili** (profiles) menu can add the preconfigured Maven example. It is created disabled. Green indicates established SSH and forwarding, not a Maven health check.

## Logs and backups

**Apri log** (open logs) opens the local `logs/` directory. Files record application start/stop, tunnel UUIDs and tunnel states. Recent detailed events are also available in the selected tunnel panel.

**Password → Esporta backup cifrato** (export encrypted backup) writes saved profiles and stored credentials to a password-protected `.npfbackup`. The backup password may be different from the vault master password.

**Importa backup cifrato** (import encrypted backup) appends new copies of the profiles, does not overwrite existing profiles and does not start tunnels. Stop tunnels before importing.

Private-key files, host-key trust and logs are not part of the backup. When moving to another computer, copy private keys separately and verify host fingerprints again. **Profili → Esporta configurazione** (export configuration) remains a credential-free export.

## Future releases

With normal launch, replacing or upgrading application binaries keeps using the same data directory. Export/import is therefore not required for every upgrade. Profiles, encrypted vault, host keys, logs and window state stay separate from the installation. Keep a backup before upgrading anyway.

For self-contained portable storage, explicitly start `start-portable.bat` or `start-portable.sh`. Portable mode uses `data/`, including `data/logs/`, next to the launcher. Preserve the entire `data/` directory when moving to a newer portable release, or restore from an encrypted backup.
