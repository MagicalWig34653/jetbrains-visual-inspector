# Third-party notices

This project is licensed under the MIT License (see `LICENSE`), except for the parts listed here,
which remain under their own licenses.

## IntelliJ Platform Plugin Template

- Source: <https://github.com/JetBrains/intellij-platform-plugin-template>
- Commit: `c7c9f997a7e67920efc534202e7ec29684d3b161`
- License: Apache License 2.0, full text in `LICENSES/Apache-2.0.txt`

Copied unchanged: `plugin/gradlew`, `plugin/gradlew.bat`, `plugin/gradle/wrapper/gradle-wrapper.jar`,
`plugin/gradle/wrapper/gradle-wrapper.properties`. The Gradle wrapper files originate from the
Gradle project, also under the Apache License 2.0.

Modified: `plugin/settings.gradle.kts`, `plugin/build.gradle.kts` and `plugin/gradle.properties` were
derived from the template and changed for this project: project name and group, removal of the
changelog plugin, WebStorm as the target, Java toolchain, `since-build`, and Plugin Verifier targets.

The template's sample code, images, workflows and documentation are not included.

## Superpowers skills

- Source: <https://github.com/obra/superpowers>
- Version 7.0.2, commit `22f0b463e7a5e579904dd6211a520321035526f7`
- License: MIT, Copyright (c) 2025 Jesse Vincent; the license text is in each skill directory

Included, with the changes listed in `docs/skills.md`: `.claude/skills/systematic-debugging/`,
`.claude/skills/test-driven-development/` and `.claude/skills/verification-before-completion/`.
