# Checkpoint B2 — prepared Prebid interstitial handoff (research only)

Status: OPERATOR-REVIEWED PASS for the bounded Android 10 B2 research objectives.
Both original exported reports retain classification=INCONCLUSIVE. This separate operator
acceptance record does not rewrite their classifications, evidence or the classifier.
A/B1 are preserved.
Branch: research/prebid-adapter-checkpoint-b2, based on B1 e4a477f.
Protected references: Asterise release/android-ads 311cde2; MAX fork release/android-max 6d31ab6.
No production Flutter API, Core, dashboard, renderer or protected source is changed.

## Completed operator acceptance record

Evidence source: the operator's Android 10 runtime review reported in this conversation.
Codex did not rerun builds, device tests or advertising requests for this acceptance update.
The original exported reports remain operator-held and unchanged; no raw captures are
added to this source commit. No run IDs, timestamps or missing report fields are invented.

| Scenario | Operator-reviewed evidence | Original automated classification | Separate operator review |
|---|---|---|---|
| A — No Prebid demand | Enhanced adapter returned no-fill; AppLovin loaded and displayed successfully. | INCONCLUSIVE | PASS for no-demand fallback research objective |
| B — Synthetic Prebid demand | Synthetic response prepared; SLOT_CLAIM=MATCH; CACHE_POP=CONSUMED; ADAPTER_DELEGATED=SUCCESS; MAX waterfall CLASS_MATCH / AD_LOADED; publisher DISPLAYED and HIDDEN callbacks; operator visually confirmed the synthetic video. | INCONCLUSIVE | PASS for prepared-demand handoff and visible rendering research objective |

The automated classification is retained as evidence of the classifier's actual output.
The operator's review supplies a separate bounded acceptance decision; it is not an
automated PASS and does not establish that every automatic predicate was satisfied.
The exact reason each classifier remained INCONCLUSIVE is not established by this update.
No acceptance assertions or implementation/classification logic were changed.

Earlier verification is kept separate: Codex ran the six SDK-free PendingSlot JUnit tests
with existing JDK17/JUnit4.13.2 jars: OK (6 tests). IDE inspections reported no issues in
the five main bridge/adapter/screen files checked; that is not compilation evidence.
The operator subsequently reported the B2 Gradle test/package command BUILD SUCCESSFUL
in 46 seconds (93 actionable tasks: 92 executed, 1 up-to-date), then completed the two
runtime scenarios above. Codex did not independently repeat those build/device checks.

Acceptance is limited to the pinned, isolated interstitial proof of concept. It does not
certify production readiness, OMID, VAST completion, monetized revenue, MAX-first recovery,
multiple overlapping loads, rewarded or banner behavior.

## Fixed environment

- Android applicationId com.moonetise.asterise.sample; minSdk 26.
- Official hosted applovin_max 4.5.1, native MAX strictly 13.3.1, Prebid SDK/Core strictly 3.4.1.
- MAX interstitial d236de754b83a168.
- Existing TestPBS / CUSTOM_NETWORK_SDK-70429.
- Original class com.applovin.mediation.adapters.PrebidMaxMediationAdapter.
- Existing single network/CPM ladder; no dashboard changes or new placements.
- Existing approved endpoint https://pbs-test.moonetise.com/openrtb2/auction,
  account moonetise-test, stored impression moonetise-mobile-interstitial-test,
  fixed stored response moonetise-test-video-interstitial-3usd.
These are existing internal fixture identifiers, not credentials or production defaults.
Their approval and global-state limitations are recorded in the protected reference's
sample-app/SYNTHETIC_VIDEO_TESTING.md. Today's endpoint availability is not assumed.

WARNING: THIS PACKAGE REPLACES THE EXISTING ASTERISE SAMPLE ON THE SAME ANDROID USER.
Use an isolated device/user. Obtain fresh explicit operator acknowledgement before EACH
installation. Never automatically uninstall, downgrade, clear data or change package ID.
The Gradle installDebug task additionally requires -Pb2AcknowledgeSampleReplacement=YES.
Manual adb installation cannot be guarded by Gradle; use the acknowledgement command below.

## What is implemented

Flutter owns official MAX initialization, interstitial listener, one load, and explicit show.
Native MainActivity owns only research diagnostics, installed-APK scanning and optional
Prebid initialization/preparation. It never constructs or retrieves a MAX fullscreen object.
Publisher privacy/CMP must already be satisfied independently. The runtime checkbox is
development authorization, not consent; no consent strings or privacy preferences are forged.

A — NO PREBID DEMAND initializes Prebid (a status check can occur) but performs ZERO
PBS auctions. It arms an empty slot, calls official MAX once, and expects prompt adapter
NO_FILL if TestPBS is reached. Other eligible MAX demand remains under MAX control.
This deliberately unavailable-demand scenario does not prove a real PBS no-bid response.

B — SYNTHETIC PREBID DEMAND performs ONE real SDK preparation using the fixed approved
stored-response override. No local response JSON or targeting is invented. A public
PrebidMediationDelegate captures the response ID after the SDK caches the response.
The provider accepts only the expected video/$3.00/hb_pb=3.00 fixture. A fresh response
is eligible for 5000 monotonic milliseconds by default (operator adjustable 1000–30000),
shortened by the response's positive bid expiration when supplied. Arming does not extend it.

