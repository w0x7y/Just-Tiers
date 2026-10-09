"""Read the flat target profiles shared by Gradle and release tooling."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def read_properties(path: Path) -> dict[str, str]:
    """Read our deliberately small key=value subset of Java properties."""
    properties = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if not re.fullmatch(r"[a-z_]+=[^\s\\]+", line):
            raise ValueError(f"{path}:{number}: expected a plain key=value declaration")
        key, value = line.split("=", 1)
        if key in properties:
            raise ValueError(f"{path}:{number}: duplicate {key}")
        properties[key] = value
    return properties


@dataclass(frozen=True)
class Target:
    minecraft: str
    java_version: int
    loader_version: str
    loader_min_version: str
    mapping_strategy: str
    source_strategy: str
    screen_event: str
    fabric_api_version: str
    yacl_dependency: str
    yacl_min_version: str
    modmenu_dependency: str


def load_target(path: Path) -> Target:
    values = read_properties(path)
    required = set(Target.__dataclass_fields__) - {"minecraft"}

    def require(condition: bool, message: str) -> None:
        if not condition:
            raise ValueError(f"{path}: {message}")

    require(re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,2}", path.stem) is not None,
            "invalid Minecraft target filename")
    require(values.keys() == required,
            f"missing keys {sorted(required - values.keys())}; unknown keys {sorted(values.keys() - required)}")
    require(values["java_version"].isdigit() and int(values["java_version"]) > 0,
            "java_version must be a positive integer")
    versions = {}
    for key in ("loader_version", "loader_min_version", "yacl_min_version"):
        require(re.fullmatch(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?", values[key]) is not None,
                f"{key} must be a release version")
        versions[key] = tuple((values[key] + ".0").split(".")[:3])
    require(tuple(map(int, versions["loader_version"])) >= tuple(map(int, versions["loader_min_version"])),
            "tested loader_version is below loader_min_version")
    for key, choices in {
        "mapping_strategy": {"official", "intermediary"},
        "source_strategy": {"native", "legacy_render", "legacy_render_1_21_10"},
        "screen_event": {"afterRender", "afterExtract", "afterForeground"},
    }.items():
        require(values[key] in choices, f"unknown {key}: {values[key]}")
    for key in ("fabric_api_version", "yacl_dependency", "modmenu_dependency"):
        value = values[key]
        pattern = r"[A-Za-z0-9_.+-]+" if key == "fabric_api_version" else r"[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+"
        version = value.rsplit(":", 1)[-1]
        require(re.fullmatch(pattern, value) is not None
                and not version.startswith("latest.") and not version.endswith(("+", "-SNAPSHOT")),
                f"{key} must pin a release dependency")
    return Target(minecraft=path.stem, **(values | {"java_version": int(values["java_version"])}))


def discover_targets(root: Path = ROOT) -> list[Target]:
    targets = [load_target(path) for path in sorted((root / "gradle/targets").glob("*.properties"))]
    if not targets:
        raise ValueError(f"{root / 'gradle/targets'}: no target profiles")
    return targets


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("command", choices=("matrix", "count", "list"))
    args = parser.parse_args()
    try:
        versions = [target.minecraft for target in discover_targets(args.root)]
    except (ValueError, OSError) as error:
        parser.error(str(error))
    if args.command == "matrix":
        print(json.dumps({"minecraft": versions}, separators=(",", ":")))
    elif args.command == "count":
        print(len(versions))
    else:
        print("\n".join(versions))


if __name__ == "__main__":
    main()
