# Nexu Port Forwarding 1.2.0-rc.1

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

First 1.2.0 release candidate focused on explicit SSH proxy support and clearer forwarding diagnostics.

### New in this prerelease

- Added per-profile connection routing: **Direct**, **SOCKS5**, or **HTTP CONNECT**.
- Proxy host, port and optional username are stored with the profile; proxy passwords are requested when the tunnel starts and kept only in memory.
- Existing v1/v2 profile files remain compatible and default to **Direct**; the new profile format is v3.
- Added specific proxy diagnostics such as DNS failure, proxy timeout, authentication rejection, SOCKS5 failure and HTTP CONNECT failure.
- `FORWARDING_REJECTED` now records the concrete forwarding request and points to the relevant SSH-server policy checks. For remote forwarding this includes `AllowTcpForwarding`, the required `PermitListen` listener, `GatewayPorts` when a non-loopback listener is requested, and possible `Match`/per-user restrictions.
- The stable 1.1.0 release remains unchanged; this prerelease is for validating the new proxy transport on Windows and Linux.

### Security

Proxy passwords are not written to the profile configuration or exported as plain profile data. They remain session-only and are cleared by the existing “lock and forget in-memory secrets” action or when the application closes.

This is an unsigned prerelease. Validate proxy and SSH-server policy settings in your own environment before production use.
