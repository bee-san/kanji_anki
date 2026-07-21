# Dependency update automation

Kani uses Renovate for unattended repository dependency update pull requests.

Renovate owns:

- GitHub Actions updates, grouped into one weekly PR and configured to pin action references by digest.
- Gradle wrapper updates, kept separate from library/plugin updates.
- Gradle dependency and plugin updates from `*.gradle.kts`, `gradle.properties`, and the `gradle/libs.versions.toml` version catalog.

Dependabot version-update PRs are intentionally not configured. Running Dependabot and Renovate against the same Gradle and GitHub Actions files would create duplicate update PRs. Dependabot security alerts can still be enabled in repository settings without adding `.github/dependabot.yml` version-update schedules.

Noise controls:

- Renovate targets `main` only.
- Update PR creation is scheduled before 06:00 on Mondays.
- Non-major Gradle dependency/plugin updates are grouped.
- GitHub Actions updates are grouped.
- Automerge is disabled; dependency PRs must pass CI and be reviewed or merged by the normal repo policy.

Pull-request safety gates live in `.github/workflows/android-ci.yml`: Gradle wrapper validation blocks on wrapper-integrity failures, while dependency review reports new dependency risk when GitHub Dependency graph is enabled for the repository. The dependency review step is intentionally non-blocking until Dependency graph is enabled in repository security settings; otherwise GitHub fails the action before it can inspect the PR.
