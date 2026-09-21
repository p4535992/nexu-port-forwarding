# Nexu Port Forwarding — Tabby import

[English](TABBY-IMPORT.md) | [Italiano](TABBY-IMPORT.it.md)

This feature is included in the **v1.1.0-rc.1 prerelease**. The stable 1.0.0 binaries remain unchanged. The graphical interface still uses Italian labels.

## Custom and Tabby tabs

**Custom** contains existing profiles and profiles created using **+ Nuovo tunnel**. Older configuration files are migrated to Custom, with an empty installation label.

**Tabby** contains forwarding profiles imported through **Importa Tabby…**. One SSH profile in Tabby can produce several independent rows: one per local, remote or dynamic forward. Imported rows are editable locally; edits are not written back to Tabby. Duplicating an imported row creates a Custom copy without copying credentials.

Search and **Avvia visibili** apply to the selected tab. Counters, **Ferma tutti** and the tray's start/stop-all commands apply to both tabs. Import never starts connections.

## Import workflow

Press **Importa Tabby…**, choose `config.yaml`, optionally enter an **Installazione** name and press **Analizza file**. Review the preview and skipped-profile report, select rows and press **Importa selezionati**.

Suggested locations are `%APPDATA%\tabby\config.yaml` on Windows and `${XDG_CONFIG_HOME:-$HOME/.config}/tabby/config.yaml` on Linux. `TABBY_CONFIG_DIRECTORY`, when defined with an absolute path, is suggested first. A custom or portable Tabby config can always be selected with **Sfoglia…**. No config file is read at application startup; file reading occurs only after the analysis button is pressed.

The file is read only. No copy of the full YAML is saved. Password fields, Tabby's encrypted vault, key contents, host-key trust and login scripts are **not imported**. The selected file is parsed in process memory; immediate erasure of library-managed strings cannot be guaranteed. Do not share a real config publicly for troubleshooting.

Before starting an imported row, enter the SSH credential in Nexu Port Forwarding and verify the server fingerprint independently. Store the credential in Nexu Port Forwarding's local encrypted vault as usual.

## Field mapping

| Tabby field | Nexu Port Forwarding field |
| --- | --- |
| `profiles[].name` and forwarding `description` | Row name |
| Group name, or the import dialog's override | Editable, searchable **Installazione** |
| `options.host`, `options.port`, `options.user` | SSH host, port and username |
| `options.forwardedPorts[].type` | `Local` → `LOCAL (-L)`; `Remote` → `REMOTE (-R)`; `Dynamic` → `DYNAMIC (-D)` |
| Forwarding `host`, `port` | Listening address and port |
| `targetAddress`, `targetPort` | Fixed destination for Local/Remote only |
| `options.privateKeys[0]` | First explicit local key path, not its contents |
| `keepaliveInterval`, `keepaliveCountMax`, `readyTimeout` | Converted and bounded keepalive/timeout settings; review the warnings |

Global `profileDefaults.ssh.options`, direct group `defaults.ssh.options` and profile `options` are applied in that order. An explicit list of forwarded ports replaces the inherited list; an empty list disables it. Parent-group defaults are not recursively inherited. Built-in profiles loaded externally by Tabby, SSH config includes and templates are not imported.

Null/automatic authentication is mapped to an explicit key file when one is configured, otherwise password authentication, and requires review. Agent and keyboard-interactive/MFA profiles are skipped with an explanation. Profiles requiring a jump host or connection proxy are also skipped: they are never silently converted into direct connections. Key-provider URLs, vault key references, UNC/network paths, relative key paths and unresolved variables are rejected. `~/`, `%h` and `%r` in explicit key paths are expanded. Multiple keys trigger a first-key-only warning.

## Dynamic SOCKS forwarding