The synchronous provider checks exact unit, original custom-parameter matching (requiring
hb_pb to be configured), expiry and authorization. A mismatch leaves demand untouched.
One matching claim removes the pending ID atomically before calling the original manager.
The copied Prebid ParametersChecker and ListenersCreator remain unchanged. The copied
MaxInterstitialManager adds only a marker immediately after its successful original cache
pop. Existing Prebid Core PluginRendererFactory/controller/creative renderer remain unchanged.
No PBS call or initialization is performed inside the adapter load callback.

The replacement preserves the original class/AppLovinSdk constructor and all original MAX
interfaces. Only interstitial is functional here; rewarded/ad-view/native return NO_FILL.
Those formats are out of scope and must not be exercised in this research process.
Initialization preserves original non-null age-restriction -> COPPA propagation and completes
once immediately: INITIALIZED_SUCCESS when Prebid is ready, INITIALIZED_UNKNOWN otherwise.
MAX-first recovery with INITIALIZED_UNKNOWN remains a runtime question, not an acceptance claim.

Terminal publisher loaded/failed callbacks clear only the unused owned response. Claimed
controllers are left to MAX and Prebid show/destroy. Backgrounding before explicit Show revokes
pending demand and disables Show. A fullscreen presentation's own background transition does
not destroy it. Cancellation, observation timeout and detach close demand. Clearing the
process-global stored-response override cannot retract requests already sent.

One run per fresh process, no re-arm, no retries, no queue. An unresolved old load can therefore
never receive demand from a later B2 run. This is deliberately not a general publisher SDK.
An unrelated same-unit load during an armed slot is indistinguishable: cooperative integration
is required. MAX controls its own scheduling/timeouts and subsequent demand; fill is not guaranteed.

## Operator build (no installation)

Use the same existing Flutter 3.44.9, Gradle 8.13, JDK 17 and Android SDK 35 as B1.
Provide ignored android/local.properties with flutter.sdk and sdk.dir ONLY. Never put SDK
keys in source, local.properties, environment variables, command arguments or dart-define.
The copied pubspec.lock preserves B1's official hosted 4.5.1 dependency and checksum.
Do not copy generated B1 directories or reuse B1 build outputs.

PowerShell (set $gradle to your existing 8.13 gradle.bat executable):

    $fixture = 'C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research/checkpoint-b2'
    $flutter = 'C:/Users/AMlad/fvm/versions/3.44.9/bin/flutter.bat'
    $adb = 'C:/Users/AMlad/AppData/Local/Android/sdk/platform-tools/adb.exe'
    Set-Location $fixture
    & $flutter pub get
    & $flutter analyze
    & $gradle -p "$fixture/android" :replacement:testDebugUnitTest :app:verifyCheckpointB2 --rerun-tasks --no-build-cache --stacktrace

Require CHECKPOINT_B2_GRAPH_PASS and CHECKPOINT_B2_APK_PASS: adapter definitions=1.
The six PendingSlot JVM tests cover tier mismatch, wrong unit/expiry, late completion,
claimed-response cleanup, no-bid cycle tracking, and concurrent matching claims.
No test uses synthetic JSON or masquerades as PBS/rendering evidence.

Upstream org.prebid:prebid-mobile-sdk-max-adapters is excluded on every configuration.
The replacement compiles against MAX13.3.1 as compileOnly; the host supplies the sole runtime.
The graph check rejects wrong pins/forked plugin/upstream adapter. The final APK task counts
actual class_def entries across EVERY classes*.dex. The runtime preflight counts base/splits.
AGP itself did NOT reject duplicates in A: build success alone is insufficient.
Release is disabled; this is a debug fixture, not a publishable artifact.

## Explicit installation acknowledgement

After successful build/class-count verification, warn the operator again:
"This installs over com.moonetise.asterise.sample and may replace the working sample."
Run the following only on the approved isolated device. It prompts freshly; it never
uninstalls or clears data. A signature/version conflict must STOP the installation.

    $ack = Read-Host 'This replaces the existing Asterise sample. Type INSTALL B2 to acknowledge'
    if ($ack -ne 'INSTALL B2') { throw 'Installation cancelled' }
    & $adb install -r "$fixture/android/app/build/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Install failed: stop; do not uninstall/downgrade/clear data' }

## Two bounded device runs

Use separate fresh processes for A and B, never hot restart/reload. No instrumentation run,
other SDK caller or unrelated ad-unit load during the experiment.

    & $adb shell am force-stop com.moonetise.asterise.sample
    & $adb shell am start -n com.moonetise.asterise.sample/com.moonetise.research.checkpointb2.MainActivity

1. Enter the development SDK key in the obscured memory-only field.
2. Select A or B, keep the default 5000ms eligibility initially.
3. Confirm existing publisher privacy/CMP and explicit development authorization.
4. Press RUN INTERSTITIAL TEST once. Init/preparation are bounded; failed preparation
   still dispatches the independently authorized official MAX load.
