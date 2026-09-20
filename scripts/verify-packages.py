#!/usr/bin/env python3
"""Verify downloaded release packages before publication (no network access)."""
from pathlib import Path
import importlib.util
import json
import os
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_checks', ROOT / 'scripts/verify-release.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def main():
    version = os.environ['APP_VERSION']
    directory = ROOT / 'release-assets'
    prefix = f'nexu-port-forwarding-{version}-'
    expected = ('windows-x64.zip', 'windows-x64.exe', 'windows-x64.msi',
                'linux-x64.tar.gz', 'linux-x64.deb', 'linux-x64.rpm',
                'windows-java.zip', 'linux-java.tar.gz', 'source.zip')
    errors = []
    for suffix in expected:
        path = directory / (prefix + suffix)
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f'Missing distribution: {path.name}')
    for path in sorted(directory.iterdir()):
        if path.suffix == '.zip':
            with zipfile.ZipFile(path) as archive:
                if archive.testzip():
                    raise ValueError(f'Corrupt ZIP: {path.name}')
                names = archive.namelist()
                if path.name.startswith('diagnostics-'):
                    for name in names:
                        if not name.endswith('/'):
                            checks.scan(archive.read(name), path.name + ':' + name, errors)
                    continue
        elif path.name.endswith('.tar.gz'):
            with tarfile.open(path, 'r:gz') as archive:
                names = archive.getnames()
        else:
            continue
        for name in names:
            parts = Path(name).parts
            if '.git' in parts or name.endswith(('.npfvault', '.npfbackup', '.p12', '.pfx')):
                raise ValueError(f'Unexpected local data in {path.name}')
        for name in ('LICENSE', 'docs/GETTING-STARTED.md', 'docs/GETTING-STARTED.it.md'):
            if not any(n.endswith('/' + name) for n in names):
                raise ValueError(f'{path.name}: {name} missing')
        if 'source.zip' not in path.name and not any(n.endswith('/dependency-inventory.json') for n in names):
            raise ValueError(f'{path.name}: dependency inventory missing')
        if 'x64.zip' in path.name or 'x64.tar.gz' in path.name:
            if not any('/runtime/' in n and '/legal/' in n for n in names):
                raise ValueError(f'{path.name}: bundled runtime legal directory missing')
    if errors:
        raise ValueError('; '.join(errors))
    print(json.dumps({'application': 'Nexu Port Forwarding', 'version': version,
                      'distributions': len(expected), 'package_checks': 'passed',
                      'scope': 'Archive integrity, expected content, license/docs inclusion and diagnostic scan.'}))


if __name__ == '__main__':
    main()