Dynamic forwarding creates a **local SOCKS TCP proxy**, using the SSH backend's dynamic-forwarding API. There is no fixed target: the SOCKS client chooses the destination host/port, which the SSH server connects to. The exported OpenSSH command uses `-D` rather than `-L` or `-R`. SOCKS4 and SOCKS5 TCP tests are included in the integration suite; no UDP relay is implemented by this application.

The local SOCKS endpoint has no separate password authentication. Keep its bind address on `127.0.0.1` or `::1`. Non-loopback import rows require explicit selection, and connection startup retains the existing non-loopback exposure confirmation. The dynamic feature does not relax the policy rejecting unsolicited server-initiated channels.

## Installation label and repeated imports

The **INSTALLAZIONE** column is editable by double-clicking the cell while the row is stopped, or using **Modifica… → Connessione → Installazione**. It is searchable, sortable and saved with the profile. During import, a supplied label applies to all selected rows; a blank label uses the direct Tabby group name when available.

Each imported row has a local SHA-256 provenance key derived from the Tabby profile identity and SSH/forwarding settings. The same row is skipped on reimport even if the file moved, the installation label was edited, or the forwarding list was reordered. Existing credentials, labels and edited rows are not overwritten. Changed connection/forwarding settings create a **new** row; old rows are not deleted automatically. Without a Tabby profile ID, the source profile name participates in identity. This is import, not synchronization.

The preview flags possible listening-port conflicts. A tunnel already using the same port in Tabby or another program must be stopped, or the port changed, before starting the imported row. The two applications do not share SSH sessions.

## Storage and safety limits

Configuration format **2** adds origin, installation and import identity. Format 1 remains readable and maps to Custom. Encrypted backups preserve the new fields and DYNAMIC mode; backup restore continues to allocate fresh profile UUIDs. Configuration writes check both the 1,000-row cap and the 2 MB serialized-size cap before writing.

**Back up existing data before testing. Older binaries cannot read format 2.** Downgrading requires restoring a pre-upgrade configuration/backup, rather than pointing an old binary at the new files. No automatic file migration or deletion is performed outside the selected Nexu Port Forwarding data directory.

YAML input is capped at 4 MB, 40 nested levels and 100,000 converted value nodes. Imports are capped at 1,000 SSH profiles and 1,000 forwarding definitions. The reader uses SnakeYAML composition followed by plain-data conversion, not arbitrary Java object construction. Duplicate mapping keys, custom tags, aliases, merge keys and multiple documents are rejected. Errors do not expose YAML excerpts. Unsupported/invalid rows are reported; valid rows remain selectable.

## Verification status

The dependency-free conversion/storage tests run through `bash scripts/test-core-offline.sh`. `TabbyYamlReaderTest` tests the actual SnakeYAML reader. `MinaIntegrationTest` includes three new SOCKS tests against loopback-only SSH/echo fixtures, including client-chosen destinations and listener shutdown. These dependency-based tests must be executed with `mvn clean verify` before packaging or publishing. See the accompanying local verification report for what was actually run in the development environment. No real user Tabby configuration was supplied or opened during development.

## Format references

The importer was written independently against the public Tabby format, inspected on 20 September 2026. No Tabby application code is bundled.

- [SSH options and forwarding fields](https://github.com/Eugeny/tabby/blob/master/tabby-ssh/src/api/interfaces.ts)
- [Built-in SSH defaults](https://github.com/Eugeny/tabby/blob/master/tabby-ssh/src/profiles.ts)
- [Profile and direct-group defaults](https://github.com/Eugeny/tabby/blob/master/tabby-core/src/services/profiles.service.ts)
- [Configuration filename](https://github.com/Eugeny/tabby/blob/master/app/lib/config.ts)
- [Apache MINA dynamic forwarding API](https://github.com/apache/mina-sshd/blob/sshd-2.19.0/sshd-core/src/main/java/org/apache/sshd/common/forward/PortForwardingManager.java)
- [Synthetic YAML example](../src/test/resources/tabby/config.example.yaml)
