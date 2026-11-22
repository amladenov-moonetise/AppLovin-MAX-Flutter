# Moonetise B5 — Android Banner E2E

CLOSED — OPERATOR-REVIEWED PASS for the bounded Android synthetic banner E2E milestone.
Branch: research/prebid-adapter-checkpoint-b5.
Frozen B4 parent: 944dbe3d5fb9b26d219e4f4aab8b718f5ed89e74.
Completed A/B1/B2/B3/B4 and their evidence are unchanged.

## Final acceptance and Central Integration handover

These are separate operator-reviewed findings. Every original automatic report remains
INCONCLUSIVE, unchanged. Network-name normalization and operator-review requirements
prevented automatic positive classification; no classifier, report or callback was
changed to force PASS.

Runtime evidence below is transcribed from the operator-supplied observations and run
IDs. Original JSONL exports were not located in checkpoint-b5 and are not reconstructed
or fabricated in this commit. Preserve the operator's original exports separately.
Codex inspected existing source/build reports for closure without rerunning validation.

### Flutter and packaging

- flutter pub get: operator PASS; official hosted applovin_max 4.5.1 retained.
- flutter analyze: operator PASS, no issues.
- :replacement:testDebugUnitTest and :app:verifyCheckpointB5Debug: operator PASS.
  Existing generated JUnit XML reports independently inspected during closure show
  26 tests, zero failures/errors/skips in seven suites.
- Debug graph/APK console evidence supplied by the operator:

```text
CHECKPOINT_B5_DEBUG_GRAPH_PASS: MAX=13.3.1; Prebid=3.4.1; upstream absent
CHECKPOINT_B5_DEBUG_APK_PASS: adapter definitions=1
BUILD SUCCESSFUL — 95 actionable tasks
```

- :app:verifyCheckpointB5Release: operator PASS, with supplied console evidence:

```text
CHECKPOINT_B5_RELEASE_GRAPH_PASS: MAX=13.3.1; Prebid=3.4.1; upstream absent
CHECKPOINT_B5_RELEASE_APK_PASS: adapter definitions=1
BUILD SUCCESSFUL — 136 actionable tasks
```

This establishes debug and minified release packaging, including one original-name
replacement adapter definition per final APK. It does NOT establish release banner
runtime rendering. Generated reports/APKs/build outputs remain ignored and uncommitted.

### Tablet MAX-only — caaa14b9-2995-4a2c-9e23-31656be02447

Installed APK scan succeeded. Adapter invoked with ADAPTER_FORMAT=BANNER and
FORMAT_SIZE_CHECK=MATCH; no eligible Prebid contribution; prompt adapter no-fill.
MAX ultimately reported FAILED and publisher view was removed. No alternative network
filled. This verifies the observed no-demand/no-fill path, not successful alternative
fill. Overall automatic classification stays INCONCLUSIVE.

### Tablet synthetic — 7ff327d3-9902-49a8-adb6-4b0c44b50a4f

Decisive operator-reviewed synthetic banner E2E: PASS.

Observed:
PREBID_INITIALIZED=SUCCESS; PREPARATION_RESULT=BID; CAPTURED=FRESH;
ADAPTER_FORMAT=BANNER; FORMAT_SIZE_CHECK=MATCH; SLOT_CLAIM=MATCH;
CACHE_POP=CONSUMED; ADAPTER_DELEGATED=SUCCESS; HOST_LOAD_RESULT=LOADED;
HOST_WATERFALL=CLASS_MATCH / AD_LOADED; HOST_REVENUE=SUCCESS;
HOST_OPERATOR_VISIBLE=YES; BANNER_MANAGER_DESTROYED=SUCCESS;
HOST_VIEW_REMOVED=SUCCESS.

The operator confirmed the synthetic TestPBS 320x50 banner in the actual
publisher MAX view, and one genuine publisher revenue callback was observed.
No missing normalized HOST_SELECTED_NETWORK value or additional operator-review event
is invented. The original automatic classification remains INCONCLUSIVE.

Additional adapter invocations after the successful load returned PROVIDER_MISS /
ADAPTER_NO_FILL. The prepared contribution was consumed once and not reused.
Those invocations do NOT establish explicit publisher refreshes, controlled repeated
Prebid auctions or a refresh-controller validation.

