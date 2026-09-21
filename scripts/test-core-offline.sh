#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/target/offline-tests"
mkdir -p "$OUT"
find "$ROOT/src/main/java/it/nexu/forwarding/model" "$ROOT/src/main/java/it/nexu/forwarding/config" "$ROOT/src/main/java/it/nexu/forwarding/ssh" "$ROOT/src/main/java/it/nexu/forwarding/importer" \
  -name '*.java' ! -name 'MinaTunnelBackend.java' ! -name 'ProfileForwardingFilter.java' ! -name 'TabbyYamlReader.java' -print > "$OUT/sources.txt"
printf '%s\n' "$ROOT/src/test/java/it/nexu/forwarding/CoreSelfTest.java" "$ROOT/src/test/java/it/nexu/forwarding/StorageSelfTest.java" "$ROOT/src/test/java/it/nexu/forwarding/PortableStorageSelfTest.java" "$ROOT/src/test/java/it/nexu/forwarding/TabbyImportSelfTest.java" "$ROOT/src/test/java/it/nexu/forwarding/MobaXtermImportSelfTest.java" >> "$OUT/sources.txt"
sed 's/.*/"&"/' "$OUT/sources.txt" > "$OUT/quoted-sources.txt"
javac --release 21 -encoding UTF-8 -d "$OUT" @"$OUT/quoted-sources.txt"
java -cp "$OUT" it.nexu.forwarding.CoreSelfTest
java -cp "$OUT" it.nexu.forwarding.StorageSelfTest
java -cp "$OUT" it.nexu.forwarding.PortableStorageSelfTest

java -cp "$OUT" it.nexu.forwarding.TabbyImportSelfTest
java -cp "$OUT" it.nexu.forwarding.MobaXtermImportSelfTest
