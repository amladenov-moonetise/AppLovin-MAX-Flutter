# Moonetise B4 — Android rewarded E2E research

Status: CLOSED — OPERATOR-REVIEWED PASS for the bounded Android rewarded E2E milestone.
Branch: research/prebid-adapter-checkpoint-b4.
Frozen parent: 70540f41dd5295fde37b766b4420ec1079a22f13.
Completed A/B1/B2/B3 and their original reports remain unchanged.

## Completed acceptance — 2026-11-22

This is a separate operator-reviewed finding. Original exported automatic
INCONCLUSIVE classifications and reports are preserved unchanged. They are not
rewritten as automatic PASS. Runtime observations below were supplied and reviewed
by the operator; Codex inspected existing build/test outputs without rerunning them.

| Evidence | Reviewed outcome |
| --- | --- |
| Flutter pub get | Operator-reported PASS |
| Flutter analyze | Operator-reported PASS; no issues |
| Debug JVM and frozen-source regression | PASS; existing XML reports show 19 tests, 0 failures, 0 errors, 0 skipped across five suites |
| Debug graph | MAX 13.3.1; Prebid 3.4.1; upstream adapter absent |
| Debug final APK | Exactly one replacement adapter definition |
| A — MAX-only rewarded | Enhanced adapter invoked and returned no-fill; AppLovin loaded, displayed and hidden normally; genuine publisher reward callback observed |
| B — synthetic rewarded | Actual preparation BID; SLOT_CLAIM=MATCH; CACHE_POP=CONSUMED; ADAPTER_DELEGATED=SUCCESS |
| B — MAX selection/lifecycle | Genuine LOADED; enhanced adapter waterfall CLASS_MATCH / AD_LOADED; genuine DISPLAYED, exactly one official applovin_max publisher reward callback, and HIDDEN |
| B — operator visual review | Synthetic TestPBS video confirmed; tablet rotation during playback succeeded, with a close button afterward |
| Entitlements | No game currency or entitlements granted |
| Release verification | Existing :app:verifyCheckpointB4Release completed BUILD SUCCESSFUL; 136 tasks (135 executed, 1 up-to-date) |

### Inspected local build evidence

Local Gradle 8.13 daemon output, daemon-665652.out.log:
- Lines 735 / 747: CHECKPOINT_B4_DEBUG_GRAPH_PASS and CHECKPOINT_B4_DEBUG_APK_PASS.
  The latter records adapter definitions=1.
- Lines 1665 / 1675: the following release markers were located, not inferred:

```text
CHECKPOINT_B4_RELEASE_GRAPH_PASS: MAX=13.3.1; Prebid=3.4.1; upstream absent
CHECKPOINT_B4_RELEASE_APK_PASS: adapter definitions=1
BUILD SUCCESSFUL in 1m 22s
136 actionable tasks: 135 executed, 1 up-to-date
```

The full original APK marker names the local checkpoint-b4/android/app/build/outputs/apk/release/app-release.apk.
The success/task-count lines are 1685–1686. Only the safe marker fields are transcribed
here; local machine paths and complete generated logs are not committed.
The existing release output-metadata.json identifies the same application ID,
release variant and minSdk26. These are packaging/build observations, not release
rewarded rendering runtime acceptance.

The five inspected generated JUnit XML reports are under
android/replacement/build/test-results/testDebugUnitTest:
CycleGateTest (7), FormatScopeTest (1), InterstitialSourceRegressionTest (2),
PendingSlotTest (6), RewardObservationTest (3). Generated XML/HTML, APKs, logs,
.cxx, local.properties and build directories remain ignored and uncommitted.
The narrow Java 8 test compatibility correction is included:
new String(Files.readAllBytes(path), StandardCharsets.UTF_8), retaining path resolution,
UTF-8 decoding, newline normalization, assertions and exception handling.

### Closure limits

- No release rewarded rendering runtime test.
- No multi-cycle rewarded validation.
- No server-side reward verification or fraud-prevention acceptance.
- No production Sourcepoint/TCF synchronization.
- No standalone Flutter package integration or pub.dev delivery acceptance.
- No banner implementation.
- No iOS rewarded acceptance.
- Rotation is the bounded operator observation above, not comprehensive lifecycle testing.

The original single-flight/publisher-cooperation constraints and privacy boundaries
remain. No SDK versions, renderer behavior, MAX/PBS settings or completed fixtures
were changed for closure. No new build, device test or advertising request was run.


## Frozen integration

- Official hosted applovin_max 4.5.1, unchanged; MAX 13.3.1; Prebid SDK/Core 3.4.1.
- minSdk26; B3 toolchain retained. No upgrades to suppress warnings.
- Application ID com.moonetise.asterise.sample; rewarded 691ff46cc460ce8e.
- Existing TestPBS CUSTOM_NETWORK_SDK-70429; no dashboard changes or second ladder.
- Original com.applovin.mediation.adapters.PrebidMaxMediationAdapter with public
  AppLovinSdk constructor; upstream adapter artifact excluded.
- Same-name adapter adds rewarded lookup/load/show/destroy using original Prebid
  MaxRewardedManager/controller. B3 interstitial path remains intact in this fixture.
