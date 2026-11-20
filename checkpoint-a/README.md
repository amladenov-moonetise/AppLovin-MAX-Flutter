# Android Checkpoint A — adapter packaging and class identity

**Research fixture only. NOT a completed or publishable Prebid adapter.**

Scope: packaging, dependency resolution, binary entry-point compatibility, exact class
identity, APK definition counting, and device class loading/construction.
No PBS, response registry, cache handoff, auction coordination, renderer invocation,
MAX initialization, MAX fullscreen objects, or real ads. No dashboard changes.

Everything lives below this directory. The sibling MAX fork plugin is not a dependency
and must remain untouched. No Asterise Core or Casualino application is included.

## Status

Source-level feasibility: YES. Operator reports the official dependency graph,
positive APK definition count and two physical Android 10 tests passed. The original
negative precheck and full probe assembly both succeeded. The positive packaging
check still passes. The negative control remains INCONCLUSIVE until the actual
probe APK definition count is known. Custom artifact-view diagnostics were removed;
a separate APK inspection task now reuses the existing DEX parser.
Codex did not run Gradle, Flutter resolution, tests, Git commands, or device actions.
A successful fixture result is not proof that MAX invoked this class for TestPBS.
That requires a separately authorized mediation experiment.

Protected references supplied by the operator:
- Asterise release/android-ads: 311cde2
- MAX fork release/android-max: 6d31ab6

## Exact pinned evidence

The following cached 3.4.1 sources and POMs were inspected; no newer Prebid source
was substituted. Cache base:
C:/Users/AMlad/.gradle/caches/modules-2/files-2.1/org.prebid/

- prebid-mobile-sdk-max-adapters/3.4.1/86b7c411826c6fb472da4f5dd0871a12aa439391/prebid-mobile-sdk-max-adapters-3.4.1-sources.jar
- prebid-mobile-sdk-max-adapters/3.4.1/f3af7eac61d0499161766f4d9e4e9d7511f17cf8/prebid-mobile-sdk-max-adapters-3.4.1.pom
- prebid-mobile-sdk/3.4.1/68b1b36e50ec67a652f729ff3425877aafb1ae58/prebid-mobile-sdk-3.4.1.pom
- prebid-mobile-sdk-core/3.4.1/d9741a1496249374cb1e83e425536941c1426022/prebid-mobile-sdk-core-3.4.1.pom
- Adapter binary: prebid-mobile-sdk-max-adapters/3.4.1/4e0eac90dc35bcd50de132d92ffbd8c61f6ddcf8/prebid-mobile-sdk-max-adapters-3.4.1.jar

Within the sources JAR:
- com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java:
  public non-final class; extends MediationAdapterBase; implements MaxAdViewAdapter,
  MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter; public
  PrebidMaxMediationAdapter(AppLovinSdk) constructor.
  Public initialize/load/show/onDestroy/version methods; private manager fields
  and setConsents. initialize propagates age restriction to COPPA and completes only
  when PrebidMobile.isSdkInitialized() is true. This fixture does not copy that behavior.
- com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java:
  public class/loadAd/showAd/destroy; private internal load overload and listener setup.
- .../MaxRewardedManager.java: public class/loadAd/showAd/destroy; private internal
  loader/listener setup.
- .../ParametersChecker.java: public class and static response validation/lookup
  methods; private getResponseId.
- Core sources JAR:
  prebid-mobile-sdk-core/3.4.1/9d3f9d676d25b5c80ec09bb38ce5d2c05eeea010/prebid-mobile-sdk-core-3.4.1-sources.jar
  entry org/prebid/mobile/rendering/bidding/display/PluginRendererFactory.java:
  public renderer factory in CORE, not in the replaceable adapter artifact.

The adapter JAR contains the adapter AND its managers, helpers, listener creators,
and native-ad wrapper. Its META-INF/proguard/proguard.pro keeps the exact adapter class.
Removing this artifact does not remove Core's renderer, but a future real replacement
must retain necessary companion adapter classes. This STUB intentionally does not
exercise or replace them.

MAX binary inspected with javap (not a build/test):
C:/Users/AMlad/.gradle/caches/8.13/transforms/cb3c06104496e9d582066692f3d1e526/transformed/applovin-sdk-13.3.1/jars/classes.jar

- com.applovin.impl.mediation.g, private a(String), bytecode offsets 0–20:
  Class.forName, MaxAdapter.isAssignableFrom, asSubclass.
- private a(com.applovin.impl.a3, Class, boolean), offsets 0–33:
  public constructor lookup with AppLovinSdk, newInstance, cast to MediationAdapterBase.
  No Maven artifact/origin check appears in this inspected loader path.
- Public MaxAdapter, MediationAdapterBase, error constants and listener signatures
  were checked against this binary. The instrumentation test uses public class and
  constructor operations only; it does not call this private loader.

