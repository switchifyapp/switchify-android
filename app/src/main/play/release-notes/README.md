# Play Store release notes

`.github/workflows/play-release.yml` uploads these files as the "what's new" copy whenever a non-prerelease GitHub Release is published. The file name matches the Play track being uploaded (`internal.txt` for internal testing).

## Editing

Edit `en-US/internal.txt` **before** running `gh release create vX.Y.Z --generate-notes`. The GitHub release-notes body is for engineers (PR list); this file is for users. Max **500 chars**.

## Adding a locale

Drop another folder under this directory, e.g. `de-DE/internal.txt`. Both files get uploaded.

## Promoting to production

Today the workflow uploads to the `internal` track only. When you promote a build in Play Console, the production release notes can be edited there directly, or — if you want this to flow through CI — add `production.txt` here and change the workflow's `track:` (or add a second upload step).
