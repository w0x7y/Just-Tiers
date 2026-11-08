"""Check the boundary between local project copy and an explicit live update."""

import json
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.parse
from unittest.mock import patch

from test_minecraft_targets import ROOT

sys.path.insert(0, str(ROOT / "tools"))
from modrinth_project import gallery_images, gallery_plan, project_update, publish, publish_gallery, release_plan


class ModrinthProjectTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        (self.root / "Modrinth").mkdir()
        (self.root / "gradle.properties").write_text("org.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8\nmodrinth_id=8zkz6d1C\n")
        self.metadata = self.root / "Modrinth/project.json"
        self.metadata.write_text(json.dumps({"description": "Four independently selectable tier sites"}))
        self.body = self.root / "Modrinth/description.md"
        self.body.write_text("# Just-Tiers\n\nPvPTiers and PvPHQ.\n")

    def test_preview_contains_exact_copy_and_only_requested_project_fields(self):
        project_id, payload = project_update(self.root)
        self.assertEqual("8zkz6d1C", project_id)
        self.assertEqual({"description", "body"}, payload.keys())
        self.assertEqual(self.body.read_text(), payload["body"])
        self.assertEqual("Four independently selectable tier sites", payload["description"])

    def test_issues_url_is_sent_only_when_listed_and_must_be_https(self):
        issues = "https://github.com/w0x7y/Just-Tiers/issues"
        self.metadata.write_text(json.dumps({"description": "Valid copy", "issues_url": issues}))
        self.assertEqual(issues, project_update(self.root)[1]["issues_url"])
        for url in ("http://github.com/w0x7y/Just-Tiers/issues", "github.com/issues", "https://", 7):
            with self.subTest(url=url):
                self.metadata.write_text(json.dumps({"description": "Valid copy", "issues_url": url}))
                with self.assertRaises(ValueError):
                    project_update(self.root)

    def test_invalid_copy_is_rejected_before_publication(self):
        for metadata in ({"description": "x"}, {"description": "x" * 256},
                         {"description": "Valid copy", "status": "archived"}, {"issues_url": "https://example.com"}, []):
            with self.subTest(metadata=metadata):
                self.metadata.write_text(json.dumps(metadata))
                with self.assertRaises(ValueError):
                    project_update(self.root)
        self.metadata.write_text(json.dumps({"description": "Valid copy"}))
        self.body.write_text(" \n")
        with self.assertRaises(ValueError):
            project_update(self.root)

    def test_missing_token_never_attempts_a_network_update(self):
        project_id, payload = project_update(self.root)
        with patch("modrinth_project.urllib.request.urlopen") as network:
            with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
                publish(project_id, payload, " ")
            network.assert_not_called()

    def test_explicit_update_sends_one_patch_with_the_previewed_content(self):
        project_id, payload = project_update(self.root)
        with patch("modrinth_project.urllib.request.urlopen") as network:
            network.return_value.__enter__.return_value.status = 204
            publish(project_id, payload, "test-token")
            network.assert_called_once()
            request = network.call_args.args[0]
            self.assertEqual(f"https://api.modrinth.com/v2/project/{project_id}", request.full_url)
            self.assertEqual("PATCH", request.method)
            self.assertEqual(payload, json.loads(request.data))

    def write_gallery(self, images, files=("a.jpg", "b.png")):
        (self.root / "Modrinth/gallery").mkdir(exist_ok=True)
        for name in files:
            (self.root / "Modrinth/gallery" / name).write_bytes(b"image")
        (self.root / "Modrinth/gallery.json").write_text(json.dumps(images))

    def test_gallery_rejects_missing_files_duplicate_titles_and_unknown_fields(self):
        a = {"file": "Modrinth/gallery/a.jpg", "title": "Nametags", "description": "Four sites"}
        b = {"file": "Modrinth/gallery/b.png", "title": "Lookup", "description": "Every gamemode"}
        for images in ([a | {"file": "Modrinth/gallery/missing.jpg"}], [a | {"file": "gradle.properties"}],
                       [a, a | {"file": "Modrinth/gallery/b.png"}], [a | {"featured": True}, b | {"featured": True}],
                       [a | {"url": "https://example.com"}], [a | {"featured": "yes"}], []):
            with self.subTest(images=images):
                self.write_gallery(images)
                with self.assertRaises(ValueError):
                    gallery_images(self.root)
        self.write_gallery([a | {"featured": True}, b])
        self.assertEqual(2, len(gallery_images(self.root)))

    def test_gallery_plan_matches_by_title(self):
        desired = [{"file": "Modrinth/gallery/a.jpg", "title": "Nametags", "description": "New caption", "featured": True},
                   {"file": "Modrinth/gallery/b.png", "title": "Lookup", "description": "Every gamemode"}]
        current = [{"url": "https://cdn/old.jpeg", "title": "Banner", "description": "Earlier release", "featured": True, "ordering": 0},
                   {"url": "https://cdn/n.jpeg", "title": "Nametags", "description": "Old caption", "featured": False, "ordering": 1}]
        plan = gallery_plan(current, desired)
        self.assertEqual(["https://cdn/old.jpeg"], plan["delete"])
        self.assertEqual([{"file": "Modrinth/gallery/b.png", "title": "Lookup", "description": "Every gamemode",
                           "featured": False, "ordering": 1}], plan["upload"])
        self.assertEqual([{"url": "https://cdn/n.jpeg", "title": "Nametags", "description": "New caption",
                           "featured": True, "ordering": 0}], plan["update"])
        settled = [{"url": "u1", "title": "Nametags", "description": "New caption", "featured": True, "ordering": 0},
                   {"url": "u2", "title": "Lookup", "description": "Every gamemode", "featured": False, "ordering": 1}]
        self.assertEqual({"delete": [], "upload": [], "update": []}, gallery_plan(settled, desired))

    def test_gallery_publish_deletes_then_uploads_bytes_then_patches(self):
        self.write_gallery([])
        plan = {"delete": ["https://cdn/old.jpeg"],
                "upload": [{"file": "Modrinth/gallery/a.jpg", "title": "Nametags", "description": "A & B", "featured": True, "ordering": 0}],
                "update": [{"url": "https://cdn/n.jpeg", "title": "Lookup", "description": "C", "featured": False, "ordering": 1}]}
        with patch("modrinth_project.urllib.request.urlopen") as network:
            network.return_value.__enter__.return_value.status = 204
            publish_gallery("8zkz6d1C", plan, "test-token", self.root)
            requests = [call.args[0] for call in network.call_args_list]
        self.assertEqual(["DELETE", "POST", "PATCH"], [r.method for r in requests])
        upload = urllib.parse.urlparse(requests[1].full_url)
        self.assertEqual("/v2/project/8zkz6d1C/gallery", upload.path)
        self.assertEqual({"ext": ["jpg"], "featured": ["true"], "title": ["Nametags"], "description": ["A & B"], "ordering": ["0"]},
                         urllib.parse.parse_qs(upload.query))
        self.assertEqual(b"image", requests[1].data)
        self.assertEqual("image/jpeg", requests[1].get_header("Content-type"))
        self.assertEqual(["https://cdn/n.jpeg"], urllib.parse.parse_qs(urllib.parse.urlparse(requests[2].full_url).query)["url"])

    def test_gallery_publish_needs_a_token_before_any_request(self):
        with patch("modrinth_project.urllib.request.urlopen") as network:
            with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
                publish_gallery("8zkz6d1C", {"delete": ["u"], "upload": [], "update": []}, "")
            network.assert_not_called()

    def test_release_plan_promotes_only_the_current_version(self):
        versions = [{"id": "a", "version_number": "1.2.1+mc26.2", "version_type": "beta",
                     "changelog": "## Just-Tiers 1.2.1 beta\n\nFixes. Not beta software anymore."},
                    {"id": "b", "version_number": "1.2.1+mc26.3", "version_type": "release", "changelog": "## Just-Tiers 1.2.1"},
                    {"id": "c", "version_number": "1.2.0+mc26.2", "version_type": "beta", "changelog": "## Just-Tiers 1.2.0 beta"},
                    {"id": "d", "version_number": "1.2.10+mc26.2", "version_type": "beta", "changelog": ""}]
        self.assertEqual([{"id": "a", "version_type": "release", "changelog": "## Just-Tiers 1.2.1\n\nFixes. Not beta software anymore."}],
                         release_plan(versions, "1.2.1", "release"))
        self.assertEqual([], release_plan(versions, "1.2.1", "beta"))


if __name__ == "__main__":
    unittest.main()
