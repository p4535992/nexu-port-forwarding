#!/usr/bin/env python3
"""Collect actual runtime artifacts, intact notices and bilingual offline docs."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'target/app'


def main() -> None:
    if not (APP / 'nexu-port-forwarding.jar').is_file():
        raise SystemExit('Run mvn clean verify first.')
    for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'README.md', 'README.it.md'):
        shutil.copy2(ROOT / name, APP / name)
    shutil.copytree(ROOT / 'docs', APP / 'docs', dirs_exist_ok=True)
    inventory = []
    for jar in sorted((APP / 'lib').glob('*.jar')):
        embedded = []
        with zipfile.ZipFile(jar) as archive:
            for item in archive.infolist():
                path = PurePosixPath(item.filename)
                if item.is_dir() or not re.search(r'(?i)(license|licence|notice|copying|copyright)', path.name):
                    continue
                if path.is_absolute() or '..' in path.parts or item.file_size > 4_000_000:
                    raise ValueError('Unsafe embedded notice path or size')
                dest = APP / 'legal/dependencies' / jar.name / Path(*path.parts)
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(archive.read(item))
                embedded.append(item.filename)
        inventory.append({'file': jar.name, 'bytes': jar.stat().st_size,
                          'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                          'embedded_legal_resources': embedded})
    if not inventory:
        raise SystemExit('Runtime dependency directory is empty.')
    report = {'application': 'Nexu Port Forwarding', 'application_license': 'MIT',
              'scope': 'Exact runtime JAR inventory; original JARs are unmodified. '
                       'Embedded notices are also copied for convenience. '
                       'Runtime legal notices remain in the native runtime legal directory. '
                       'This inventory is not a license compatibility or security audit.',
              'artifacts': inventory}
    (APP / 'dependency-inventory.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'Prepared distribution documentation and inventory for {len(inventory)} runtime JARs.')


if __name__ == '__main__':
    main()
