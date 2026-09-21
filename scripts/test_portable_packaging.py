"""Offline tests for portable marker placement, not tests of native installers."""
from pathlib import Path
import os
import shutil
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PortablePackagingTest(unittest.TestCase):
    def test_marker_defaults_to_portable(self):
        text = (ROOT / 'scripts/portable.properties').read_text(encoding='utf-8')
        values = dict(line.split('=', 1) for line in text.splitlines() if '=' in line and not line.startswith('#'))
        self.assertEqual({'version': '1', 'storage': 'portable'}, values)

    def test_windows_marker_is_added_after_installer_loop(self):
        text = (ROOT / 'scripts/package-windows.ps1').read_text(encoding='utf-8')
        self.assertLess(text.index('Copy-Item $package.FullName'), text.index('Copy-Item scripts/portable.properties'))
        self.assertLess(text.index('Copy-Item scripts/portable.properties'), text.index('Compress-Archive'))
        self.assertNotIn('set "NEXU_PF_HOME=', text)

    @unittest.skipIf(os.name == 'nt' or shutil.which('bash') is None, 'POSIX shell fixture only')
    def test_linux_portable_marker_is_absent_from_installer_input(self):
        with tempfile.TemporaryDirectory(prefix='npf-package-') as temp:
            root = Path(temp)
            (root / 'scripts').mkdir(); (root / 'docs').mkdir(); (root / 'target/app').mkdir(parents=True)
            for name in ('package-linux.sh', 'portable.properties'):
                shutil.copy2(ROOT / 'scripts' / name, root / 'scripts' / name)
            for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md'):
                (root / name).write_text('Fixture notice\n')
            (root / 'docs/DATA-AND-LOGS.txt').write_text('Fixture guide\n')
            (root / 'target/app/nexu-port-forwarding.jar').write_bytes(b'fixture only')
            tools = root / 'tools'; tools.mkdir()
            mock = tools / 'jpackage'
            mock.write_text('''#!/usr/bin/env python3
import sys
from pathlib import Path
args=sys.argv[1:]
def value(key): return args[args.index(key)+1]
out=Path(value('--dest'))
kind=value('--type')
if kind == 'app-image':
    image=out/'NexuPortForwarding'
    (image/'bin').mkdir(parents=True)
    (image/'bin/NexuPortForwarding').write_text('fixture')
else:
    image=Path(value('--app-image'))
    assert not (image/'portable.properties').exists(), 'Portable preference leaked into installer'
    (out/('fixture.'+kind)).write_bytes(b'fixture package')
''', encoding='utf-8')
            mock.chmod(0o755)
            env = dict(os.environ, PATH=str(tools)+os.pathsep+os.environ['PATH'])
            subprocess.run(['bash', str(root/'scripts/package-linux.sh'), '1.1.0'], cwd=root, env=env, check=True, capture_output=True)
            with tarfile.open(root/'target/release-assets/nexu-port-forwarding-1.1.0-linux-x64.tar.gz') as archive:
                marker = archive.extractfile('NexuPortForwarding/portable.properties').read()
                starter = archive.extractfile('NexuPortForwarding/start-portable.sh').read()
                self.assertIn(b'storage=portable', marker)
                self.assertNotIn(b'export NEXU_PF_HOME=', starter)
                self.assertFalse(any('/data/' in m.name or '/logs/' in m.name for m in archive.getmembers()))