Official Flutter source reference already inspected in Investigations 01–04:
AppLovin/AppLovin-MAX-Flutter, release_4_5_1,
applovin_max/android/build.gradle (MAX 13.3.1), and
applovin_max/android/src/main/java/com/applovin/applovin_max/AppLovinMAX.java.
No official package was found in the local hosted pub cache during fixture preparation.
Operator resolution and the graph guard must establish its presence before a PASS.
The fork checkout beside this fixture is not acceptable evidence of official-package compatibility.

## Dependency graph and replacement boundary

Original:
org.prebid:prebid-mobile-sdk-max-adapters:3.4.1 (JAR)
  -> org.prebid:prebid-mobile-sdk:3.4.1 (JAR)
      -> org.prebid:prebid-mobile-sdk-core:3.4.1 (AAR)
  -> com.applovin:applovin-sdk:13.1.0

Core POM dependencies retained:
- org.prebid:prebid-mobile-sdk-open-measurement:1.4.1
- androidx.annotation:annotation:1.9.1
- com.google.android.gms:play-services-base:18.1.0
- com.google.android.gms:play-services-ads-identifier:18.0.1
- androidx.media3:media3-exoplayer:1.3.1
- androidx.media3:media3-ui:1.3.1
- androidx.localbroadcastmanager:localbroadcastmanager:1.0.0

Presence of these libraries is not identifier access, consent, or network authorization.

Fixture:
official pub.dev applovin_max:4.5.1 -> MAX 13.3.1
app -> :replacement -> Prebid SDK 3.4.1 -> Core 3.4.1
app -> strict MAX 13.3.1 and strict Prebid SDK/Core 3.4.1 constraints

The isolated root build excludes ONLY
org.prebid:prebid-mobile-sdk-max-adapters
on every configuration in every fixture project. The replacement is an Android
library project with a different Gradle identity and the ORIGINAL Java class name.
It never depends on the fork, Asterise :asterise-max, or the original adapter JAR.
The graph guard rejects an upstream adapter that survives exclusion.

Existing development graph, read only:
Asterise asterise-max/build.gradle.kts explicitly includes the original adapter and
MAX 13.6.3 (through libs.versions.toml); it cannot be imported into this locked fixture.
flutter/asterise_flutter/android-ads/build.gradle.kts also explicitly includes the
original adapter. The public Flutter Core bridge declares only its Core artifact.
Thus exclusion on just one dependency edge is insufficient in a future host.
No protected build declaration was changed.

Gradle resolves versions of one module; it does not replace an unrelated module
merely because both contain the same Java class. If both adapter definitions enter
the runtime classpath, AGP duplicate-class checking / D8 must fail.
Resource packaging exclusions or pickFirst are not class replacement mechanisms.
Reference: https://developer.android.com/build/dependency-resolution-errors

## Operator preparation — isolated directory only

Use existing JDK 17+ and Gradle 8.13, Android SDK 35, and a supported installed
Flutter SDK. AGP 8.11.1 was already present in the local Gradle cache. Do not upgrade
MAX or Prebid to fix a tooling error. Record tool versions and any failure instead.
No wrapper binary or SDK installation is added by this fixture.

Before running anything, inspect these operator-only Git commands:

    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research rev-parse --show-toplevel
    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research status --short --branch
    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research worktree list
    git -C C:/Users/AMlad/AndroidStudioProjects/asterise rev-parse release/android-ads
    git -C C:/Users/AMlad/AndroidStudioProjects/AppLovin-MAX-Flutter rev-parse release/android-max

Confirm the research checkout is not either protected branch. Do not switch branches.
Record existing modifications without discarding them.

In the IDE create checkpoint-a/android/local.properties with your existing paths.
Use forward slashes, for example:

    flutter.sdk=C:/path/to/existing/flutter
    sdk.dir=C:/Users/AMlad/AppData/Local/Android/Sdk

Use PowerShell and substitute your existing Gradle and Flutter executables:

    $research = 'C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research'
    $fixture = "$research/checkpoint-a"
    $gradle = 'C:/path/to/existing/gradle-8.13/bin/gradle.bat'
    $flutter = 'C:/path/to/existing/flutter/bin/flutter.bat'
    Set-Location $fixture
    & $flutter --version
    & $flutter pub get
    & $gradle -p "$fixture/android" --version

Review pubspec.lock: applovin_max must be hosted at https://pub.dev, exactly 4.5.1,
with its hosted sha256 recorded. No dependency_overrides or local MAX path.
Preserve the generated lockfile as operator evidence. The Gradle guard also requires
the plugin project path to end in /hosted/pub.dev/applovin_max-4.5.1/android.
A hosted directory alone is not a cryptographic audit of cache tampering.
Do not patch plugin sources or use this repository's sibling applovin_max directory.

## Operator positive verification

    & $gradle -p "$fixture/android" :app:verifyCheckpointAGraph
    & $gradle -p "$fixture/android" :app:dependencyInsight --dependency applovin-sdk --configuration debugRuntimeClasspath
    & $gradle -p "$fixture/android" :app:dependencyInsight --dependency prebid-mobile-sdk --configuration debugRuntimeClasspath
    & $gradle -p "$fixture/android" :app:dependencyInsight --dependency prebid-mobile-sdk-max-adapters --configuration debugRuntimeClasspath
    & $gradle -p "$fixture/android" :app:checkDebugDuplicateClasses :app:verifyCheckpointA --stacktrace

