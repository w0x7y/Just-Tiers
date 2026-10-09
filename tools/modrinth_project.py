"""Preview the Modrinth project update; use --apply to publish it explicitly."""

import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[1]


def project_update(root: Path = ROOT) -> tuple[str, dict[str, str]]:
    properties = dict(
        line.split("=", 1) for raw in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if (line := raw.strip()) and not line.startswith("#")
    )
    project_id = properties["modrinth_id"]
    if not project_id.isalnum():
        raise ValueError("modrinth_id must be a project ID or alphanumeric slug")
    metadata = json.loads((root / "Modrinth/project.json").read_text(encoding="utf-8"))
    if not isinstance(metadata, dict) or set(metadata) != {"description"}:
        raise ValueError("Modrinth/project.json must contain only the project description")
    description = metadata["description"]
    if not isinstance(description, str) or not 3 <= len(description) <= 255:
        raise ValueError("The Modrinth project description must be 3 to 255 characters")
    body = (root / "Modrinth/description.md").read_text(encoding="utf-8")
    if not body.strip():
        raise ValueError("The Modrinth project body must not be empty")
    return project_id, {"description": description, "body": body}


def gallery_update(project_id: str, root: Path = ROOT) -> list[dict[str, str]]:
    gallery = json.loads((root / "Modrinth/gallery.json").read_text(encoding="utf-8"))
    if not isinstance(gallery, list) or not gallery:
        raise ValueError("Modrinth/gallery.json must contain gallery caption updates")
    prefix = f"https://cdn.modrinth.com/data/{project_id}/images/"
    seen = set()
    for item in gallery:
        if not isinstance(item, dict) or set(item) != {"url", "title", "description"}:
            raise ValueError("Gallery updates must contain only url, title and description")
        if not all(isinstance(value, str) and value.strip() for value in item.values()):
            raise ValueError("Gallery caption values must be nonempty strings")
        if not item["url"].startswith(prefix) or item["url"] in seen:
            raise ValueError("Gallery URLs must be distinct images belonging to this project")
        if len(item["title"]) > 255 or len(item["description"]) > 2048:
            raise ValueError("Gallery title or description is too long")
        seen.add(item["url"])
    return gallery


def _patch(url: str, data: bytes | None, token: str) -> None:
    if not token.strip():
        raise ValueError("Set MODRINTH_TOKEN with PROJECT_WRITE permission to apply the update")
    request = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": token,
            "Content-Type": "application/json",
            "User-Agent": "w0x7y/Just-Tiers (https://github.com/w0x7y/Just-Tiers)",
        },
        method="PATCH",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 204:
            raise ValueError(f"Unexpected Modrinth response: HTTP {response.status}")


def publish(project_id: str, payload: dict[str, str], token: str) -> None:
    _patch(f"https://api.modrinth.com/v2/project/{project_id}",
           json.dumps(payload).encode("utf-8"), token)


def publish_gallery(project_id: str, gallery: list[dict[str, str]], token: str) -> None:
    for item in gallery:
        query = urllib.parse.urlencode(item)
        _patch(f"https://api.modrinth.com/v2/project/{project_id}/gallery?{query}", None, token)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Update the live Modrinth page")
    parser.add_argument("--gallery", action="store_true", help="Preview or apply gallery captions instead of the project copy")
    args = parser.parse_args()
    try:
        project_id, payload = project_update()
        gallery = gallery_update(project_id) if args.gallery else None
        if args.apply:
            token = os.environ.get("MODRINTH_TOKEN", "")
            if gallery is not None:
                publish_gallery(project_id, gallery, token)
                print(f"Updated Modrinth project {project_id}: {len(gallery)} gallery captions")
            else:
                publish(project_id, payload, token)
                print(f"Updated Modrinth project {project_id}: summary and Markdown body")
        else:
            print(json.dumps(gallery if gallery is not None else payload, ensure_ascii=False, indent=2))
    except urllib.error.HTTPError as error:
        parser.exit(1, f"Modrinth rejected the project update: HTTP {error.code}\n")
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Project update failed: {error}\n")


if __name__ == "__main__":
    main()
