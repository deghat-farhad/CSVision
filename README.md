# CSVision

A Kotlin Android app that downloads CSV issue records and displays names, issue counts, and birth dates.

## Run

Open in Android Studio, sync Gradle, and run `app` on an API 24+ device or emulator. Android SDK 37 is required; the Gradle daemon is configured to use JDK 25.

Tap **Load issues** to download the data. **Reload** and **Retry** start a fresh import.

## Build and test

```sh
./gradlew :app:assembleDebug :core:csv:test :feature:issues:testDebugUnitTest lint
```

With a device or emulator connected:

```sh
./gradlew :feature:issues:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Tests cover CSV parsing, typed conversion, import/cancellation behavior, database paging, loading/retry UI, and scrolling across page eviction.

## Design

- `core:csv`: custom streaming CSV parser, independent of Android.
- `feature:issues`: data/domain/presentation packages with a Compose UI and ViewModel.
- `app`: entry point and Hilt wiring.

OkHttp streams records into bounded Room batches; Paging loads database rows for the UI. This avoids keeping the whole file or dataset in memory. CSV order and duplicate records are preserved.

## CSV input

Expected columns, in order:

```csv
First name,Sur name,Issue count,Date of birth,avatar
```

Issue counts become `Int` values; ISO local timestamps such as `1978-01-02T00:00:00` become `LocalDate` values. Blank records are skipped. Invalid field counts, integers, or dates are rejected; header or CSV syntax errors stop the import. If an import fails, committed rows remain visible with an incomplete-data warning.

To test another file with the same schema, change the source URL in [IssuesModule.kt](feature/issues/src/main/kotlin/deghat/farhad/csvision/feature/issues/di/IssuesModule.kt).
