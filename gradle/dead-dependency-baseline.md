# Dead-dependency baseline

This B6-M5 baseline compares direct declarations with app source and the
resolved `debugRuntimeClasspath` and `debugAndroidTestRuntimeClasspath` graphs.
It records candidates only; B6-M5 does not remove dependencies.

## Test-tooling refresh

| Catalog version | Before | After | Scope |
|---|---:|---:|---|
| `androidxTestExt` | `1.2.1` | `1.3.0` | `androidTestImplementation` only |
| `espresso` | `3.6.1` | `3.7.0` | `androidTestImplementation` only |

JUnit 4, Compose test, coroutines test, serialization JSON, Media3, and the
TVHeadend SDK test fixture remain aligned with their existing production or
platform versions.

## Confirmed candidates

Each row is independently removable and must be handled by a separate
successor. An empty unique closure means the candidate adds no resolved module
that another direct root does not already provide.

| Direct declaration | Source evidence | Resolved-graph evidence |
|---|---|---|
| `implementation(libs.androidx.compose.ui.tooling.preview)` | No `@Preview` or `androidx.compose.ui.tooling` usage under `app/src`. | Resolves `ui-tooling-preview:1.11.4`; unique closure is empty on `debugRuntimeClasspath`. |
| `debugImplementation(libs.androidx.compose.ui.tooling)` | No tooling API usage under `app/src`; the debug manifest only replaces the application class. | Resolves `ui-tooling:1.11.4`; unique closure is empty on `debugRuntimeClasspath`. |
| `implementation(libs.androidx.lifecycle.viewmodel.compose)` | No `androidx.lifecycle.viewmodel.compose` usage; activity ViewModel injection is owned by Koin. | Resolves `lifecycle-viewmodel-compose:2.11.0`; unique closure is empty on `debugRuntimeClasspath`. |
| `implementation(libs.coil.network.okhttp)` | The app maps artwork to the SDK's HTSP fetcher, and `IconResolver.kt` rejects raw HTTP(S) artwork. | Its unique closure is `coil-network-okhttp:3.5.0`, `coil-network-okhttp-android:3.5.0`, `coil-network-core:3.5.0`, `coil-network-core-android:3.5.0`, and `okhttp:4.12.0`. |
| `androidTestImplementation(libs.tvheadend.sdk.testing)` | `sdk.testing` fixtures are used under `app/src/test` but not under `app/src/androidTest`. | Its only unique component on `debugAndroidTestRuntimeClasspath` is `sdk-testing:0.4.0`. |

## Retained no-import declarations

These declarations are not candidates despite lacking a matching Kotlin
import:

- `material` supplies the `Theme.Material3.Dark.NoActionBar` XML parent.
- Compose BOM declarations align Compose artifacts in each configuration.
- `ui-test-manifest` supplies the debug manifest used by Compose UI tests.
- `kotlinx-coroutines-android` supplies the Android main dispatcher at runtime.
- Released SDK source configurations feed `syncReleasedSdkEvidence` and are not
  app classpaths.

The remaining direct dependencies have source, XML, test, or explicit tooling
consumers. Candidate successors must prove their own focused build and test
gates after removing exactly one declaration.
