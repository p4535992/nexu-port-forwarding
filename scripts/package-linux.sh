#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
VERSION="${1:-1.0.0}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid numeric app version' >&2; exit 1; }
[[ -f target/app/nexu-port-forwarding.jar ]] || { echo 'Run mvn clean verify first.' >&2; exit 1; }
OUT=target/package
[[ ! -e "$OUT/NexuPortForwarding" ]] || { echo 'Clean target/package before packaging again.' >&2; exit 1; }
mkdir -p "$OUT" target/release-assets
jpackage --type app-image --name NexuPortForwarding --app-version "$VERSION" \
  --vendor 'Nexu Port Forwarding' --description 'Desktop SSH TCP tunnel manager' \
  --input target/app --dest "$OUT" --main-jar nexu-port-forwarding.jar \
  --main-class it.nexu.forwarding.Launcher --icon src/main/resources/app-icon.png \
  --java-options '-Dfile.encoding=UTF-8' \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.management,java.security.jgss,java.security.sasl,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.unsupported.desktop,jdk.charsets,jdk.zipfs
cp docs/DATA-AND-LOGS.txt "$OUT/NexuPortForwarding/LOGS.txt"
cp THIRD_PARTY_NOTICES.md "$OUT/NexuPortForwarding/"
cp LICENSE "$OUT/NexuPortForwarding/"
cp -R docs "$OUT/NexuPortForwarding/docs"
# Installers intentionally never include user configuration, vaults, or logs.
for type in deb rpm; do
  jpackage --type "$type" --app-image "$OUT/NexuPortForwarding" --app-version "$VERSION" \
    --dest "$OUT" --linux-package-name nexu-port-forwarding --linux-menu-group Network --linux-shortcut
  package="$(find "$OUT" -maxdepth 1 -type f -name "*.$type" -print -quit)"
  [[ -n "$package" ]] || exit 1
  cp "$package" "target/release-assets/nexu-port-forwarding-$VERSION-linux-x64.$type"
done
cat > "$OUT/NexuPortForwarding/start-portable.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export NEXU_PF_HOME="$HERE/data"
exec "$HERE/bin/NexuPortForwarding" "$@"
EOF
chmod +x "$OUT/NexuPortForwarding/start-portable.sh"
tar -C "$OUT" -czf "target/release-assets/nexu-port-forwarding-$VERSION-linux-x64.tar.gz" NexuPortForwarding
# Platform JAR is distributed with its lib directory, never misleadingly as a standalone JAR.
tar -C target -czf "target/release-assets/nexu-port-forwarding-$VERSION-linux-java.tar.gz" app
printf 'Linux artifacts prepared in target/release-assets\n'
