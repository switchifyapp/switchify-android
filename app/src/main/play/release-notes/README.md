# Play Store release notes

`.github/workflows/play-release.yml` uploads these files as the "what's new" copy whenever a GitHub Release is published. The file name matches the Play track being uploaded:

- `internal.txt` — uploaded for **every** release (both regular and prerelease).
- `production.txt` — uploaded **only for non-prereleases**, alongside the internal upload.

A prerelease (`gh release create vX.Y.Z-rc.1 --prerelease`) therefore touches `internal.txt` only; a regular release (`gh release create vX.Y.Z`) touches both.

## Editing

Edit both files **before** running `gh release create`. The GitHub release-notes body is for engineers (PR list); these files are user-facing. Max **500 chars** each.

It's fine to keep `internal.txt` and `production.txt` identical, or to write a more candid changelog for internal testers and a polished summary for the store listing — your call.

## Adding a locale

Drop another folder under this directory, e.g. `de-DE/internal.txt` + `de-DE/production.txt`. All matching files get uploaded.

## Staged production rollout

The workflow currently pushes production at `status: completed` (100% of users). To roll out gradually, change the production step in `play-release.yml`:

```yaml
status: inProgress
userFraction: 0.1   # 10% to start; bump in Play Console as you gain confidence
```
