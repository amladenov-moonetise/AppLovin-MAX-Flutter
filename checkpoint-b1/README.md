# Checkpoint B1 — MAX custom-network invocation research

Status: IMPLEMENTED FIXTURE, NOT RUN by Codex. B1 runtime outcome is INCONCLUSIVE
until the operator supplies the evidence below. This is NOT an enhanced Prebid
bidding adapter and is not suitable for publication or production use.

All B1 files are separate from the preserved checkpoint-a fixture. Worktree:
C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research
Required branch: research/prebid-adapter-checkpoint-b1
HEAD was read as that branch before editing; Codex did not run Git commands.
Protected production references must remain untouched:
- Asterise release/android-ads: 311cde2
- MAX Flutter fork release/android-max: 6d31ab6

## What the test does

The publisher Flutter screen owns official MAX initialization, one explicit load,
and its listeners. Native MainActivity handles diagnostics and installed APK checks
only. It never creates or retrieves a MAX fullscreen object or initializes MAX.
The official Flutter plugin privately owns the actual MAX fullscreen object.

The runtime action:
1. Requires an operator-supplied development SDK key, existing MAX ad-unit ID and
   confirmation that publisher privacy prerequisites are already satisfied.
2. Counts the original adapter class definitions in the installed base/split APKs
   on a background thread. Anything other than one rejects the run.
3. Starts one process-local run ID and a 60-second observation window.
4. Calls official AppLovinMAX.initialize and verifies isInitialized. Initialization
   waits at most 20 seconds, followed by at most 3 seconds for isInitialized.
5. Calls official loadInterstitial OR loadRewardedAd exactly once from the screen.
6. Observes genuine MAX callbacks and safe waterfall metadata. It never calls show,
   retries the publisher load, initializes Prebid, fetches PBS or grants a reward.
7. Allows copying only the bounded, redacted diagnostic JSON lines.

One run per fresh process. A timeout does not cancel MAX's internal asynchronous
work, SDK-managed retries or mediation. It does not authorize a second load.
At 60 seconds capture closes; SDK callbacks after the observation window are not
used as evidence. Force-stop the research app after export. There is no SDK reset,
private-object access or invented destruction callback.

## Research adapter contract

The only implementation of:
com.applovin.mediation.adapters.PrebidMaxMediationAdapter
extends MediationAdapterBase, keeps the public AppLovinSdk constructor and the
original ad-view/interstitial/rewarded/native interfaces and entry-point signatures.

It emits these real entry-point markers with Android Logcat tag MoonetiseAdapterB1:
- MOONETISE_B1_ADAPTER_CONSTRUCTED
- MOONETISE_B1_MAX_INITIALIZE_CALLED
- MOONETISE_B1_MAX_LOAD_CALLED
- MOONETISE_B1_ADAPTER_DESTROYED

The stub initialize method immediately completes INITIALIZED_SUCCESS for the
research stub itself. RESEARCH_STUB_READY is not Prebid SDK readiness or consent.
No publisher privacy flags are read, fabricated or overwritten by the adapter.
It returns MaxAdapterError.NO_FILL through the appropriate public adapter listener
when MAX invokes a load. No successful-ad callback or rendering path exists.
Show entry points return AD_NOT_READY; the screen has no show action.
onDestroy records a marker only when MAX actually calls it. MAX may retain an
adapter beyond the window, so an absent destruction marker is not by itself FAIL.

Adapter version 0.0.1 identifies this research implementation. SDK metadata remains
the installed Prebid 3.4.1 version. No Prebid core/renderer source is copied or changed.
This differs deliberately from A's initialize-failure stub so MAX can proceed to
a controlled no-fill load without any live Prebid work.

## Source-grounded account and network prerequisites

Existing network, supplied by the operator:
- TestPBS
- Management identifier CUSTOM_NETWORK_SDK-70429
- Android adapter class com.applovin.mediation.adapters.PrebidMaxMediationAdapter

Read-only account verification is required BEFORE running:
- The development SDK key belongs to the approved MAX account/app.
- An existing Android ad unit of the selected fullscreen format belongs to that app.
- TestPBS is already enabled and eligible in that unit's existing waterfall,
  including existing country/placement/custom-parameter/CPM configuration.
- The Android adapter class remains the exact name above and there is one active
  Prebid network/ladder. No new network, tier, placement or dashboard write is allowed.