Expected:
- CHECKPOINT_A_GRAPH_PASS: hosted official MAX 4.5.1, native MAX 13.3.1,
  Prebid SDK/Core 3.4.1, original adapter artifact absent.
- checkDebugDuplicateClasses and debug APK assembly succeed.
- CHECKPOINT_A_APK_PASS: exactly one class definition across every classes*.dex
  in app/build/outputs/apk/debug/app-debug.apk.

The APK task parses DEX class_def entries, not arbitrary matching strings.
It does not count the separate test APK as an application definition.

Connect an isolated Android device/emulator (API 26+); select one target:

    & $gradle -p "$fixture/android" :app:connectedDebugAndroidTest --stacktrace

The two tests require public entry-point signatures, all original adapter interfaces,
exact superclass and constructor, successful class loading and construction,
the unmistakable test-stub marker, and linkable official Flutter/Core renderer classes.
They do not initialize MAX or Prebid or invoke ads. The host removes INTERNET
permission, has no real SDK key, and never changes consent or reward state.
A construction failure is evidence to investigate, not permission to initialize
MAX or add ad loading.

Results: android/app/build/reports/androidTests/connected/ and
android/app/build/outputs/androidTest-results/connected/.

## Operator probe — inspect the packaged class definitions

The operator reports that both checkDebugDuplicateClasses and full probe assembly
succeeded. The earlier expectation of a standard AGP/D8 failure was not established.
Do not infer that both requested class definitions reached the APK from the graph
or assembly success alone.

    & $gradle -p "$fixture/android" :app:inspectCheckpointAProbeApk -PcheckpointADuplicateProbe --rerun-tasks --no-build-cache --stacktrace

This builds the probe and inspects its APK before a subsequent positive build can
overwrite it. The task uses the existing DEX class_def parser and reports
PROBE_DEX_ADAPTER_DEFINITIONS per DEX plus PROBE_APK_ADAPTER_DEFINITIONS total.
It installs no artifact view or hooks into AGP's duplicate-check task.

Interpretation:
- Total 2: both definitions reached the APK despite assembly success; the earlier
  assumption that this build pipeline necessarily rejects that collision is false.
- Total 1: the intended collision did not reach the APK; artifact selection or
  class inclusion needs further investigation.
- Any other total or tooling failure: investigate; do not count as a negative PASS.

The task is inspection only: it neither manufactures an AGP duplicate-class error
nor grants a Checkpoint A PASS. Codex did not run it. The upstream dependency in
probe mode and its normal-mode exclusion remain unchanged. Do not use the probe
property for positive/device verification.

Positive confirmation after the probe:

    & $gradle -p "$fixture/android" :app:verifyCheckpointA --rerun-tasks --no-build-cache --stacktrace

The positive dependency path, adapter source, SDK versions and packaging checks are
unchanged. The root exclusion remains active when the probe property is absent.

Afterward, operator runs:

    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research status --short --branch
    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research diff --check

Also inspect the new, untracked checkpoint-a files: ordinary git diff --check does
not cover untracked files. Verify protected worktree statuses/references against
the recorded baseline. No commit, push, dashboard or protected-worktree modification
is part of this step.

## Acceptance criteria and limits

PASS (packaging/class-loading subchecks only):
- Exact official hosted Flutter 4.5.1 and strict native SDK versions verified.
- Exactly one adapter definition in the assembled application APK.
- No duplicate-class error in positive build.
- Device class lookup, constructor invocation, superclass/interfaces and signatures pass.
- Expected duplicate-class failure in the negative control.
- No fork dependency and no protected changes.

FAIL: wrong versions, surviving original adapter, multiple definitions, incompatible
entry points, construction/linkage failure, fork dependency, or protected edits.

INCONCLUSIVE: missing dependencies/toolchain/device, commands not run, or failure
unrelated to the property being tested.

Source-level same-class replacement is feasible without an official Flutter plugin
patch or a renamed adapter. It creates no second MAX network or ladder.
Retaining the configured name eliminates a class-name migration at the inspected
loader boundary. Actual server-configured TestPBS loading, reporting/debugger metadata,
initialization recovery, live mediation and production compatibility remain unverified.
Do not label Class.forName/constructor success as MAX having invoked TestPBS.

The test double deliberately does NOT preserve operational Prebid behavior. A future
real replacement must retain required upstream companion classes, renderer delegation,
privacy propagation and callback semantics, and address bounded initialization separately.
Upstream adapter/core POMs declare Apache-2.0; any future copied implementation must
retain applicable license/notices and identify changes. This original test double
does not copy the upstream implementation.

Standalone pub.dev distribution remains a future packaging integration: this Android
library can be carried by a Flutter plugin, but this private fixture is not that package.
No public Asterise API or production dependency graph has been changed.

STOP after Checkpoint A. No Checkpoint B authorization is implied.
