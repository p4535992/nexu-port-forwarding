# Getting started and upgrades

[English](GETTING-STARTED.md) | [Italiano](GETTING-STARTED.it.md)

This guide covers the **v1.1.0-rc.1 prerelease**. The stable 1.0.0 release remains available and unchanged.

## Windows

In portable archives built from these sources, extract the entire folder and open `NexuPortForwarding.exe`: adjacent `data/` and `logs/` directories are created. The optional BAT respects the same preference. EXE/MSI installers use `%LOCALAPPDATA%\nexu-port-forwarding\data\` for profiles/settings and the sibling `logs\` directory for logs.

## Linux

In the portable TAR.GZ built from these sources, start `NexuPortForwarding/bin/NexuPortForwarding` or `start-portable.sh`: data lives in `NexuPortForwarding/data/` and logs in `NexuPortForwarding/logs/`, not under `bin/`. DEB/RPM packages use `${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/data/` and the sibling `logs/` directory.

## Choose a data location

Open **Impostazioni → Cartella dati…** (Settings → Data folder), choose **Portabile** or **Cartella utente**, and restart. No data is moved automatically. The dialog shows the active paths; external `NEXU_PF_HOME` / `-Dnexu.home` overrides take precedence. You may also edit `portable.properties`, setting `storage=portable` or `storage=appdata` with `version=1`.

## Main views and import

The main tab order is **Attivi → Custom → Tabby → MobaXterm**. **Attivi** shows only currently ACTIVE tunnels. Tabby and MobaXterm are import sources only: after pressing the corresponding import button, selected forwarding rules are copied into Nexu Port Forwarding's local `profiles.properties`; no live dependency on the external program remains and nothing is started automatically.

The filter bar combines free text, forwarding type (all / LOCAL / REMOTE / DYNAMIC), resolved SSH-server IP and state. `HOSTNAME` shows the configured SSH host and `INDIRIZZO IP` is resolved in the background. If the configured host is already an IP literal, the same value is shown immediately.

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

Preserve `data/`, `logs/` and `portable.properties` when updating the portable bundle. Profiles in the user-data directory remain separate and are never imported automatically. Use encrypted backups to transfer them. Existing logs under `data/logs/` remain untouched; new portable logs go to `logs/`.


### Grid editing and forwarding notation

The first column is **AZIONI**. Double-click **NOME** to edit it inline; Enter or leaving the field saves the profile immediately. **FORWARDING** replaces the former separate type/listener/destination columns:

- `R · SSH[listen] → PC → destination`: remote forwarding; the listener is on the SSH server.
- `L · PC[listen] → SSH → destination`: local forwarding; the listener is on this computer.
- `D · PC[listen] → SOCKS → SSH`: dynamic SOCKS forwarding.

Use the row menu to copy the command for **Windows PowerShell**, **Windows CMD**, or **Linux/POSIX** according to the shell you actually use.
