# Contributing to Switchify

Thank you for considering a contribution. Switchify exists to help people with mobility impairments use their devices, so every improvement has a direct human impact.

## Before you start

- **Bug reports and feature requests:** [open an issue](https://github.com/enaboapps/Switchify/issues). Search first to see if it's already tracked.
- **Larger changes:** open an issue describing what you want to do before sending a pull request, so we can agree on direction before you invest time.

## Development setup

See the [README](README.md#setup) for cloning, configuring `local.properties`, and building. All five `local.properties` values are required — the build fails with `Missing config` if any are absent.

## Workflow

1. Fork the repo and create a branch off `main`:
   ```bash
   git checkout -b feature/short-description
   ```
   Use `feature/` for additions, `fix/` for bug fixes, `docs/` for documentation, `chore/` for everything else.

2. Make your changes. One logical change per pull request.

3. Type-check before pushing:
   ```bash
   ./gradlew compileDebugKotlin
   ```
   And run tests if you've touched something covered by them:
   ```bash
   ./gradlew test
   ```

4. Commit using [Conventional Commits](https://www.conventionalcommits.org/) (`type: subject`). The repo ships a commit-msg hook — enable it once with:
   ```bash
   git config core.hooksPath .githooks
   chmod +x .githooks/commit-msg
   ```
   Allowed types: `feat`, `fix`, `docs`, `chore`, `refactor`, `perf`, `test`, `build`, `ci`, `style`, `revert`.

5. Push and open a pull request against `main`. Reference any related issue.

## Code style

- Follow the patterns of neighboring files. Use meaningful, self-documenting names; avoid comments unless the *why* is non-obvious.
- Compose UI: Material 3, reuse existing components, theme via `MaterialTheme.colorScheme`.
- Coroutines: `Default` for CPU work, `IO` for network/disk. Be careful inside the accessibility service — ANRs there break the whole point of the app.
- Never commit secrets (API keys, OAuth credentials, keystores). New telemetry must go through `Logger`, which is opt-in gated; don't bypass it.

For a fuller set of project conventions, see [AGENTS.md](AGENTS.md).

## Pull request review

- Push additional commits to address review feedback. Don't force-push during review — it makes individual comments hard to follow. The PR is squashed on merge, so the intermediate commits get collapsed automatically.
- Keep PRs focused. If a review surfaces a separate problem, file a new issue rather than expanding the PR.

## License

Switchify is licensed under the [GNU Affero General Public License v3.0](LICENSE). By contributing, you agree your contributions are licensed under the same terms (GitHub's "inbound = outbound" policy).

If you're contributing on behalf of an employer, confirm you have permission first.

## Code of Conduct

Switchify is built for users with disabilities. Treat that user community with the dignity it deserves, especially when discussing design decisions, accessibility trade-offs, or use cases. Lived experience matters; listen to it.

This project adopts the [Contributor Covenant v2.1](CODE_OF_CONDUCT.md). Report violations to <owen@switchifyapp.com>.
