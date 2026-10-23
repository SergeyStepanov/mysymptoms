# Contributing

Thank you for your interest in improving Symptom Journal. Bug reports, ideas, documentation updates, translations, and code contributions are welcome.

## Before you contribute

- Check the existing GitHub issues and pull requests to see whether the topic is already being discussed.
- For a substantial change, open an issue first so the approach can be discussed before you invest time in implementation.
- Please do not include real medical records, personal health information, passwords, signing keys, or other private data in issues, pull requests, screenshots, or test fixtures.
- This project is a symptom-tracking journal, not a diagnostic or treatment tool. Contributions should not present it as a substitute for professional medical care.

## Reporting bugs and suggesting improvements

Open a GitHub issue and include:

- A clear description of the problem or proposed improvement.
- Steps to reproduce the problem, if applicable.
- Expected and actual behavior.
- Android version and device model, if relevant.
- Sanitized logs or screenshots, after checking that they contain no personal or medical information.

Use GitHub's security reporting process for suspected security vulnerabilities. Do not publish exploit details or private user data in a public issue.

## Submitting code

1. Fork the repository on GitHub and create a branch for your change.
2. Keep the change focused and follow the existing Kotlin, Jetpack Compose, and Android resource patterns.
3. Add or update tests for behavior changes where practical.
4. Run the relevant checks:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleDebug
   ```

   If you have an Android device or emulator available, also run:

   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```

5. Open a pull request against the project's default branch. Explain what changed, why it is needed, how you tested it, and note any user-visible behavior changes.

Please do not include generated build output, local IDE settings, signing credentials, or private data in a pull request.

## Licensing and attribution

The project's first-party code, translations, documentation, and original artwork are offered under the GNU Affero General Public License, version 3 only (AGPL-3.0-only). By submitting a contribution to this repository, you agree that your contribution may be distributed as part of the project under that license.

You retain copyright in your contribution; this project does not require you to transfer copyright or sign a Contributor License Agreement. Please only submit work that you created or are authorized to contribute. If your contribution includes third-party code, fonts, images, or other assets, identify their source and license in the pull request and include any required notices. Do not assume that an asset is compatible based only on being publicly available.

Contributors may be acknowledged in the Git history and release notes. They do not need to be added to the app's user-facing description for every contribution.
