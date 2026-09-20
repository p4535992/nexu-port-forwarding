import hashlib
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('release_checks', Path(__file__).with_name('verify-release.py'))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class ReleaseChecksTest(unittest.TestCase):
    def setUp(self):
        self.saved = checks.BLOCKED
        self.sample = b'private-for-test.example'
        checks.BLOCKED = {len(self.sample): {hashlib.sha256(self.sample).hexdigest()}}

    def tearDown(self):
        checks.BLOCKED = self.saved

    def test_detects_ascii_without_logging_value(self):
        found = []
        checks.scan(b'prefix-' + self.sample + b'-suffix', 'test file', found)
        self.assertEqual(1, len(found))
        self.assertNotIn(self.sample.decode(), found[0])

    def test_detects_utf16(self):
        self.assertTrue(checks.retired(self.sample.decode().encode('utf-16le')))

    def test_documentation_example_is_allowed(self):
        self.assertFalse(checks.retired(b'203.0.113.10 maven.example.com'))

    def test_token_pattern(self):
        found = []
        checks.scan(b'ghp_' + b'Z' * 36, 'test file', found)
        self.assertTrue(found)
        self.assertNotIn('Z' * 36, found[0])


if __name__ == '__main__':
    unittest.main()
