import json
import tempfile
import unittest
from pathlib import Path

from prepare_release import APPLICATION_ID, prepare


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.metadata = self.root / "output-metadata.json"
        self.output = self.root / "release"
        self.data = {
            "applicationId": APPLICATION_ID,
            "elements": [{"versionCode": 3, "versionName": "1.2.1",
                          "outputFile": "app-debug.apk", "filters": []}],
        }
        (self.root / "app-debug.apk").write_bytes(b"test-apk-bytes")

    def run_prepare(self):
        self.metadata.write_text(json.dumps(self.data))
        return prepare(self.metadata, self.output)

    def test_uses_built_apk_version_and_preserves_bytes(self):
        result = self.run_prepare()
        self.assertEqual(3, result["versionCode"])
        self.assertEqual("https://github.com/nbnurlan/Gidscreen/releases/download/v1.2.1/Gidscreen.apk",
                         result["apkUrl"])
        self.assertEqual(b"test-apk-bytes", (self.output / "Gidscreen.apk").read_bytes())
        self.assertEqual(result, json.loads((self.output / "update.json").read_text()))

    def test_rejects_different_app(self):
        self.data["applicationId"] = "other.app"
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_rejects_splits(self):
        self.data["elements"][0]["filters"] = [{"filterType": "ABI", "value": "arm64"}]
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_rejects_unsafe_version_or_path(self):
        for field, value in [("versionName", "1.2.1/../../bad"), ("versionCode", -1),
                             ("versionCode", True), ("outputFile", "../other.apk")]:
            with self.subTest(field=field, value=value):
                original = self.data["elements"][0][field]
                self.data["elements"][0][field] = value
                with self.assertRaises(ValueError):
                    self.run_prepare()
                self.data["elements"][0][field] = original

    def test_rejects_missing_apk(self):
        (self.root / "app-debug.apk").unlink()
        with self.assertRaises(ValueError):
            self.run_prepare()


if __name__ == "__main__":
    unittest.main()