### Tablet cancellation — bee9754f-32ee-4320-9e59-2205018a39b7

Cancellation/cache protection: operator-reviewed PASS.

PREPARATION_RESULT=BID; OWNED_CACHE_CLEARED=SUCCESS;
CONTRIBUTION_CLEARED=CANCELLED; SLOT_ARMED=MISSING.
Subsequent adapter calls returned prompt no-fill, with no matching claim or consumed
cache pop. MAX ultimately returned FAILED; publisher view was removed.
Alternative-network fill was not observed. Automatic classification remains INCONCLUSIVE.

### Phone cancellation / AppLovin — d50e6ccc-94ef-4075-b884-e97aa062d92a

AppLovin fallback after Prebid cancellation: operator-reviewed PASS at SDK/callback level.

PREPARATION_RESULT=BID; OWNED_CACHE_CLEARED=SUCCESS;
CONTRIBUTION_CLEARED=CANCELLED; SLOT_ARMED=MISSING; ADAPTER_NO_FILL;
HOST_LOAD_RESULT=LOADED; TestPBS CLASS_MATCH / AD_FAILED_TO_LOAD;
AppLovin CLASS_MISMATCH / AD_LOADED; HOST_REVENUE=SUCCESS;
HOST_VIEW_REMOVED=SUCCESS.

Explicit operator visual confirmation of the AppLovin creative is not recorded.
Do not infer it from loaded/revenue callbacks. This is not a synthetic Prebid
positive-path PASS because Prebid demand was deliberately cancelled.
Automatic classification remains INCONCLUSIVE.

### Accepted limits and future boundary

1. No same-process banner disposal/recreation reliability test.
2. No controlled repeated Prebid refresh test.
3. No production remote-config refresh integration.
4. No automatic switching between managed refresh and native MAX refresh.
5. No minified release runtime banner test.
6. No memory-leak profiling.
7. No production Sourcepoint/TCF synchronization.
8. No standalone Flutter package delivery.
9. No iOS acceptance.
10. No claim of guaranteed alternative-network fill.
11. Phone fallback visual confirmation is not explicitly recorded.
12. Existing B3/B4 fullscreen runtime results are preserved, not rerun.

Refresh-controller research needs separate Central Integration authorization, a separate
checkpoint and acceptance contract. Multiple SDK adapter invocations do not authorize
or prove a refresh implementation. No B6, refresh development, Sourcepoint, Google,
iOS or production Flutter integration begins during this closure.


## Scope and frozen contract

- Official hosted applovin_max 4.5.1; MAX 13.3.1; Prebid SDK/Core 3.4.1; minSdk26.
- Existing Flutter 3.44.9 / Gradle 8.13 / B4 Android toolchain; no warning-driven upgrades.
- Application com.moonetise.asterise.sample; banner cecda62026b8a5bb.
- TestPBS CUSTOM_NETWORK_SDK-70429; one unchanged network/price ladder.
- Original com.applovin.mediation.adapters.PrebidMaxMediationAdapter identity/public
  AppLovinSdk constructor, with upstream prebid-mobile-sdk-max-adapters excluded.
- Banner 320x50 DIPs, approved $3.00 synthetic fixture only in debug.
- Publisher owns official MAX initialization, view, listener/revenue and disposal.
- No substitute banner view, HTML/WebView renderer, dashboard changes or production APIs.

See SOURCE_PROVENANCE.md for exact pinned source and compatibility differences;
REPORT_FORMAT.md for acceptance and FILES.md for the exact added-file inventory.

## Handoff and official widget lifecycle

One optional banner-scoped response uses B3/B4 PendingSlot semantics. Actual cached
response must be type=banner, w=320, h=50, price=3 and hb_pb=3.00. The configured
320x50 SDK request is not itself proof that the returned creative matches.

The adapter accepts only the actual MAX BANNER format, 320x50 and exact unit.
Original ParametersChecker compares all MAX custom targeting; mismatch does not
consume. One matching claim removes eligibility atomically; original MaxBannerManager
then performs actual cache pop and creates unchanged SDK MediationBannerView.
Its original listener supplies that actual View to MAX. No auction/wait occurs
inside adapter loading. Missing or ineligible demand yields prompt no-fill.