- No banner/native implementation; no production Flutter API or currency awards.

See SOURCE_PROVENANCE.md for exact pinned sources, approved fixture and modifications.
See REPORT_FORMAT.md for evidence and acceptance rules.
See FILES.md for the complete added source inventory.

## Minimal contract

One rewarded scenario per fresh process, one preparation, one publisher MAX load,
one explicit Show. No overlap, retries, automatic display or next-cycle rearming.
The official Flutter plugin owns MAX objects and callbacks. Native code never
acquires a MaxRewardedAd.

RewardedDemandBridge has its own fixed rewarded ID and PendingSlot. Rewarded method
entry selects the format; MAX does not provide an independent load token.
The original ParametersChecker compares MAX custom parameters including hb_pb.
Mismatch preserves demand; one matching atomic claim removes slot eligibility,
and the original manager pops the actual Prebid cache. Expiry is monotonic, default
5000ms, configurable 1000–30000ms, capped by remaining SDK response lifetime.
There is no PBS request/wait inside adapter loading.

Only unused owned cache is cleared on genuine MAX loaded/failed, detach or failure.
Preparation cleanup never destroys a claimed controller or loaded creative.
AppLovin may win before TestPBS is evaluated; a late TestPBS call then gets no-fill.
This is INCONCLUSIVE for the positive path, not permission to force the waterfall.

NO_DEMAND does not initialize Prebid or perform an auction. Synthetic mode initializes
Prebid before MAX, with SDK logging/PBS debug disabled. Adapter initialization preserves
original COPPA propagation and completes promptly with ready/unknown/failure.
No CMP values are written. The operator checkbox is authorization, not fabricated consent.
When preparation fails, times out or returns no bid, the publisher still loads MAX once.
MAX controls other-network scheduling/fill; fallback fill is not guaranteed.

## Operator: build and source regression

Codex does not execute the following commands. Use existing Java/Android/Flutter setup.
The completed B3 ignored local.properties is machine-local input, not source/evidence.

```powershell
$repo = 'C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research'
$fixture = "$repo/checkpoint-b4"
$flutter = 'C:/Users/AMlad/fvm/versions/3.44.9/bin/flutter.bat'
# Preserve your existing $gradle pointing to Gradle 8.13 gradle.bat.
if (-not $gradle) { throw 'Set $gradle to the existing Gradle 8.13 gradle.bat first.' }
if (-not (Test-Path "$fixture/android/local.properties")) {
    Copy-Item -LiteralPath "$repo/checkpoint-b3/android/local.properties" -Destination "$fixture/android/local.properties"
}
Set-Location $fixture
& $flutter pub get
if ($LASTEXITCODE -ne 0) { throw 'pub get failed' }
& $flutter analyze
if ($LASTEXITCODE -ne 0) { throw 'analyze failed' }
& $gradle -p "$fixture/android" :replacement:testDebugUnitTest :app:verifyCheckpointB4Debug --rerun-tasks --no-build-cache --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'B4 debug/JVM verification failed; do not install' }
```

Required debug task result: CHECKPOINT_B4_DEBUG_APK_PASS, definitions=1.
Graph verification rejects forked/non-hosted Flutter MAX, wrong pins or upstream adapter.
Debug APK inspection also counts original rewarded manager, factory and controller definitions.
Do not infer uniqueness from dependencyInsight or AGP duplicate checking alone.

Focused JVM sources cover slot mismatch/expiry/cleanup/atomic claims, format isolation,
reward-before/after-hidden and duplicate evidence. Existing B3 slot/generation tests
are retained. InterstitialSourceRegressionTest compares frozen B3 and B4 helper/provider
source and interstitial adapter load/show bodies after research naming normalization.
It requires the committed sibling checkpoint-b3 directory; it does not run ads or
prove renderer operation. JVM tests and builds have NOT been run by Codex.

Optional reuse of B3's minified non-debuggable research release verification:

```powershell
& $gradle -p "$fixture/android" :app:verifyCheckpointB4Release --rerun-tasks --no-build-cache --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'B4 release verification failed; do not install' }
& $gradle -p "$fixture/android" :app:dependencies --configuration releaseRuntimeClasspath
```

Release remains a locally debug-signed research APK, not a production release target.
It permits MAX-only mode; synthetic implementation/fixture is debug-source-set only.
The release check verifies one original-name adapter, merged non-debuggable manifest,
synthetic response absence and R8 adapter mapping/keep rule. Preserve Prebid's own
consumer rules/resources. Manager/core implementation names may be obfuscated by R8.
No blanket Prebid keep rules are added. Do not claim rewarded release rendering PASS
from source or packaging alone.

## Operator: installation requires fresh acknowledgement EVERY time

WARNING: installing B4 can replace the current Asterise sample with the same application ID.
No automatic installation is performed. Do not uninstall, downgrade, clear application data,
change the application ID or bypass signature/version conflicts. Stop and report conflicts.

After successful final-APK verification, perform this block separately for each installation.
Use the already selected authorized device; do not put private serials into reports.

