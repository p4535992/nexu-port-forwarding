# Nexu Port Forwarding 1.2.2

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## English

Stable feature release 1.2.2: unified multi-term search and a combined LOCAL/REMOTE forwarding filter.

### Changes since 1.2.1

- Replaced separate installation, name and hostname/IP searches with a single search box for tunnel profiles.
- Every whitespace-separated term is matched as a case-insensitive substring of an individual field. **All** terms must match, but each may match a **different** field. For example, `prato 8687` matches a Prato profile with listener port 8687 regardless of word order.
- Search covers profile UUID, name/title, notes/description, installation, origin, forwarding type, SSH hostname, resolved SSH IP, username, listener/destination/proxy hostnames and IPs, relevant port numbers and import source ID. Passwords and private-key paths are not indexed.
- Search results update when asynchronous SSH-hostname resolution supplies an IP address.
- The type dropdown now includes **LOCAL + REMOTE (-L / -R)** alongside All types, LOCAL, REMOTE and DYNAMIC.
- The source tabs and connection-state filter continue to combine with search results.
- Added regression tests for multi-field terms, substrings, IPs, ports, IDs, empty input and type combinations.

### Safety

No SSH connection is started by searching or changing filters. Stored credentials are not indexed. Packages are unsigned; verify the downloaded checksums and the SSH-server settings before production use.
