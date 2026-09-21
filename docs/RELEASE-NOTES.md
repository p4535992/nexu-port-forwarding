# Nexu Port Forwarding 1.2.0

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Stable 1.2.0 release with configurable SOCKS5/HTTP CONNECT proxy transport, OpenSSH comparison diagnostics, adaptive table layout, clearer tunnel-state coloring, and persistent English/Italian UI language selection with English as the default.

### Highlights in 1.2.0

- Added **Settings → Language → English / Italiano** with **English as the default** when no preference exists.
- The selected language is persisted in the local data directory and is applied on the next application start.
- Main window controls, filters, table labels, tunnel state labels, profile/proxy dialogs, vault/password dialogs, data-folder settings and tray actions now follow the selected language.
- Language switching is intentionally restart-based so the application does not rebuild the UI while SSH tunnels may be active.

- **ACTIVE** tunnel rows now use a true green background, including when selected; stopped rows remain red and the contextual **■ Ferma** action remains red.
- Removed the redundant **STATO** table column; the status filter remains available, while row color plus **Avvia/Ferma** communicate state directly.
- Fixed wrapped **NOME** / **INSTALLAZIONE** cells so the table row recalculates its preferred height instead of clipping multiline text at the top or bottom.
- **FORWARDING** and **HOSTNAME / INDIRIZZO IP** now wrap with dynamic row height and more adaptive column widths, reducing horizontal pressure on smaller displays.

- Every **Avvia** action now writes copyable OpenSSH-equivalent commands into the tunnel activity panel for **Windows PowerShell** and **Linux/POSIX**.
- The diagnostic commands explicitly use **`-F NUL`** on Windows and **`-F /dev/null`** on Linux so they do not read the user's OpenSSH config, matching Nexu's self-contained SSH behavior more closely.
- The log explicitly states that Nexu uses **Apache MINA SSHD** and does not actually launch `ssh.exe`; the printed command is a parameter/forwarding comparison tool.
- When a Nexu SOCKS5/HTTP CONNECT proxy is configured, the panel logs that transport separately because it is implemented internally and is not represented by an external OpenSSH `ProxyCommand`.
- Includes the optional **OpenSSH local configuration diagnostic** from after rc.1, which compares `ssh -G` with `ssh -F NUL/-F /dev/null -G` and highlights ProxyJump, ProxyCommand, IdentityFile, HostName, User and Port differences.

- Added per-profile connection routing: **Direct**, **SOCKS5**, or **HTTP CONNECT**.
- Proxy host, port and optional username are stored with the profile; proxy passwords are requested when the tunnel starts and kept only in memory.
- Existing v1/v2 profile files remain compatible and default to **Direct**; the new profile format is v3.
- Added specific proxy diagnostics such as DNS failure, proxy timeout, authentication rejection, SOCKS5 failure and HTTP CONNECT failure.
- `FORWARDING_REJECTED` now records the concrete forwarding request and points to the relevant SSH-server policy checks. For remote forwarding this includes `AllowTcpForwarding`, the required `PermitListen` listener, `GatewayPorts` when a non-loopback listener is requested, and possible `Match`/per-user restrictions.
- 1.2.0 promotes the proxy transport, OpenSSH diagnostics, adaptive table improvements and bilingual UI from the release-candidate cycle into the stable release.

### Security

Proxy passwords are not written to the profile configuration or exported as plain profile data. They remain session-only and are cleared by the existing “lock and forget in-memory secrets” action or when the application closes.

This release is unsigned. Validate proxy and SSH-server policy settings in your own environment before production use.
