# Nexu Port Forwarding 1.1.0

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Stable 1.1.0 release with the tested Windows/Linux package set and the UI, import, diagnostics, packaging and SSH runtime improvements validated through the release-candidate cycle.

### Highlights in 1.1.0

- Removed the global **Avvia visibili** button from the main toolbar to avoid accidental bulk starts.
- Moved **Importa Tabby…** and **Importa MobaXterm…** into the **Profili** menu, keeping import operations grouped with profile management.
- **Ferma tutti** remains directly available in the toolbar for fast shutdown of active tunnels.
- The row action is visually state-aware: **▶ Avvia** is green on stopped/red rows, while **■ Ferma** is red when a tunnel is running.
- Long **NOME** values now expand the table row dynamically so wrapped text remains fully visible.
- The right-side activity/log panel can be collapsed and reopened with a compact edge control, making the grid more usable on smaller screens.
- **INSTALLAZIONE**, **NOME**, and **HOSTNAME / INDIRIZZO IP** now wrap automatically across multiple lines; **INSTALLAZIONE** and **NOME** can be edited directly in the grid with automatic persistence.

- **NOME** cells now wrap onto multiple lines instead of truncating long labels with an ellipsis; table rows grow as needed while keeping a 62 px minimum height.
- **INSTALLAZIONE** is shown again immediately before **NOME** in the main grid.
- The broad free-text search has been replaced by three explicit text filters: **Installazione**, **Nome**, and **Hostname / Indirizzo IP**. Type and state remain separate structured filters.

- Fixed native runtime packaging by including the JDK `java.rmi` module required by the packaged SSH stack; this resolves the reported missing `java.rmi.ServerException` failure.
- The packaged SSH runtime smoke test now explicitly loads `java.rmi.ServerException`, so a native image missing this module cannot be published.

- Row **Avvia/Ferma** controls are now one compact state-aware button; pressing it automatically selects the row and shows its tunnel log.
- The activity/log area is now a **right-side vertical panel** beside the tunnel grid.
- **PORTA SSH** is folded into the compact **FORWARDING** text (for example `R · SSH:22 · SERVER[...] → PC → destination`).
- **HOSTNAME** and resolved **INDIRIZZO IP** are combined into one **HOSTNAME / INDIRIZZO IP** column; literal IP profiles show the IP only.

- Fixed a packaged-runtime SSH startup failure that surfaced as `ClassNotFoundException` before any network connection was attempted.
- Apache MINA SSHD now uses the NIO2 I/O backend explicitly instead of relying on runtime provider discovery inside jpackage images.
- Added a **packaged SSH runtime smoke test** on both Windows and Linux; a native package is no longer published unless the packaged executable can initialize the SSH stack successfully.
- Missing runtime classes are now reported as application/runtime errors, not misleading DNS/firewall/proxy failures.

- Fixed vault/master-password dialog validation feedback after adding password reveal controls: invalid input now shows a specific persistent error message and focuses the field to correct; valid input closes the dialog normally.

- Renamed rotating log files from `nexu-0.log` style names to `nexu-port-forwarding-0.log`, `nexu-port-forwarding-1.log`, etc. Existing older log files are left untouched.

- Preflight local listener availability for LOCAL and DYNAMIC/SOCKS before SSH authentication. Occupied ports are reported immediately with a clear hint that another tunnel application (for example Tabby/MobaXterm) may already be using them.
- Persistent logs now record sanitized diagnostic categories and safe context for recognized failures, while still excluding credentials and arbitrary raw exception text.

- Inline-editable **NOME** with automatic persistence on Enter or focus loss.
- **INSTALLAZIONE** is displayed directly before **NOME** in the main grid.
- **TIPO**, **ASCOLTO** and **DESTINAZIONE** are condensed into one **FORWARDING** column with explicit listener-side arrows.
- Separate command exports for Windows PowerShell, Windows CMD and Linux/POSIX; CMD no longer receives PowerShell quoting.

- Password/passphrase fields include a show/hide eye control.
- The **AZIONI** column is now the first table column.
- SSH connection failures now distinguish DNS, TCP timeout, connection refused, no route, interrupted handshake and authentication failures. Timeout diagnostics explicitly state that the connection is direct and the operating-system HTTP/SOCKS proxy is not automatically used.

- Main views ordered **Active → Custom → Tabby → MobaXterm**. Active contains only tunnels whose SSH forwarding state is ACTIVE.
- Read-only **Tabby** import from `config.yaml`, with preview and Local/Remote/Dynamic forwarding conversion.
- Read-only **MobaXterm** import from `[PortForwarding]` in `MobaXterm.ini`/`.mobaconf`; password sections and unrelated settings are ignored.
- Imported rows become ordinary local Nexu Port Forwarding profiles. Reopening Tabby or MobaXterm is not required after import, and imports never auto-start tunnels.
- Search uses dedicated **Installazione**, **Nome**, and **Hostname / Indirizzo IP** fields; forwarding type and state remain separate filters.
- DYNAMIC (`-D`) SOCKS forwarding supported by the Apache MINA SSHD backend.
- Storage layout: portable packages use sibling `data/` and `logs/`; installed builds use the user parent `nexu-port-forwarding/data/` and `nexu-port-forwarding/logs/`. Recognized 1.0.0 user-data files are copied once into `data/` without deleting originals.

### Safety

Importers do not copy Tabby/MobaXterm passwords, host trust, scripts or unrelated configuration. Unsupported connection proxies/jump-host behavior is skipped instead of silently becoming a direct connection. All imported tunnels start stopped.

This release is unsigned. Validate imports against your own configuration and deployment environment before production use.
