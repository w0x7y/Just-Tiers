"""Preview Modrinth listing updates (copy, gallery, release type); use --apply to publish them explicitly."""

import argparse
import json
import os
import re
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[1]


USER_AGENT = "w0x7y/Just-Tiers (https://github.com/w0x7y/Just-Tiers)"


def _properties(root: Path = ROOT) -> dict[str, str]:
    return dict(
        line.split("=", 1) for raw in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if (line := raw.strip()) and not line.startswith("#")
    )


def project_update(root: Path = ROOT) -> tuple[str, dict[str, str]]:
    properties = _properties(root)
    project_id = properties["modrinth_id"]
    if not project_id.isalnum():
        raise ValueError("modrinth_id must be a project ID or alphanumeric slug")
    metadata = json.loads((root / "Modrinth/project.json").read_text(encoding="utf-8"))
    if not isinstance(metadata, dict) or "description" not in metadata or not set(metadata) <= {"description", "issues_url"}:
        raise ValueError("Modrinth/project.json must contain the project description and optionally issues_url")
    description = metadata["description"]
    if not isinstance(description, str) or not 3 <= len(description) <= 255:
        raise ValueError("The Modrinth project description must be 3 to 255 characters")
    body = (root / "Modrinth/description.md").read_text(encoding="utf-8")
    if not body.strip():
        raise ValueError("The Modrinth project body must not be empty")
    payload = {"description": description, "body": body}
    if "issues_url" in metadata:
        issues = metadata["issues_url"]
        if not isinstance(issues, str) or urllib.parse.urlsplit(issues).scheme != "https" or not urllib.parse.urlsplit(issues).netloc:
            raise ValueError("The Modrinth issues_url must be an https URL")
        payload["issues_url"] = issues
    return project_id, payload


IMAGE_TYPES = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp", "gif": "image/gif"}
API = "https://api.modrinth.com/v2"


def gallery_images(root: Path = ROOT) -> list[dict]:
    """The gallery as it should be: image files in the repository with their captions, in display order."""
    gallery = json.loads((root / "Modrinth/gallery.json").read_text(encoding="utf-8"))
    if not isinstance(gallery, list) or not gallery:
        raise ValueError("Modrinth/gallery.json must list the gallery images")
    titles, featured = set(), 0
    for item in gallery:
        if not isinstance(item, dict) or not {"file", "title", "description"} <= set(item) <= {"file", "title", "description", "featured"}:
            raise ValueError("Gallery images need file, title and description, and may set featured")
        if not all(isinstance(item[key], str) and item[key].strip() for key in ("file", "title", "description")):
            raise ValueError("Gallery file, title and description must be nonempty strings")
        if len(item["title"]) > 255 or len(item["description"]) > 2048:
            raise ValueError("Gallery title or description is too long")
        path = (root / item["file"]).resolve()
        if (root / "Modrinth/gallery").resolve() not in path.parents or not path.is_file():
            raise ValueError(f"Gallery file must exist under Modrinth/gallery: {item['file']}")
        if path.suffix.lower().lstrip(".") not in IMAGE_TYPES or path.stat().st_size > 5 * 1024 * 1024:
            raise ValueError(f"Gallery file must be a PNG, JPEG, WebP or GIF of at most 5 MiB: {item['file']}")
        if item["title"] in titles:
            raise ValueError("Gallery titles must be distinct; they identify uploaded images")
        titles.add(item["title"])
        featured += item.get("featured", False) is True
        if not isinstance(item.get("featured", False), bool):
            raise ValueError("Gallery featured must be true or false")
    if featured > 1:
        raise ValueError("At most one gallery image can be featured")
    return gallery


def gallery_plan(current: list[dict], desired: list[dict]) -> dict[str, list]:
    """Match live images to the wanted ones by title: delete the rest, upload missing ones, fix captions."""
    wanted = {item["title"]: (index, item) for index, item in enumerate(desired)}
    live = {image.get("title"): image for image in current}
    plan = {"delete": [image["url"] for image in current if image.get("title") not in wanted], "upload": [], "update": []}
    for title, (index, item) in wanted.items():
        fields = {"title": title, "description": item["description"], "featured": item.get("featured", False), "ordering": index}
        if title not in live:
            plan["upload"].append({"file": item["file"], **fields})
        elif any(live[title].get(key) != value for key, value in fields.items()):
            plan["update"].append({"url": live[title]["url"], **fields})
    return plan


