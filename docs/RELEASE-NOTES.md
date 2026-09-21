# Nexu Port Forwarding 1.2.0-rc.2

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Second 1.2.0 release candidate, adding direct OpenSSH comparison tools on top of the proxy support and forwarding diagnostics introduced in rc.1.

### New in this prerelease

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
- The stable 1.1.0 release remains unchanged; this prerelease is for validating the new proxy transport on Windows and Linux.

### Security

Proxy passwords are not written to the profile configuration or exported as plain profile data. They remain session-only and are cleared by the existing “lock and forget in-memory secrets” action or when the application closes.

This is an unsigned prerelease. Validate proxy and SSH-server policy settings in your own environment before production use.
