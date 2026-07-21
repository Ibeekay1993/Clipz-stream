# Fix Gradle Build Error: Task 'kotlinCompilerPluginClasspath' not found

The project is using a very new version of Android Gradle Plugin (AGP 9.3.0) and Gradle (9.7.0-rc-1). The error `Task 'kotlinCompilerPluginClasspath' not found` and the subsequent `AbstractMethodError` related to the Compose Compiler are likely caused by the experimental "Built-in Kotlin" support in AGP 9.0+ being enabled while still applying the standard `kotlin-android` plugin.

## Proposed Changes

### [Component Name] Gradle Configuration

#### [MODIFY] [gradle.properties](file:///C:/Users/WINDOWS/Documents/antigravity/magical-faraday/Clipz-stream/gradle.properties)
- Disable `android.builtInKotlin` to revert to the standard Kotlin Gradle Plugin behavior. AGP 9.0 enables this by default, which can conflict with the `org.jetbrains.kotlin.android` plugin.
- Ensure `android.newDsl=false` is maintained if the project is not yet migrated to the new AGP 9.0 DSL.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify that the project builds successfully without the "Task not found" or "Compose Compiler incompatibility" errors.

### Manual Verification
- Verify that the IDE (Android Studio) no longer shows the "Selection failed" error when building or syncing.
