"""Exercise discovery through the same CLI used by workflows."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/minecraft_targets.py"
PROFILE = """java_version=25
loader_version=0.19.5
loader_min_version=0.19.5
mapping_strategy=official
source_strategy=native
screen_event=afterForeground
fabric_api_version=0.161.0+26.3
yacl_dependency=maven.modrinth:yacl:s9SjoFu1
yacl_min_version=3.9.7
modmenu_dependency=maven.modrinth:modmenu:kyy7dbrZ
"""


class TargetDiscoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "gradle/targets").mkdir(parents=True)

    def write(self, version, profile=PROFILE):
        (self.root / "gradle/targets" / f"{version}.properties").write_text(profile)

    def run_cli(self, command):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root), command],
            text=True, capture_output=True)

    def test_new_profile_automatically_joins_matrix_and_artifact_count(self):
        self.write("26.3")
        self.write("27.1")
        result = self.run_cli("matrix")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"minecraft": ["26.3", "27.1"]}, json.loads(result.stdout))
        self.assertEqual("2\n", self.run_cli("count").stdout)
        self.assertEqual("26.3\n27.1\n", self.run_cli("list").stdout)

    def test_malformed_profiles_stop_discovery_without_partial_output(self):
        self.write("26.2")
        cases = {
            "missing floor": PROFILE.replace("loader_min_version=0.19.5\n", ""),
            "duplicate key": PROFILE + "java_version=21\n",
            "unknown key": PROFILE + "loader_min_verison=0.19.5\n",
            "invalid java": PROFILE.replace("java_version=25", "java_version=twenty-five"),
            "untested floor": PROFILE.replace("loader_version=0.19.5", "loader_version=0.19.3"),
            "unknown mapping": PROFILE.replace("mapping_strategy=official", "mapping_strategy=unknown"),
            "unknown source": PROFILE.replace("source_strategy=native", "source_strategy=unknown"),
            "unknown event": PROFILE.replace("screen_event=afterForeground", "screen_event=unknown"),
            "unversioned dependency": PROFILE.replace("maven.modrinth:yacl:s9SjoFu1", "maven.modrinth:yacl"),
            "dynamic dependency": PROFILE.replace("maven.modrinth:yacl:s9SjoFu1", "maven.modrinth:yacl:latest.release"),
            "unsupported properties escape": PROFILE + "# end\ninvalid\\key=value\n",
        }
        for name, profile in cases.items():
            with self.subTest(name=name):
                self.write("26.3", profile)
                result = self.run_cli("matrix")
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", result.stdout)
                self.assertIn("26.3.properties", result.stderr)

    def test_empty_catalog_and_invalid_target_filename_fail(self):
        self.assertNotEqual(0, self.run_cli("matrix").returncode)
        self.write("26.3;echo oops")
        self.assertNotEqual(0, self.run_cli("matrix").returncode)


if __name__ == "__main__":
    unittest.main()
