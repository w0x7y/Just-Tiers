# CI and releases

## CI

The [Build workflow](../.github/workflows/build.yml) validates the Gradle wrapper, lints workflows
with actionlint, runs `build` on Linux and Windows for pull requests and pushes to main, and uploads
JARs and test reports from each platform. Reports are kept for 14 days. These checks do not launch
Minecraft.

## Releasing a version

1. Update `mod_version` in `gradle.properties`, and `release_type` if needed.
2. Verify the build and run the [runtime smoke checklist](runtime-smoke-test.md).
3. Add release notes in `docs/releases/v<version>.md`. Without them, the notes come from commits
   since the previous tag.
4. Push a matching `v<version>` tag.

The release workflow rejects a mismatched tag, builds, requires workflow lint, creates the GitHub
release first and then uploads to Modrinth. GitHub gets the mod JAR without the sources JAR.
`release_type=alpha` or `beta` creates a GitHub prerelease and the matching Modrinth version type;
`release` creates a regular GitHub release.

Publishing needs a repository `MODRINTH_TOKEN` secret with permission to create versions. The manual
Modrinth dry-run workflow checks payload construction without uploading. Locally:

```bash
MODRINTH_TOKEN=... ./gradlew modrinth -Pmodrinth_dry_run=true
```

The dry run contacts Modrinth and can't guarantee that a later upload will be accepted. If Modrinth
rejects an upload, the GitHub release stays available. Fix the cause before rerunning; the GitHub step
updates an existing release.

## Updating the Modrinth listing

The listing lives in the repository, separate from releases:

- [Modrinth/description.md](../Modrinth/description.md) is the page body.
- [Modrinth/project.json](../Modrinth/project.json) holds the summary and issues link.
- [Modrinth/gallery.json](../Modrinth/gallery.json) lists the gallery images in `Modrinth/gallery/`.

Run the manual [Sync Modrinth description workflow](../.github/workflows/modrinth-description.yml)
when you want a listing update. It syncs the summary, issues link and body, replaces the gallery with
the listed images, and gives the current version's Modrinth builds the `release_type` from
`gradle.properties`. It promotes, never demotes, and leaves earlier versions alone.

Preview the exact project update locally with `python3 tools/modrinth_project.py`. Add `--gallery`
or `--release-type` to preview those changes. Gallery images are matched by title, so give a replaced
image a new title to upload it again. `./gradlew modrinthSyncBody` overwrites only the live body and
is deliberately absent from release automation.