def release_plan(versions: list[dict], mod_version: str, release_type: str) -> list[dict]:
    """Promote the current version's builds to release_type; earlier versions and demotions are left alone."""
    if release_type != "release":
        return []
    plan = []
    for version in versions:
        if not version["version_number"].startswith(mod_version + "+") or version["version_type"] == "release":
            continue
        changelog = re.sub(r"\A(## Just-Tiers \S+) (?:alpha|beta)$", r"\1", version.get("changelog") or "", count=1, flags=re.M)
        plan.append({"id": version["id"], "version_type": "release", "changelog": changelog})
    return plan


def _get(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def _send(method: str, url: str, token: str, data: bytes | None = None, content_type: str = "application/json") -> None:
    if not token.strip():
        raise ValueError("Set MODRINTH_TOKEN with PROJECT_WRITE permission to apply the update")
    request = urllib.request.Request(url, data=data, method=method,
                                     headers={"Authorization": token, "Content-Type": content_type, "User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status != 204:
            raise ValueError(f"Unexpected Modrinth response: HTTP {response.status}")


def publish_gallery(project_id: str, plan: dict[str, list], token: str, root: Path = ROOT) -> None:
    gallery = f"{API}/project/{project_id}/gallery"
    for url in plan["delete"]:
        _send("DELETE", f"{gallery}?{urllib.parse.urlencode({'url': url})}", token)
    for item in plan["upload"]:
        ext = Path(item["file"]).suffix.lower().lstrip(".")
        query = urllib.parse.urlencode({"ext": ext, "featured": str(item["featured"]).lower(), "title": item["title"],
                                        "description": item["description"], "ordering": item["ordering"]})
        _send("POST", f"{gallery}?{query}", token, (root / item["file"]).read_bytes(), IMAGE_TYPES[ext])
    for item in plan["update"]:
        query = urllib.parse.urlencode({"url": item["url"], "featured": str(item["featured"]).lower(), "title": item["title"],
                                        "description": item["description"], "ordering": item["ordering"]})
        _send("PATCH", f"{gallery}?{query}", token)


def publish_release(plan: list[dict], token: str) -> None:
    for item in plan:
        body = json.dumps({"version_type": item["version_type"], "changelog": item["changelog"]}).encode("utf-8")
        _send("PATCH", f"{API}/version/{item['id']}", token, body)


def publish(project_id: str, payload: dict[str, str], token: str) -> None:
    _send("PATCH", f"{API}/project/{project_id}", token, json.dumps(payload).encode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Update the live Modrinth page")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--gallery", action="store_true", help="Preview or apply the gallery images instead of the project copy")
    mode.add_argument("--release-type", action="store_true",
                      help="Preview or apply release_type to the current version's builds")
    args = parser.parse_args()
    try:
        project_id, payload = project_update()
        token = os.environ.get("MODRINTH_TOKEN", "")
        if args.gallery:
            plan = gallery_plan(_get(f"{API}/project/{project_id}").get("gallery", []), gallery_images())
            if args.apply:
                publish_gallery(project_id, plan, token)
            print(json.dumps(plan, ensure_ascii=False, indent=2))
        elif args.release_type:
            properties = _properties()
            plan = release_plan(_get(f"{API}/project/{project_id}/version"), properties["mod_version"], properties["release_type"])
            if args.apply:
                publish_release(plan, token)
            print(json.dumps([{"id": item["id"], "version_type": item["version_type"]} for item in plan], indent=2))
        elif args.apply:
            publish(project_id, payload, token)
            print(f"Updated Modrinth project {project_id}: {', '.join(payload)}")
        else:
            print(json.dumps(payload, ensure_ascii=False, indent=2))
    except urllib.error.HTTPError as error:
        parser.exit(1, f"Modrinth rejected the update: HTTP {error.code}\n")
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Project update failed: {error}\n")


if __name__ == "__main__":
    main()