- Verify no other custom network using this class could be confused with TestPBS.
- The application ID used for this build is approved for that existing test app.
  The default research package is not assumed to match an existing MAX account.
- The publisher has independently satisfied applicable privacy/consent requirements.
  The UI checkbox only authorizes this research action; it does not set MAX consent.
- Use an isolated device/test account and approved network connectivity.

The known moonetise-prebid-rewarded-300 and moonetise-prebid-banner-300 values are
network placement identifiers, NOT MAX ad-unit IDs. Do not enter them as an ad-unit
ID. No eligible interstitial configuration has been assumed or invented. Choose
Rewarded only if the existing approved rewarded unit is the appropriate fixture;
it is LOAD ONLY. Do not change a production ad unit to make the test pass.

If there is no existing approved unit/package/configuration suitable for this app,
record INCONCLUSIVE. Do not change the dashboard or install over a protected or
actual publisher application. An earlier/higher network may win without invoking
TestPBS; that is also INCONCLUSIVE for TestPBS invocation.

Sources actually inspected:
- Official pub cache, hosted pub.dev/applovin_max-4.5.1/lib/applovin_max.dart:
  initialize lines 52–86; isInitialized around 170; setVerboseLogging around 253;
  interstitial listener/load 565–575; rewarded listener/load 610–620.
- Same pinned package lib/src/ad_listeners.dart: InterstitialListener 102–120,
  RewardedAdListener 124–144; all callbacks supplied here are publisher-side listeners.
- lib/src/ad_classes.dart: MaxError code/message/waterfall around 193–225;
  MaxAdWaterfallInfo networkResponses around 303–334; MaxNetworkResponse around
  341–397; MaxMediatedNetworkInfo name/adapterClassName around 403–429.
  credentials and error messages are deliberately NEVER copied or exported.
- lib/src/enums.dart: AdLoadState around 113–122; ErrorCode 125–176. Its documented
  invalidAdUnitId cases include disabled/nonexistent units and package mismatch.
- Official Android AppLovinMAX.java (2242-line 4.5.1 published package):
  loadInterstitial 556–561, loadRewardedAd 582–587, private retrieveInterstitial
  around 1231–1243 and retrieveRewardedAd around 1247–1259; waterfall adapter-class
  serialization around 1512–1523. The bridge does not access those private objects.
- Prebid 3.4.1 adapter sources and MAX 13.3.1 binary signatures are recorded in
  ../checkpoint-a/README.md. B1 retains those public signatures and class identity.
- AppLovin official custom SDK network guide:
  https://support.applovin.com/en/max/mediated-network-guides/integrating-custom-sdk-networks
  documents Android adapter class configuration and enabling an existing custom
  SDK network on an ad unit, with placement/custom parameters/CPM/targeting.
  This is evidence of prerequisites, not authorization to create/edit anything.

The management identifier 70429 is not exposed as a verified identity by these
Dart waterfall fields. The account owner must corroborate that the observed SDK
network name/class refers to the existing TestPBS network.

## Pins and duplicate-class rejection

Official pub.dev applovin_max exactly 4.5.1; native MAX strictly 13.3.1;
Prebid SDK/Core strictly 3.4.1. Original prebid-mobile-sdk-max-adapters is excluded
on every B1 configuration. No dependency on the sibling Flutter MAX fork.
The graph task rejects wrong versions, non-hosted MAX paths and an upstream adapter.

Checkpoint A proved that duplicate definitions can be packaged without AGP rejecting
them. B1 therefore requires final APK class-definition counting; build success is
insufficient. verifyCheckpointB1 counts every classes*.dex. installDebug depends
on that verification, and the running app independently counts installed APKs
before MAX is initialized. A zero/multiple-definition result rejects B1.
Release variants and Checkpoint A's duplicate-probe property are disabled in B1.
This is a debug research fixture, not a release-library validation.

## Exact files added

No existing A files or protected files were edited. Added under checkpoint-b1/:
- .gitignore, pubspec.yaml, lib/main.dart
- android/settings.gradle, build.gradle, gradle.properties
- android/app/build.gradle
- android/app/src/main/AndroidManifest.xml
- android/app/src/main/java/com/moonetise/research/checkpointb1/MainActivity.java
- android/app/src/main/java/com/moonetise/research/checkpointb1/InstalledApkCheck.java
- android/app/src/androidTest/java/com/moonetise/research/checkpointb1/B1DiagnosticsTest.java
- android/replacement/build.gradle, consumer-rules.pro, src/main/AndroidManifest.xml
- android/replacement/src/main/java/com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java
- android/replacement/src/main/java/com/moonetise/research/b1/B1Trace.java
- README.md and REPORT_TEMPLATE.md