Official MaxAdView loads in its native constructor when mounted, after applying
extras and refresh state. B5 therefore prepares and arms BEFORE mounting it.
No separate loadBanner call is made. Publisher widget properties:
adFormat.banner, width=320, height=50, adaptive=false, autoRefresh=false,
extraParameters force_banner=true. The same widget object/key is retained across
diagnostic parent rebuilds. No preload, plugin patch, native object lookup or custom
platform view is used.

IMPORTANT TABLET BOUNDARY: official 4.5.1 factory initially chooses LEADER on tablets.
Dart dimensions alone do not force BANNER. The existing public force_banner parameter
is applied before load; ADAPTER_FORMAT records what MAX 13.3.1 actually supplies.
A persistent LEADER invocation is rejected without consuming the response and the
positive path stays INCONCLUSIVE. Do not silently substitute a 728x90 creative,
coerce adapter format or change the official plugin to make it pass.

Native unused-cache cleanup occurs after genuine publisher loaded/failed or cancellation/
detach. It never destroys a claimed renderer. Removing the actual official Flutter
widget triggers the plugin's normal platform-view disposal and SDK destroy path.
HOST_VIEW_REMOVED records Flutter frame/subtree removal, not an invented SDK completion.
BANNER_MANAGER_DESTROYED records the actual original manager destroy callback.
Operator disposal review is separate. No memory-leak-free claim is made.

## Verified approved fixture / live dependencies

Existing protected banner reference verifies:
- https://pbs-test.moonetise.com/openrtb2/auction
- Account moonetise-test
- Stored impression moonetise-mobile-banner-test
- Fixed stored auction response moonetise-test-display-banner-3usd
- Existing banner unit above; reference diagnostics for 320x50 / banner / $3 / hb_pb=3.00.

Current PBS availability, bid w/h/type and MAX $3 TestPBS mapping have NOT been
queried by Codex. Actual preparation and actual adapter custom parameters must prove
them during operator testing. Missing dimensions/type/targeting are not invented.
An unavailable/incompatible fixture preserves MAX-only operation and an INCONCLUSIVE
positive report; no remote configuration is changed.

Synthetic override is SDK-global, debug-only, explicit, never saved, and cleared on
stop/dispose/detach. Clearing cannot retract an already-built request. Use one
scenario/format per fresh process; do not run other Prebid placements concurrently.
Prebid refresh interval is zero, refresh is stopped around fetch, delegate refresh
permission is false, and the unit is destroyed after bounded completion.

## Operator: Flutter, JVM, debug and release

Codex does not execute these commands. Preserve the existing $gradle pointing at
Gradle 8.13 gradle.bat. B5 has separate sources/build roots and no generated files
copied from B4. Supply ignored local.properties using the same machine-local SDK paths.

```powershell
# Run from the Prebid-Adapter-Research checkout root.
$repo = (git rev-parse --show-toplevel).Trim()
$fixture = "$repo/checkpoint-b5"
$flutter = Join-Path $env:USERPROFILE 'fvm/versions/3.44.9/bin/flutter.bat'
if (-not $gradle) { throw 'Set $gradle to the existing Gradle 8.13 gradle.bat first.' }
if (-not (Test-Path "$fixture/android/local.properties")) {
    Copy-Item -LiteralPath "$repo/checkpoint-b4/android/local.properties" -Destination "$fixture/android/local.properties"
}
Set-Location $fixture
& $flutter pub get
if ($LASTEXITCODE -ne 0) { throw 'pub get failed' }
& $flutter analyze
if ($LASTEXITCODE -ne 0) { throw 'analyze failed' }
& $gradle -p "$fixture/android" :replacement:testDebugUnitTest :app:verifyCheckpointB5Debug --rerun-tasks --no-build-cache --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'B5 JVM/debug verification failed; do not install' }
& $gradle -p "$fixture/android" :app:verifyCheckpointB5Release --rerun-tasks --no-build-cache --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'B5 release verification failed' }
```

Successful markers recorded above; the following remain reproduction requirements:
- CHECKPOINT_B5_DEBUG_GRAPH_PASS / CHECKPOINT_B5_DEBUG_APK_PASS.
- CHECKPOINT_B5_RELEASE_GRAPH_PASS / CHECKPOINT_B5_RELEASE_APK_PASS.
- Adapter definitions=1 in BOTH final APKs, across every DEX file.

