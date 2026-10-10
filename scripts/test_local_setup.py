import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("init_local", Path(__file__).with_name("init-local.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class LocalSetupTest(unittest.TestCase):
    def test_creates_private_unique_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = Path(directory) / "first.env", Path(directory) / "second.env"
            self.assertTrue(setup.initialize(first))
            self.assertTrue(setup.initialize(second))
            one = dict(line.split("=", 1) for line in first.read_text().splitlines())
            two = dict(line.split("=", 1) for line in second.read_text().splitlines())
            self.assertEqual(one["BOOTSTRAP_USERNAME"], "admin")
            self.assertGreaterEqual(len(one["BOOTSTRAP_PASSWORD"]), 12)
            self.assertNotEqual(one["DATABASE_PASSWORD"], one["BOOTSTRAP_PASSWORD"])
            self.assertNotEqual(one["DATABASE_PASSWORD"], two["DATABASE_PASSWORD"])
            self.assertEqual(stat.S_IMODE(first.stat().st_mode), 0o600)

    def test_preserves_existing_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            original = "BOOTSTRAP_USERNAME=existing\nBOOTSTRAP_PASSWORD=existing-local-password\n"
            path.write_text(original)
            self.assertFalse(setup.initialize(path))
            self.assertEqual(path.read_text(), original)


if __name__ == "__main__":
    unittest.main()
