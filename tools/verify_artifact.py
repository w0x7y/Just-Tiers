"""Check packaged metadata and bytecode against the selected target profile."""

import argparse
import json
import re
import struct
import zipfile
from pathlib import Path

from minecraft_targets import ROOT, Target, discover_targets


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_jar(path: Path, target: Target, mod_version: str) -> None:
    java = target.java_version
    with zipfile.ZipFile(path) as jar:
        metadata = json.loads(jar.read("fabric.mod.json"))
        for key, expected in {
            "minecraft": target.minecraft,
            "java": f">={java}",
            "fabricloader": f">={target.loader_min_version}",
            "yet_another_config_lib_v3": f">={target.yacl_min_version}",
        }.items():
            require(metadata["depends"].get(key) == expected, f"{path}: wrong {key} requirement; expected {expected}")
        require(metadata["version"] == f"{mod_version}+mc{target.minecraft}", f"{path}: wrong mod version")
        require(metadata["environment"] == "client", f"{path}: wrong mod environment")
        mixins = json.loads(jar.read("justtiers.mixins.json"))
        require(mixins["compatibilityLevel"] == f"JAVA_{java}", f"{path}: wrong mixin Java compatibility")
        require(mixins["required"] is True and "PlayerMixin" in mixins["client"], f"{path}: missing required PlayerMixin")
        classes = [name for name in jar.namelist() if name.endswith(".class")]
        require(bool(classes), f"{path}: empty mod JAR")
        for name in classes:
            bytecode = jar.read(name)
            require(len(bytecode) >= 8 and bytecode[:4] == b"\xca\xfe\xba\xbe", f"{name}: invalid class header")
            major = struct.unpack(">H", bytecode[6:8])[0]
            require(major == java + 44, f"{name}: class version {major}, expected Java {java}")
        mixin = jar.read("com/w0x7y/justtiers/mixin/PlayerMixin.class")
        # Inspect actual bytecode independently of the source conversion or Gradle task chosen.
        namespaces = {
            "intermediary": (b"net/minecraft/class_1657", b"method_5476"),
            "official": (b"net/minecraft/world/entity/player/Player", b"getDisplayName"),
        }
        player, method = namespaces[target.mapping_strategy]
        require(player in mixin and method in mixin, f"{path}: wrong mixin mapping namespace")


def verify(minecraft: str, root: Path = ROOT) -> Path:
    targets = {target.minecraft: target for target in discover_targets(root)}
    require(minecraft in targets, f"Unsupported Minecraft target: {minecraft}")
    target = targets[minecraft]
    jars = [jar for jar in (root / "build" / minecraft / "libs").glob("*.jar")
            if not jar.name.endswith("-sources.jar")]
    require(len(jars) == 1, f"Expected one installable JAR for {minecraft}: {jars}")
    # Unrelated Gradle settings can use spaces and other properties syntax. Only
    # the plain mod_version declaration participates in the artifact contract.
    versions = re.findall(r"(?m)^[ \t]*mod_version[ \t]*=[ \t]*([^\s\\]+)[ \t]*$",
                          (root / "gradle.properties").read_text(encoding="utf-8"))
    require(len(versions) == 1, "Expected one plain mod_version declaration in gradle.properties")
    verify_jar(jars[0], target, versions[0])
    print(f"Verified {jars[0].relative_to(root)}: Java {target.java_version}, metadata and mixin targets")
    return jars[0]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("minecraft")
    args = parser.parse_args()
    try:
        verify(args.minecraft, args.root)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.error(str(error))
