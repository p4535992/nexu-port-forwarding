# Verification — 1.1.0-rc.1

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

This prerelease adds Tabby/MobaXterm import, Active/Custom/Tabby/MobaXterm views, Local/Remote/Dynamic filtering, asynchronous hostname-to-IP display/filtering, and the `nexu-port-forwarding/data` + sibling `logs` layout.

Local dependency-free checks cover the core engine, encrypted storage, portable/user path resolution and migration, Tabby conversion and MobaXterm `[PortForwarding]` conversion. The release workflow additionally runs Maven/JUnit, Apache MINA loopback SSH integration tests, native packaging and packaged JavaFX smoke tests on Windows and Linux.

Importers are read-only against external configuration. Tabby passwords/vault and MobaXterm password sections are not imported. Unsupported proxy/jump-host semantics are rejected rather than silently downgraded. Imported rows are persisted in Nexu Port Forwarding and start stopped.

Hostname resolution uses the operating-system DNS resolver for display/filtering and does not send ICMP ping. IP literals are copied directly.

The 1.0.0 user layout is migrated conservatively: recognized files directly below the user `nexu-port-forwarding` parent are copied to `data/` only when the destination file is absent; originals are retained. Portable storage remains local to the extracted package.

The prerelease remains unsigned and is not a security audit. Real MobaXterm variants, real Tabby files, Linux tray implementations, installer upgrade/uninstall and real SSH server policies still require operator validation.