Graph gates reject wrong pins, non-hosted/forked Flutter MAX or upstream Prebid adapter.
Debug DEX checks include original banner/fullscreen managers, MediationBannerView,
factory and interstitial controller. Release checks retain adapter runtime identity,
non-debuggable manifest, absence of synthetic response ID, merged R8 rule, and actual
banner manager/view definitions under their mapped R8 names. Core factory code may
be optimized by R8; debug/runtime evidence is not invented for release.
If a mapped type check fails, inspect mapping/DEX before changing any keep rules.
No blanket keep rules are added.

Optional graph/mapping inspection by operator:
```powershell
& $gradle -p "$fixture/android" :app:dependencies --configuration debugRuntimeClasspath
& $gradle -p "$fixture/android" :app:dependencies --configuration releaseRuntimeClasspath
Select-String -Path "$fixture/android/app/build/outputs/mapping/release/mapping.txt" -Pattern '^com\.applovin\.mediation\.adapters\.PrebidMaxMediationAdapter ->','^com\.applovin\.mediation\.adapters\.prebid\.managers\.MaxBannerManager ->','^org\.prebid\.mobile\.rendering\.bidding\.display\.MediationBannerView ->'
```

Release remains a minified, non-debuggable, locally debug-signed RESEARCH APK.
Synthetic implementation is absent; release UI/native entry accepts MAX-only mode.
Release packaging success is not release banner rendering acceptance.

Seven focused JVM classes are supplied: BannerScopeTest; FullscreenB4RegressionTest;
existing PendingSlotTest, FormatScopeTest, CycleGateTest, RewardObservationTest and
InterstitialSourceRegressionTest. They cover banner scope/type/size, mismatch without
consumption, one-use claims, expiry/cancel cleanup and source parity of fullscreen
helpers/load/show/reward forwarding. Compile of the adapter/manager verifies actual
MAX 13.3.1 listener signatures. No JVM test is described as live mediation.
Source tests require frozen sibling checkpoint-b3 and checkpoint-b4. All source reads
retain the Java 8-compatible readAllBytes + UTF-8 pattern.

## Operator: safe debug installation and installed preflight

WARNING: installing B5 can replace the existing com.moonetise.asterise.sample app.
Fresh acknowledgement is required for EACH installation. No automatic installation,
uninstall, downgrade, data clearing, signature bypass or alternate application ID.
Stop and report any installation conflict.

Use existing $adb and selected authorized device; keep private serials out of reports.

```powershell
$apk = "$fixture/android/app/build/outputs/apk/debug/app-debug.apk"
$ack = Read-Host 'B5 may replace com.moonetise.asterise.sample. Type INSTALL B5 to authorize THIS installation'
if ($ack -cne 'INSTALL B5') { throw 'Installation not authorized' }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; stop without uninstall/downgrade/data clearing' }
& $adb shell pm path com.moonetise.asterise.sample
& $adb shell am force-stop com.moonetise.asterise.sample
& $adb shell am start -n com.moonetise.asterise.sample/com.moonetise.research.checkpointb5.MainActivity
```

INITIALIZE scans installed sourceDir plus every splitSourceDir on a worker before
SDK initialization. It counts actual DEX class definitions, not string references.
Exactly one adapter and public compatible constructor/interface are required.
INSTALLED_APK_CHECK=SUCCESS records the accepted scan. APK_CLASS_CHECK_FAILED rejects
the run. Do not bypass it based on successful build/dependencyInsight alone.
Checkpoint A proved AGP may package duplicates without an automatic error.

## Android 10 physical scenarios

Run initially one scenario per fresh application process. Between scenarios:
```powershell
& $adb shell am force-stop com.moonetise.asterise.sample
& $adb shell am start -n com.moonetise.asterise.sample/com.moonetise.research.checkpointb5.MainActivity
```
This is a process restart, not uninstall/data clearing. No new installation is needed.

A — MAX-only:
1. Select NO PREBID DEMAND. Enter memory-only key; independently satisfy publisher
   privacy/CMP and MAX advertising prerequisites, then confirm authorization.
