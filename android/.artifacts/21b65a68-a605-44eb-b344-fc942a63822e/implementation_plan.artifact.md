# Upgrade Kotlin, AGP, and Gradle to Resolve Compatibility Issues

The previous attempt to upgrade Kotlin to 2.4.10 while keeping AGP at 8.13.2 failed because the latest libraries require `compileSdk 37` and AGP 9.1.0+.

We will now upgrade all build tools to their latest stable versions.

## User Review Required

> [!IMPORTANT]
> This is a comprehensive upgrade of the entire build stack:
> - Kotlin 2.0.20 -> 2.4.10
> - AGP 8.13.2 -> 9.3.0
> - Gradle 8.13 -> 9.3.1
> - compileSdk 34 -> 37
>
> This may require further adjustments if there are breaking changes in AGP 9.x.

## Proposed Changes

### Build Configuration

#### [MODIFY] [root build.gradle.kts](file:///C:/Users/sarva/Desktop/nf/code/Lphotos/gallery/android/build.gradle.kts)
- Upgrade `com.android.application` to `9.3.0`.
- Upgrade `org.jetbrains.kotlin.android` to `2.4.10`.
- Upgrade `org.jetbrains.kotlin.plugin.compose` to `2.4.10`.

#### [MODIFY] [app build.gradle.kts](file:///C:/Users/sarva/Desktop/nf/code/Lphotos/gallery/android/app/build.gradle.kts)
- Set `compileSdk = 37`.
- Upgrade `androidx.compose:compose-bom` to `2026.06.01`.
- Upgrade `androidx.core:core-ktx` to `1.19.0`.
- Upgrade `androidx.activity:activity-compose` to `1.13.0`.
- Upgrade `androidx.lifecycle:lifecycle-runtime-ktx` to `2.11.0`.

#### [MODIFY] [gradle-wrapper.properties](file:///C:/Users/sarva/Desktop/nf/code/Lphotos/gallery/android/gradle/wrapper/gradle-wrapper.properties)
- Upgrade `distributionUrl` to `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify that the project compiles successfully.

### Manual Verification
- Perform a Gradle Sync in Android Studio.
