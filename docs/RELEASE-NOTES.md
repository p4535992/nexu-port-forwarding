# Nexu Port Forwarding 1.2.1-rc.2

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Prerelease focused on clearer tunnel-state feedback and more useful diagnostics when an SSH server rejects remote port forwarding.

### Changes since 1.2.0

- Tunnel rows in **STOPPING** state now switch to the red background immediately. **STOPPED** rows, including newly created tunnels, remain red; **ACTIVE** rows remain green.
- The existing **Start/Stop** button colors are unchanged.
- When a **REMOTE (-R)** forwarding request is rejected after successful SSH authentication, the diagnostic now explicitly suggests checking whether the requested listener on the SSH server is already occupied by another SSH remote forwarding/session or another process.
- The same diagnostic still points to server-side policy checks such as `AllowTcpForwarding`, `PermitListen`, `GatewayPorts` for non-loopback listeners, and `Match`/per-user restrictions.
- Added a regression test covering the new remote-port-conflict hint.
- Application/package version moved to **1.2.1** for the **v1.2.1-rc.2** prerelease.
- Tabby v8 profiles with forwarding entries under `profiles[*].options.forwardedPorts` are covered by a real-shape regression fixture: multiple Local/Remote forwards per SSH profile preserve SSH host, port and username while importing no password.
- MobaXterm `[PortForwarding]` fixtures now preserve Local/Remote definitions, SSH usernames and repeated tunnel names; recognized `WEB proxy` / SOCKS5 transports are mapped to Nexu proxy settings without importing proxy credentials.
- Missing Tabby `auth` no longer blocks default selection: the profile is imported without a saved credential and Nexu asks for it when the tunnel is started.
- rc.2 fixes the MobaXterm proxy carry-over regression so recognized HTTP CONNECT/SOCKS5 settings remain attached to the imported profile, and updates the Tabby regression test to match the intended no-credential import behavior.

### Diagnostic note

A generic SSH remote-forwarding rejection does not always reveal whether the cause is a port conflict or an `sshd` policy restriction. Nexu therefore reports both possibilities instead of claiming one specific cause.

### Safety

This is an unsigned prerelease. Validate forwarding behavior and SSH-server policy settings in your own environment before production use.