2. INITIALIZE. No Prebid initialization or auction occurs in A.
3. RUN BANNER TEST once. The actual official MAX widget mounts and loads.
4. Record genuine loaded/failed and optional revenue callbacks; inspect any filled
   ordinary MAX banner. TestPBS, if reached, must return prompt no-fill.
5. Dispose publisher banner after terminal result, confirm observed cleanup and copy
   the redacted report. No fill is guaranteed; skipped TestPBS stays INCONCLUSIVE.

B — synthetic 320x50:
1. Fresh process. Select SYNTHETIC; authorize approved development PBS testing and
   independently satisfied publisher privacy/CMP. INITIALIZE (Prebid then MAX).
2. RUN BANNER TEST once. Default path prepares, arms and mounts automatically after
   that explicit operator action; it does not auto-refresh or repeat preparation.
3. Require actual BID, CAPTURED=FRESH, ADAPTER_FORMAT=BANNER,
   FORMAT_SIZE_CHECK=MATCH, SLOT_CLAIM=MATCH, CACHE_POP=CONSUMED and delegation.
4. Require genuine publisher loaded, selected TestPBS and matching adapter
   CLASS_MATCH / AD_LOADED waterfall. Another winner is INCONCLUSIVE.
5. Inspect expected 320x50 synthetic creative inside the visible official MAX slot;
   press the operator visibility confirmation WHILE the banner is still mounted.
6. Observe revenue callback only if emitted. Do not click an ad just to create evidence.
7. DISPOSE PUBLISHER BANNER. Confirm widget removal/no observed corruption, and inspect
   actual BANNER_MANAGER_DESTROYED when emitted. Copy the redacted report.
   No public native-disposal acknowledgement is manufactured.
8. If unsupported LEADER format, wrong/missing bid dimensions, no matching tier,
   missing visibility or missing destroy evidence prevents PASS, retain INCONCLUSIVE
   and report the precise boundary. Do not change dashboard tiers or the renderer.

Cancellation supplement (separate fresh-process synthetic run):
- Enable Pause after preparation, optionally choose an eligibility window up to 30000ms.
- RUN, wait for prepared BID, then CANCEL PREBID ONLY before creating the MAX widget.
- Verify owned cache removal and cancellation markers. CREATE OFFICIAL MAX BANNER is
  still an explicit authorized MAX-only action; no cancelled response may be claimed.
- Dispose normally and preserve its original report (normally INCONCLUSIVE for positive
  SYNTHETIC acceptance). Record cancellation success separately.
- Cancellation during pending preparation also stops/seals the owned fetch; late
  completions cannot re-arm an eligible response.

Preparation is bounded at 2500ms, SDK auction timeout 2000ms, Prebid init 8s,
MAX host init 20s, native loading deadline 30s / host 31s.
No deadline grants fill, fabricates a callback or destroys a successfully claimed
creative. Unresolved load, backgrounding while pending, or detach requires restart.
A loaded banner is not treated as a fullscreen HIDDEN lifecycle.

Redacted live diagnostics, in a second operator terminal:
```powershell
& $adb logcat -v raw -s MoonetiseAdapterB5:I '*:S'
```
Prefer COPY REDACTED REPORT. If saved, use local ignored .jsonl/.log files.
Do not export broad SDK logs, raw requests/waterfalls, keys, IDs or revenue amounts.
Preserve each automatic export unchanged; operator-reviewed findings are separate.

## Limits and stop boundary

B5 operator-reviewed acceptance is recorded above. Codex did not rerun builds/tests/device requests for closure.
The first checkpoint deliberately allows one view/load per process, including normal
disposal. A subsequent explicitly authorized load is tested in a NEW process with
fresh preparation. Same-process dispose/recreate reliability is NOT proven; the
official widget has no public native-destroy completion acknowledgement or unique
publisher-load token. Do not introduce a refresh scheduler to hide this limitation.

Auto-refresh/remote refresh, multi-format concurrency, production CMP synchronization,
standalone Flutter delivery, Google mediation, iOS and banner memory-leak profiling
are outside B5. Existing interstitial/rewarded runtime milestones remain B3/B4 evidence;
source parity in B5 does not assert new fullscreen device acceptance.

Operator evidence has been reviewed and B5-only Git closure authorized. The committed
snapshot and remote verification form the final Central Integration handover.
No implementation or follow-up work is authorized after closure without a separate request.
