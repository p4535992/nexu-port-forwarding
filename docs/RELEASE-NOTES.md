# Nexu Port Forwarding 1.1.0-rc.3

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Prerelease adding external-profile import and operational filtering while keeping the 1.0.0 stable release unchanged.

### New in this prerelease

- Password/passphrase fields include a show/hide eye control.
- The **AZIONI** column is now the first table column.
- SSH connection failures now distinguish DNS, TCP timeout, connection refused, no route, interrupted handshake and authentication failures. Timeout diagnostics explicitly state that the connection is direct and the operating-system HTTP/SOCKS proxy is not automatically used.

- Main views ordered **Active → Custom → Tabby → MobaXterm**. Active contains only tunnels whose SSH forwarding state is ACTIVE.
- Read-only **Tabby** import from `config.yaml`, with preview and Local/Remote/Dynamic forwarding conversion.
- Read-only **MobaXterm** import from `[PortForwarding]` in `MobaXterm.ini`/`.mobaconf`; password sections and unrelated settings are ignored.
- Imported rows become ordinary local Nexu Port Forwarding profiles. Reopening Tabby or MobaXterm is not required after import, and imports never auto-start tunnels.
- Search filters for forwarding type and resolved SSH-server IP, in addition to free text and state.
- Separate **HOSTNAME** and **INDIRIZZO IP** columns. IP literals are copied immediately; hostnames are resolved asynchronously through the operating-system DNS resolver (no ICMP ping).
- Editable/searchable **INSTALLAZIONE** column for site/customer/environment labels.
- DYNAMIC (`-D`) SOCKS forwarding supported by the Apache MINA SSHD backend.
- Storage layout: portable packages use sibling `data/` and `logs/`; installed builds use the user parent `nexu-port-forwarding/data/` and `nexu-port-forwarding/logs/`. Recognized 1.0.0 user-data files are copied once into `data/` without deleting originals.

### Safety

Importers do not copy Tabby/MobaXterm passwords, host trust, scripts or unrelated configuration. Unsupported connection proxies/jump-host behavior is skipped instead of silently becoming a direct connection. All imported tunnels start stopped.

This is an unsigned prerelease. Validate imports against your own configuration before production use.
