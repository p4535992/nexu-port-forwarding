#!/usr/bin/env python3
"""Offline checks for release consistency and retired endpoints. Never print secrets.

History is read only from HEAD, heads and tags; no PR refs or remote resources.
This is a targeted audit plus common-token detection, not a security audit.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
# Hashes avoid publishing the retired infrastructure values in a prevention rule.
BLOCKED = {
    13: {'e48793dd8e58a890193b1cf3e3a506ddb960bb5684a8cf57464929435e9046a7'},
    12: {'410ef6c1beea25130b64f1472b3f4edc17b2830e7b187a811a089948afbb8145'},
}
TOKENS = re.compile(rb'[A-Za-z0-9_.-]{12,}')
SECRET_PATTERNS = (
    rb'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----',
    rb'\b(?:gh[pousr]_[A-Za-z0-9_]{24,}|github_pat_[A-Za-z0-9_]{24,})\b',
    rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
    rb'\bxox[baprs]-[A-Za-z0-9-]{15,}\b',
)


def git(*args: str) -> bytes:
    return subprocess.check_output(['git', *args], cwd=ROOT, stderr=subprocess.PIPE)


def retired(data: bytes) -> bool:
    # NUL removal also covers the ASCII endpoints in UTF-16/UTF-32 strings.
    for match in TOKENS.finditer(data.replace(b'\0', b'')):
        token = match.group()
        for length, hashes in BLOCKED.items():
            for i in range(len(token) - length + 1):
                if hashlib.sha256(token[i:i + length]).hexdigest() in hashes:
                    return True
    return False


def scan(data: bytes, label: str, errors: list[str]) -> None:
    if retired(data):
        errors.append(f'{label}: retired endpoint detected (value redacted)')
    if any(re.search(pattern, data) for pattern in SECRET_PATTERNS):
        errors.append(f'{label}: possible private key/access token (value redacted)')


def history(errors: list[str]) -> dict:
    refs = git('for-each-ref', '--format=%(refname)', 'refs/heads', 'refs/tags').decode().splitlines()
    refs = ['HEAD', *refs]
    commits = git('rev-list', *refs).decode().splitlines()
    objects = git('rev-list', '--objects', *refs).decode().splitlines()
    seen = set()
    for line in objects:
        oid, _, path = line.partition(' ')
        if oid in seen:
            continue
        seen.add(oid)
        kind = git('cat-file', '-t', oid).decode().strip()
        if kind in ('blob', 'commit', 'tag'):
            scan(git('cat-file', kind, oid), f'git object {oid[:12]}', errors)
        if path:
            scan(path.encode(), 'historical path', errors)
    return {'commits': len(commits), 'objects': len(seen), 'refs': refs}


def verify(app: Path | None, check_history: bool) -> dict:
    errors: list[str] = []
    files = [ROOT / p.decode() for p in git('ls-files', '-z').split(b'\0') if p]
    for file in files:
        if file.is_symlink():
            errors.append(f'{file.relative_to(ROOT)}: symlink not allowed in release sources')
        elif file.is_file():
            scan(file.read_bytes(), str(file.relative_to(ROOT)), errors)
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(ROOT / 'pom.xml')
    version = pom.findtext('m:version', namespaces=ns)
    if not version or not re.fullmatch(r'\d+\.\d+\.\d+', version):
        errors.append('pom.xml: invalid stable version')
    for name in ('README.md', 'README.it.md', 'docs/README.md', 'docs/README.it.md'):
        text = (ROOT / name).read_text(encoding='utf-8')
        prose = re.sub(r'```.*?```|`[^`]*`|\]\([^)]*\)', '', text, flags=re.S)
        if re.search(r'(?i)\bNexu\b(?! Port Forwarding)(?!-)', prose):
            errors.append(f'{name}: abbreviated project name')
        if not all(x in text for x in ('English', 'Italiano')):
            errors.append(f'{name}: language links missing')
    for file in [ROOT / 'README.md', ROOT / 'README.it.md', *(ROOT / 'docs').glob('*.md')]:
        text = file.read_text(encoding='utf-8')
        if re.search(r'v?0\.2\.[01](?:-rc\.\d+)?', text):
            errors.append(f'{file.relative_to(ROOT)}: obsolete release reference')
        for target in re.findall(r'\]\(([^)\s]+)\)', re.sub(r'```.*?```', '', text, flags=re.S)):
            if '://' in target or target.startswith(('#', 'mailto:')):
                continue
            target = target.split('#')[0]
            if target and not (file.parent / target).exists():
                errors.append(f'{file.relative_to(ROOT)}: broken local link {target}')
    markers = {
        'src/main/java/it/nexu/forwarding/Launcher.java': f'Nexu Port Forwarding {version}',
        'src/main/java/it/nexu/forwarding/NexuApplication.java': f'UI_READY {version} DECORATED WINDOWED',
        'src/main/java/it/nexu/forwarding/config/AppLog.java': f'application-started version={version}',
        '.github/workflows/release.yml': f"APP_VERSION: '{version}'",
        'scripts/package-linux.sh': '${1:-' + str(version) + '}',
        'scripts/package-windows.ps1': "$Version = '" + str(version) + "'",
    }
    for name, marker in markers.items():
        if marker not in (ROOT / name).read_text(encoding='utf-8'):
            errors.append(f'{name}: version mismatch')
    if not (ROOT / 'LICENSE').is_file() or 'MIT License' not in (ROOT / 'LICENSE').read_text():
        errors.append('MIT LICENSE missing')
    if pom.findtext('m:licenses/m:license/m:name', namespaces=ns) != 'MIT License':
        errors.append('pom.xml: MIT declaration missing')
    graph = history(errors) if check_history else None
    if app:
        jar = app / 'nexu-port-forwarding.jar'
        with zipfile.ZipFile(jar) as archive:
            if 'META-INF/LICENSE' not in archive.namelist():
                errors.append('Application JAR: LICENSE missing')
            for name in archive.namelist():
                if not name.endswith('/'):
                    scan(archive.read(name), f'application JAR: {name}', errors)
        for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'dependency-inventory.json'):
            if not (app / name).is_file():
                errors.append(f'Packaged application: {name} missing')
    return {'version': version, 'files_scanned': len(files), 'history': graph,
            'targeted_endpoints': 2, 'findings': errors,
            'scope': 'Retired endpoint and common credential-pattern checks; not a security audit.'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--history', action='store_true')
    parser.add_argument('--app', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.app, args.history)
        text = json.dumps(result, indent=2) + '\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text, encoding='utf-8')
        print(text)
        return bool(result['findings'])
    except (OSError, subprocess.CalledProcessError, ET.ParseError, zipfile.BadZipFile) as exc:
        print(f'Release verification could not complete: {type(exc).__name__}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
