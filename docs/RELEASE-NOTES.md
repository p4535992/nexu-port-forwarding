# Nexu Port Forwarding 1.2.1

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Stable maintenance release 1.2.1 with clearer tunnel-state feedback, improved remote-forwarding diagnostics, and substantially more robust Tabby/MobaXterm import handling.

### Changes since 1.2.0

- Tunnel rows in **STOPPING** state switch to the red background immediately. **STOPPED** rows, including newly created tunnels, remain red; **ACTIVE** rows remain green.
- Existing **Start/Stop** button colors are unchanged.
- When a **REMOTE (-R)** forwarding request is rejected after successful SSH authentication, the diagnostic explicitly suggests checking whether the requested listener on the SSH server is already occupied by another SSH remote forwarding/session or another process.
- The same diagnostic also points to `AllowTcpForwarding`, `PermitListen`, `GatewayPorts` for non-loopback listeners, and `Match`/per-user restrictions.

- Tabby v8 forwarding entries under `profiles[*].options.forwardedPorts` are supported, including multiple Local/Remote forwards in one SSH profile.
- Tabby imports preserve SSH hostname, SSH port, username, listener and destination without importing passwords.
- Missing Tabby `auth` no longer blocks normal selection: the profile is imported without a stored credential and Nexu asks for it when the tunnel starts.

- MobaXterm `[PortForwarding]` imports preserve Local/Remote definitions, SSH usernames and repeated tunnel names.
- Recognized MobaXterm `WEB proxy`/HTTP CONNECT and SOCKS5 transports are preserved without importing proxy credentials.
- Malformed MobaXterm forwarding entries are isolated per row: model validation errors increment `skipped` and are reported as warnings instead of aborting the entire file.
- A regression test verifies the sequence valid Local → invalid URL-like SSH host → valid Remote, ensuring later valid entries remain importable.
- Imported Tabby and MobaXterm profiles are appended to existing profiles; Custom, Tabby and MobaXterm origins remain distinct.

### Diagnostic note

A generic SSH remote-forwarding rejection does not always reveal whether the cause is a port conflict or an `sshd` policy restriction. Nexu reports both possibilities instead of claiming one specific cause.

### Safety

Passwords are not imported from Tabby or MobaXterm configuration files. This release is unsigned; validate forwarding behavior and SSH-server policy settings in your own environment before production use.
