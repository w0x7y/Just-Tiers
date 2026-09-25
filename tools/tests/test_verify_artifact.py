"""Feed incompatible packaged bytes to the release verifier."""

import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

from test_minecraft_targets import PROFILE, ROOT


class ArtifactVerificationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "gradle/targets").mkdir(parents=True)
        # A new version exercises declared policy rather than hardcoded version inference.
        (self.root / "gradle/targets/27.1.properties").write_text(PROFILE)
        (self.root / "gradle.properties").write_text(
            "org.gradle.jvmargs=-Xmx2G\norg.gradle.parallel=true\n\n# Release\nmod_version=2.0.0\n")
        self.libs = self.root / "build/27.1/libs"
        self.libs.mkdir(parents=True)
        self.metadata = {
            "version": "2.0.0+mc27.1", "environment": "client",
            "depends": {"minecraft": "27.1", "java": ">=25", "fabricloader": ">=0.19.5",
                        "yet_another_config_lib_v3": ">=3.9.7"},
        }
        self.mixins = {"compatibilityLevel": "JAVA_25", "required": True, "client": ["PlayerMixin"]}
        self.class_bytes = struct.pack(">IHH", 0xCAFEBABE, 0, 69) + (
            b"net/minecraft/world/entity/player/Player\x00getDisplayName")

    def package(self):
        path = self.libs / "just-tiers-2.0.0+mc27.1.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps(self.metadata))
            jar.writestr("justtiers.mixins.json", json.dumps(self.mixins))
            jar.writestr("com/w0x7y/justtiers/mixin/PlayerMixin.class", self.class_bytes)
        return path

    def verify(self):
        # Running optimized Python must not remove release safety checks.
        return subprocess.run([sys.executable, "-O", str(ROOT / "tools/verify_artifact.py"),
                               "--root", str(self.root), "27.1"], text=True, capture_output=True)

    def test_new_target_uses_declared_requirements(self):
        self.package()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_unrelated_gradle_properties_can_contain_multiple_jvm_flags(self):
        (self.root / "gradle.properties").write_text(
            "org.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8\nmod_version=2.0.0\n")
        self.package()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_legacy_mapping_strategy_works_without_legacy_version_name(self):
        profile = PROFILE.replace("mapping_strategy=official", "mapping_strategy=intermediary")
        (self.root / "gradle/targets/27.1.properties").write_text(profile)
        self.class_bytes = struct.pack(">IHH", 0xCAFEBABE, 0, 69) + b"net/minecraft/class_1657\x00method_5476"
        self.package()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_java_requirement_comes_from_profile_for_new_targets(self):
        (self.root / "gradle/targets/27.1.properties").write_text(PROFILE.replace("java_version=25", "java_version=21"))
        self.metadata["depends"]["java"] = ">=21"
        self.mixins["compatibilityLevel"] = "JAVA_21"
        self.class_bytes = self.class_bytes[:6] + struct.pack(">H", 65) + self.class_bytes[8:]
        self.package()
        result = self.verify()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_incompatible_metadata_is_rejected(self):
        mutations = [("minecraft", "26.3"), ("java", ">=21"), ("fabricloader", ">=0.19"),
                     ("yet_another_config_lib_v3", ">=3.8.2")]
        for key, value in mutations:
            with self.subTest(key=key):
                original = self.metadata["depends"][key]
                self.metadata["depends"][key] = value
                self.package()
                result = self.verify()
                self.assertNotEqual(0, result.returncode)
                self.assertIn(key, result.stderr)
                self.metadata["depends"][key] = original

    def test_stale_mod_version_is_rejected(self):
        self.metadata["version"] = "1.0.0+mc27.1"
        self.package()
        result = self.verify()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("version", result.stderr)

    def test_wrong_bytecode_and_mixin_namespace_are_rejected(self):
        original = self.class_bytes
        cases = {
            "class version": original[:6] + struct.pack(">H", 65) + original[8:],
            "class header": b"notclass" + original[8:],
            "namespace": original.replace(b"getDisplayName", b"method_5476"),
        }
        for name, data in cases.items():
            with self.subTest(name=name):
                self.class_bytes = data
                self.package()
                result = self.verify()
                self.assertNotEqual(0, result.returncode)
                self.assertIn(name, result.stderr)

    def test_missing_or_ambiguous_installable_jar_is_rejected(self):
        self.assertNotEqual(0, self.verify().returncode)
        artifact = self.package()
        (self.libs / "another.jar").write_bytes(artifact.read_bytes())
        result = self.verify()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("one installable JAR", result.stderr)


if __name__ == "__main__":
    unittest.main()