5. Observe genuine preparation, adapter and publisher results. If other networks win
   before TestPBS is invoked, the TestPBS result is INCONCLUSIVE.
6. For B only, require selected network TESTPBS before interpreting the creative as
   Prebid evidence. Press SHOW LOADED INTERSTITIAL explicitly; no automatic show occurs.
7. After dismissal, confirm expected synthetic visibility only if actually observed.
8. Within the 175-second Flutter / 180-second native window, COPY REDACTED ACCEPTANCE
   REPORT. Preserve one report per run. Then force-stop; restart for the other scenario.

Optional bounded redacted tag capture (stop within 180 seconds, Ctrl+C):

    & $adb logcat -v raw MoonetiseAdapterB2:I '*:S' | Where-Object { $_ -match '^\{' } | Tee-Object -FilePath "$fixture/b2-logcat.jsonl"

Do not export full system/SDK logs. Prebid and MAX verbose logging remain disabled.
The copied report includes computed classification plus bounded JSONL evidence.
No SDK keys, raw errors/requests/responses, response IDs, credentials, consent strings,
advertising/device IDs or revenue amounts enter the research log. Waterfalls export at
most 24 normalized network responses. Overflow/diagnostic failure is INCONCLUSIVE.
No report shipped with this fixture is an actual acceptance capture.

## Acceptance

Common: official plugin/pins, exactly one final AND installed adapter class, actual MAX
construction/initialization/load markers, existing TestPBS account association corroborated.
The SDK network name/class alone does not expose management ID 70429; account corroboration
must come from the operator's existing configuration.

A PASS: empty demand + provider MISSING/no-fill + attempted TestPBS CLASS_MATCH failure,
genuine terminal MAX callback, and another network loaded or was attempted and failed.
A no-fill is not a failed implementation; no eligible other demand may leave INCONCLUSIVE.

B PASS: real successful approved preparation, $3.00/video target validation, matching claim,
actual original cache pop, genuine MAX loaded/TestPBS selected and CLASS_MATCH AD_LOADED
waterfall response, real displayed/hidden callbacks, and explicit operator visibility YES.
A bid alone, generic displayed event, or another selected network cannot prove Prebid rendering.
This test does not claim OMID certification, VAST completion or monetized revenue.

FAIL: wrong pins/plugin, zero/multiple adapter definitions, proven binary/class-loading failure,
or operator confirms the expected creative was not visible after a selected TestPBS show.
The preflight error and packaging errors reject the run even if no JSONL report can start.
The automatic classification is an aid: manually apply common prerequisites and all criteria.
INCONCLUSIVE: TestPBS not reached/identified, init/connectivity/configuration issue, expiry,
target mismatch, absent cache, capture limit, detach, no terminal/rendering evidence.
Do not change dashboard tiers, widen matching or weaken assertions to manufacture PASS.

## Source provenance / files

Upstream adapter sources copied from prebid-mobile-sdk-max-adapters-3.4.1-sources.jar:
- com/applovin/mediation/adapters/prebid/ParametersChecker.java (unchanged)
- com/applovin/mediation/adapters/prebid/ListenersCreator.java (unchanged)
- com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java (one marker added)

Original adapter contract: PrebidMaxMediationAdapter.java lines28–107,123–159;
checker23–57; manager40–85; Core MediationBaseAdUnit130–139; BidResponseCache68–129,136–167.
MAX13.3.1 public adapter/parameter interfaces and official Flutter4.5.1 are retained.
See THIRD_PARTY_NOTICES.md and LICENSE-Prebid-Apache-2.0.txt.

Exactly 26 added files, all relative to checkpoint-b2; no existing source is changed:

```text
.gitignore
LICENSE-Prebid-Apache-2.0.txt
README.md
THIRD_PARTY_NOTICES.md
pubspec.yaml
pubspec.lock
lib/main.dart
android/settings.gradle
android/build.gradle
android/gradle.properties
android/app/build.gradle
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/moonetise/research/checkpointb2/MainActivity.java
android/app/src/main/java/com/moonetise/research/checkpointb2/InstalledApkCheck.java
android/replacement/build.gradle
android/replacement/consumer-rules.pro
android/replacement/src/main/AndroidManifest.xml
android/replacement/src/main/java/com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java
android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ParametersChecker.java
android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ListenersCreator.java
android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java
android/replacement/src/main/java/com/moonetise/research/b2/PendingSlot.java
android/replacement/src/main/java/com/moonetise/research/b2/ResponseParameters.java
android/replacement/src/main/java/com/moonetise/research/b2/DemandBridge.java
android/replacement/src/main/java/com/moonetise/research/b2/B2Trace.java
android/replacement/src/test/java/com/moonetise/research/b2/PendingSlotTest.java
```

The operator-reviewed Android 10 runs establish bounded no-demand fallback and synthetic
interstitial handoff/rendering acceptance for the tested configuration. They do not guarantee
future PBS availability or tier eligibility. MAX-first initialization recovery, broader
lifecycle/concurrency behavior and production integration remain outside this acceptance.
Original automated INCONCLUSIVE reports remain unchanged. No B3/production work is authorized.