```powershell
$apk = "$fixture/android/app/build/outputs/apk/debug/app-debug.apk"
$ack = Read-Host 'Installing B4 may replace com.moonetise.asterise.sample. Type INSTALL B4 to acknowledge this installation'
if ($ack -cne 'INSTALL B4') { throw 'Installation not authorized' }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; stop without uninstall/downgrade/data clearing' }
& $adb shell pm path com.moonetise.asterise.sample
& $adb shell am force-stop com.moonetise.asterise.sample
& $adb shell am start -n com.moonetise.asterise.sample/com.moonetise.research.checkpointb4.MainActivity
```

Set $adb to the existing platform-tools adb executable if it is not already set.
INITIALIZE scans sourceDir plus every splitSourceDir on a worker before SDK initialization.
The scan counts actual DEX class_def entries, not string references. Any count other
than one rejects the run with APK_CLASS_CHECK_FAILED. Do not continue without successful
installed base/split uniqueness preflight. This is separate from build-time APK counting.

## Physical Android 10: bounded scenarios

Run each scenario in a fresh process. Force-stop/relaunch between A and B, never clear data.
Do not use other placements/normal Prebid auctions in this process: the SDK synthetic
override is global. Clearing it cannot retract in-flight work.

A — MAX-only fallback:
1. Select A — NO PREBID DEMAND.
2. Enter authorized development MAX key (memory-only; input clears on Initialize).
3. Confirm independently satisfied publisher privacy/CMP and advertising authorization.
4. INITIALIZE, then RUN REWARDED TEST once.
5. Expect a genuine MAX load result. If TestPBS is invoked, expect PROVIDER_MISS and
   ADAPTER_NO_FILL, then inspect ordinary MAX mediation evidence.
6. If an ad loads, optionally SHOW REWARDED explicitly. Observe genuine displayed,
   reward if supplied by MAX, and hidden independently. No guaranteed fill/reward.
7. COPY REDACTED REPORT; preserve exactly as emitted.

B — synthetic rewarded:
1. Fresh process; choose B — SYNTHETIC REWARDED DEMAND (debug only).
2. Authorize the fixed development fixture and already-satisfied privacy policy;
   INITIALIZE, then RUN REWARDED TEST once.
3. Require PREPARATION_RESULT=BID, SLOT_CLAIM=MATCH, CACHE_POP=CONSUMED,
   ADAPTER_DELEGATED=SUCCESS, genuine MAX loaded, and TestPBS CLASS_MATCH/AD_LOADED.
4. SHOW REWARDED only after the genuine loaded callback. Visually inspect the creative.
5. Observe DISPLAYED, actual official publisher reward callback and HIDDEN separately.
   The UI shows the reward-callback count. A reward may arrive after hidden; listeners
   remain installed and no second load is allowed. No timer/close creates a reward.
6. Confirm expected visible creative using the operator button, then COPY REDACTED REPORT.
   If reward is not observed, preserve INCONCLUSIVE; do not manufacture it or replay callbacks.
7. If another network wins or TestPBS is never reached, preserve INCONCLUSIVE and do not
   change the dashboard/tiers. Reference fixture evidence does not prove a B4 reward.

The loading deadline is 30s native / 31s host; preparation deadline is 2500ms
with SDK timeout 2000ms; Prebid init deadline 8s and MAX host init deadline 20s.
An unresolved load requires restart. No deadline expires a successfully loaded creative
or decides whether a real reward will arrive.

Optional redacted Logcat capture (second PowerShell window, operator-controlled):
```powershell
& $adb logcat -v raw -s MoonetiseAdapterB4:I '*:S'
```
Capture only this fixed-schema tag if saving locally to an ignored .jsonl/.log.
Do not collect/export broad SDK logs or raw waterfalls. Prefer COPY REDACTED REPORT.
Preserve original automated classification. Add any reviewed operator finding separately.

## Acceptance and limits

PASS A: MAX initialized; replacement reached; prompt no-fill; genuine publisher load
result and ordinary other-network mediation evidence. Fill is not mandatory.
PASS B: approved actual BID, one matched claim/pop/delegation, TestPBS selected and
loaded in waterfall, explicit Show, displayed, exactly one genuine publisher reward,
hidden and positive operator visibility. No currency is granted.
FAIL: binary/class incompatibility or duplicate definitions; repeated claim/pop;
duplicate publisher reward callbacks; expected creative explicitly rejected.
INCONCLUSIVE: missing invocation/selection/reward/visibility evidence, preparation or
network/configuration failure, another winner, timeout or unavailable diagnostics.
The completed operator-reviewed findings are recorded separately above; original automatic classifications remain unchanged.

One-device V1 only. No unique MAX per-load token; unrelated same-unit calls are
indistinguishable. Cooperative publisher integration is required. This does not test
server-side rewards, fraud prevention, real CMP synchronization, multi-format concurrency,
standalone pub.dev packaging, banner/native, iOS or Google bidding.

Codex performed source/documentation review and the authorized repository closure only.
Builds, JVM execution, advertising requests and installations were performed by the operator;
none were rerun for closure. No B5 or production integration is authorized.
