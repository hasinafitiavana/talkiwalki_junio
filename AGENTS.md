# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application built with Kotlin and Jetpack Compose.

- `app/src/main/java/com/talkiwalki/junior/`: application source; `MainActivity.kt` is the entry point.
- `app/src/main/java/com/talkiwalki/junior/ui/theme/`: Compose color, typography, and theme definitions.
- `app/src/main/res/`: Android resources, including strings, themes, drawables, launcher icons, and XML configuration.
- `app/src/test/`: local JVM unit tests.
- `app/src/androidTest/`: device/emulator instrumentation and Compose UI tests.
- `gradle/libs.versions.toml`: centralized plugin and dependency versions.

Keep new UI features in focused packages beneath `com.talkiwalki.junior`; place reusable visual tokens in `ui/theme` rather than duplicating them in screens.

## Build, Test, and Development Commands

Run commands from the repository root:

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew test` runs local JVM tests in `app/src/test`.
- `./gradlew connectedAndroidTest` runs instrumentation tests on a connected emulator or device.
- `./gradlew lint` runs Android Lint checks.
- `./gradlew clean` removes generated build outputs when a clean build is needed.

Use `gradlew.bat` instead of `./gradlew` on Windows.

## Coding Style & Naming Conventions

Follow idiomatic Kotlin and existing four-space indentation. Keep package names lowercase and aligned with the directory tree. Use `PascalCase` for classes, composables, and files containing their primary type; use `camelCase` for functions, properties, and parameters. Name Compose previews with a `Preview` suffix, such as `GreetingPreview`. Prefer immutable `val` values and small composables with explicit state and modifiers.

No standalone formatter is configured; use Android Studio’s Kotlin formatter and resolve Android Lint findings before review.

## Testing Guidelines

Use JUnit 4 for local tests and AndroidX JUnit/Espresso or Compose test APIs for instrumentation tests. Name test classes after the subject (for example, `GreetingTest`) and test methods descriptively, such as `greeting_displaysProvidedName`. Add local tests for pure logic and instrumentation tests for Android framework or UI behavior. Run the relevant Gradle test task before submitting changes.

## Commit & Pull Request Guidelines

The current history starts with `Initial commit: Android TalkiWalki Junior`; no stricter convention is established. Write concise, imperative commit subjects, for example `Add child profile selector`. Keep commits narrowly scoped. Pull requests should explain the user-visible change, list validation performed, link related issues when available, and include screenshots or recordings for Compose UI changes.

## Configuration & Security

Do not commit keystores, API keys, device-specific configuration, or generated `build/` output. Keep dependency updates in `gradle/libs.versions.toml` coordinated with Gradle build files.