## Operator preparation and build commands

Operator performs ALL Git, Gradle, Flutter and device commands. Codex ran none.
Use existing JDK 17+, Gradle 8.13, Android SDK 35, Flutter 3.44.9 as in A.
Do not upgrade pinned dependencies or patch the official plugin to resolve failures.

    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research branch --show-current
    git -C C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research status --short --branch

Require research/prebid-adapter-checkpoint-b1. Record existing modifications and
protected worktree statuses/references using the operator's existing workflow.

Create android/local.properties in the IDE with the existing paths, for example:

    flutter.sdk=C:/Users/AMlad/fvm/versions/3.44.9
    sdk.dir=C:/Users/AMlad/AppData/Local/Android/sdk

No SDK key belongs in local.properties, source, Gradle properties, Git, CLI arguments
or dart-define. Enter it only into the obscured runtime field.

PowerShell variables (replace the Gradle path with the same existing 8.13 executable
used successfully for A; appId must be an approved isolated development package):

    $fixture = 'C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research/checkpoint-b1'
    $flutter = 'C:/Users/AMlad/fvm/versions/3.44.9/bin/flutter.bat'
    $gradle = 'C:/path/to/existing/gradle-8.13/bin/gradle.bat'
    $adb = 'C:/Users/AMlad/AppData/Local/Android/sdk/platform-tools/adb.exe'
    $appId = 'com.moonetise.research.checkpointb1'
    $buildArgs = @("-Pb1ApplicationId=$appId")
    Set-Location $fixture
    & $flutter pub get
    & $flutter analyze
    & $gradle -p "$fixture/android" @buildArgs :app:verifyCheckpointB1 --rerun-tasks --no-build-cache --stacktrace

Require CHECKPOINT_B1_GRAPH_PASS and CHECKPOINT_B1_APK_PASS: adapter definitions=1.
Review pubspec.lock for official hosted 4.5.1 and its hosted sha256. The origin guard
is not a cryptographic audit of arbitrary local pub-cache tampering.

Optional offline preflight test (no MAX init, manual adapter construction or load):

    & $gradle -p "$fixture/android" @buildArgs :app:connectedDebugAndroidTest --stacktrace

This tests installed APK uniqueness and the fixed-schema diagnostic export rejecting
arbitrary payloads. It is NOT runtime mediation evidence. Force-stop afterwards;
never mix its log run ID with the live B1 run.

Install ONLY the verified APK into the approved isolated test package:

    & $adb install -r "$fixture/android/app/build/outputs/apk/debug/app-debug.apk"
    & $adb shell am force-stop $appId

Do not use a package ID that would replace a protected or actual publisher app.
If a different approved development ID is needed, rebuild and verify with the SAME
buildArgs before installing. No dashboard update is implied.

## One bounded live device test and one exportable report

Use one isolated Android device (API 26+, physical Android 10 preferred). No hot
restart/hot reload or instrumentation run during the live experiment.

1. Clear the prior capture, then launch the verified research host:

       & $adb logcat -c
       & $adb shell am start -n "$appId/com.moonetise.research.checkpointb1.MainActivity"

2. In a second PowerShell terminal set adb/fixture as above and capture ONLY the
   redacted research tag. This produces one JSONL report, not a full SDK/system log:

       & $adb logcat -v raw MoonetiseAdapterB1:I '*:S' | Where-Object { $_ -match '^\{' } | Tee-Object -FilePath "$fixture/b1-logcat.jsonl"

3. Enter the approved development SDK key and existing MAX ad-unit ID in the app.
   Select its correct fullscreen format. Confirm privacy prerequisites only if
   actually satisfied. Press Run Adapter Invocation Test ONCE.
4. Observe for at most 60 seconds after RUN_STARTED. Initialization is bounded;
   there is one publisher load call, no show, no publisher retry. Continue observing
   after an early MAX loaded/failed callback to capture real adapter lifecycle events.
5. Stop capture at the window's end (Ctrl+C), even if no close marker appears.
   Copy redacted diagnostics from the app as an alternative JSONL export. Preserve
   one report for the matching run ID; do not concatenate tests or runs.
