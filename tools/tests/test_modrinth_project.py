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
from modrinth_project import gallery_update, project_update, publish, publish_gallery


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

    def test_invalid_copy_is_rejected_before_publication(self):
        for metadata in ({"description": "x"}, {"description": "x" * 256},
                         {"description": "Valid copy", "status": "archived"}, []):
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

    def test_gallery_rejects_other_projects_duplicates_and_unrequested_fields(self):
        image = {"url": "https://cdn.modrinth.com/data/8zkz6d1C/images/example.jpeg",
                 "title": "Nametag showcase", "description": "An earlier release"}
        for images in ([image | {"url": "https://example.com/unrelated.png"}],
                       [image, image], [image | {"featured": True}], []):
            with self.subTest(images=images):
                (self.root / "Modrinth/gallery.json").write_text(json.dumps(images))
                with self.assertRaises(ValueError):
                    gallery_update("8zkz6d1C", self.root)

    def test_gallery_patch_encodes_captions_as_query_parameters(self):
        image = {"url": "https://cdn.modrinth.com/data/8zkz6d1C/images/example.jpeg",
                 "title": "Nametag showcase", "description": "Toggles replace All mode & site modes"}
        (self.root / "Modrinth/gallery.json").write_text(json.dumps([image]))
        images = gallery_update("8zkz6d1C", self.root)
        with patch("modrinth_project.urllib.request.urlopen") as network:
            network.return_value.__enter__.return_value.status = 204
            publish_gallery("8zkz6d1C", images, "test-token")
            network.assert_called_once()
            request = network.call_args.args[0]
            url = urllib.parse.urlparse(request.full_url)
            self.assertEqual("/v2/project/8zkz6d1C/gallery", url.path)
            self.assertEqual({key: [value] for key, value in image.items()}, urllib.parse.parse_qs(url.query))
            self.assertEqual("PATCH", request.method)
            self.assertIsNone(request.data)


if __name__ == "__main__":
    unittest.main()