6. Force-stop the research host:

       & $adb shell am force-stop $appId

Use REPORT_TEMPLATE.md to record the actual outcome and evidence. No actual device
report exists yet; a template or example must never be presented as a captured run.

Exported data is restricted to run ID, timestamp, event, constant adapter class,
MAX version, fixed result/failure categories, safe network labels and load-state
enums. Raw SDK keys, ad-unit IDs, device/advertising IDs, consent strings, credentials,
raw requests, revenue amounts and raw error/SDK configuration objects are excluded.
All SDK/vendor logs remain outside the redacted tag. The official plugin can print
its own errors, so do not export an unfiltered Logcat or enable verbose SDK logging.

Network labels normalize only TestPBS, Custom Network (SDK) - TestPBS, and known
AppLovin names; other names become OTHER. An unexpected custom-network display name
may need separate read-only corroboration and is INCONCLUSIVE in this report.
HOST_SELECTED_NETWORK comes only from a genuine loaded callback. HOST_WATERFALL
comes only from genuine MAX waterfall metadata. A CLASS_MATCH only compares the
exact published adapter class name; it does not mean a network loaded or won.
A field named selectedNetwork on a waterfall record identifies that response's
network, not the winning network. Use HOST_SELECTED_NETWORK for the actual winner.
Waterfall export is capped at 24 responses. TestPBS beyond that cap or a capture
limit event can leave the network association INCONCLUSIVE; do not infer absence.

## Acceptance and failure classification

PASS for the B1 mediated-invocation experiment requires ALL:
- Correct branch/protected boundaries, official plugin, pins and exactly one final
  packaged adapter definition verified.
- HOST_MAX_INITIALIZED SUCCESS from official initialize + isInitialized.
- MOONETISE_B1_ADAPTER_CONSTRUCTED and MOONETISE_B1_MAX_INITIALIZE_CALLED for the
  same fresh runtime run ID, with no direct-construction test mixed into the run.
- MOONETISE_B1_MAX_LOAD_CALLED and its controlled no-fill observed.
- Existing TestPBS identity/class is corroborated through the account prerequisites
  AND SDK waterfall metadata: TESTPBS with CLASS_MATCH and AD_FAILED_TO_LOAD.
  Entry-point markers alone do not prove which custom-network configuration caused
  the invocation. The network must have been attempted, not merely listed.

If construction/initialization is observed but TestPBS load entry or attempted
waterfall association is absent, record that partial evidence and classify the
overall experiment INCONCLUSIVE. Other networks may win before TestPBS is reached.
No-fill is expected from the stub; PASS does not mean a bid, fill or impression.
MOONETISE_B1_ADAPTER_DESTROYED is recorded only if MAX calls onDestroy in the window;
absence alone does not fail construction/initialization acceptance.

INCONCLUSIVE:
- Other networks load but TestPBS is not reached/identified.
- SDK key, app/package, unit/format, eligibility, connectivity or init deadline blocks
  invocation; the safe failure categories do not imply a binary/class-loading error.
- No invocation without established class-loading failure, missing network identity,
  missing genuine callback evidence, premature detach, capture overflow, diagnostic
  export failure or a callback arriving outside the observation window.
- Unexpected display, revenue, click or reward callback in this no-show experiment.
  The screen only records these callbacks; it never grants currency or logs amounts.

FAIL:
- MAX explicitly attempts the configured adapter and cannot find/construct it, or
  entry points are binary-incompatible. Missing markers alone do not establish this.
- Multiple/zero adapter definitions or wrong pins/forked plugin invalidate the test.
- Patching the official plugin or changing protected production code is required.

Public MAX error codes cannot always distinguish loader failure from SDK/network
eligibility. The tag-only report deliberately does not copy raw exception messages.
Do not label an absent marker as FAIL without separate, safely redacted evidence
establishing an actual class-loading attempt and failure. That may remain a blocker.

No live run, SDK initialization, ad load, account lookup or dashboard modification
was performed by Codex. Build/test failures must be reported as observed; do not
weaken the uniqueness checks or patch dependencies. Operator performs git diff
--check/status and confirms that checkpoint-a plus both protected worktrees remain
unchanged before accepting the changes.

STOP after B1. No B2 bidding, cache, handoff, renderer work or central architecture
change is authorized.
