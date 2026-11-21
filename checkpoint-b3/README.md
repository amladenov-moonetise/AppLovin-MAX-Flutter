# Checkpoint B3 — Android interstitial reliability

CLOSED as an OPERATOR-REVIEWED V1 ANDROID INTERSTITIAL RELIABILITY MILESTONE.
This is bounded research acceptance, not an aggregate automatic PASS or production approval.
All exported automatic classifications and evidence remain exactly as originally emitted.

Branch research/prebid-adapter-checkpoint-b3, created from frozen B2
adb68cec604327799b5b7fa1dacde917cddb5ea5. All B3 files live in checkpoint-b3/.
Completed A/B1/B2 and their original evidence remain unchanged. B2's exported
INCONCLUSIVE reports and separate operator-reviewed PASS are not reclassified here.
Protected Asterise release/android-ads 311cde2 and MAX release/android-max 6d31ab6 remain unchanged.

## Completed operator-reviewed findings

Evidence source: the operator's reported build and runtime acceptance in this conversation.
The table records separate operator-reviewed findings; it does not replace the classification
in any exported report. No report/run ID, timestamp, missing predicate or automatic result
has been invented or normalized. Captures are not added to this source commit.

| Bounded objective | Operator-reviewed finding |
|---|---|
| Flutter analysis and Gradle verification | PASS: Flutter analyze and debug/release Gradle verification completed successfully. |
| Final APK adapter uniqueness | PASS: debug and release APKs each contain exactly one replacement adapter definition. |
| Release initialization and invocation | PASS: the minified, non-debuggable release APK initialized official MAX and the enhanced adapter; TestPBS adapter invocation returned prompt NO_FILL. |
| Release ordinary advertising | PASS: AppLovin loaded, displayed and hidden successfully. |
| Sequential debug loading | PASS: three publisher-owned MAX loads completed within one application process. |
| Debug Cycle 1 | PASS: the synthetic TestPBS creative loaded and rendered. |
| Debug Cycles 2 and 3 | PASS for ordinary MAX continuation: both completed through AppLovin. Fresh preparation was observed in Cycle 3; subsequent TestPBS contribution remains INCONCLUSIVE. |
| MAX-first initialization | PASS for MAX-only fallback and later Prebid preparation. Positive Prebid rendering after MAX-first remains INCONCLUSIVE. |
| Forced local preparation failure/timeout | PASS: normal MAX loading was preserved. These local seams do not establish a real remote PBS timeout. |
| Cancellation and Moonetise-only authorization withdrawal | PASS: unused demand was cleared and MAX loading was preserved. This does not simulate or establish genuine CMP consent withdrawal behavior. |
| Expiry-specific adapter rejection | INCONCLUSIVE. |
| Live multi-tier matching | INCONCLUSIVE; no live multi-tier PASS is asserted. |

The milestone establishes optional-demand coexistence, sequential MAX progress, ordinary
fallback, one successful synthetic rendering path and release identity/invocation under R8
for the tested configuration. It does not establish positive Prebid contribution on every
later cycle, positive rendering after MAX-first, expiry-specific adapter rejection, or live
multi-tier matching. SDK-level matching evidence must not be relabelled as live mediation.

Original automatic reports remain unchanged, including any PASS/FAIL/INCONCLUSIVE values
and missingEvidence fields they emitted. No aggregate automatic classification is asserted.
Operator review closes the bounded V1 milestone with the unresolved items above explicitly
retained. The original scenario procedures and automatic criteria below remain as research
instructions; their presence does not turn an unverified sub-objective into a PASS.

Codex did not run Gradle, Flutter, JVM tests, ADB, device tests or advertising requests for
this closure, and did not edit implementation, classifier logic, SDK dependencies or
MAX/PBS configuration. The following procedures are retained for reproducibility, not
instructions to repeat tests as part of this documentation-only closure.

## Pins and ownership

- Application ID com.moonetise.asterise.sample; minSdk26; existing toolchain unchanged.
- Official hosted applovin_max 4.5.1; native MAX strictly13.3.1; Prebid SDK/Core strictly3.4.1.
- Interstitial d236de754b83a168; TestPBS / CUSTOM_NETWORK_SDK-70429; one existing price ladder.
- Original class com.applovin.mediation.adapters.PrebidMaxMediationAdapter.
- Existing approved PBS endpoint https://pbs-test.moonetise.com/openrtb2/auction,
  account moonetise-test, stored impression moonetise-mobile-interstitial-test,
  debug-only fixed response moonetise-test-video-interstitial-3usd.
- No dashboard, fixture, dependency-version or production Flutter changes.
- No rewarded, banner, Google, iOS, automatic Show or automatic publisher retry.

Official Flutter MAX owns initialization, native MAX objects, listeners, load/show and revenue.
The native research bridge owns only preparation, the optional slot and bounded diagnostics.
It never constructs/obtains a MAX fullscreen object. Publisher privacy/CMP and independent
MAX advertising permission must already be satisfied. The checkbox is NOT consent.

## Minimal changes from B2

Three explicit cycles maximum per process. Each cycle owns a new PendingSlot and generation.
CycleGate permits the next cycle only after a genuine MAX load failure or after explicit
Show and genuine Hidden. A loaded-but-unshown ad blocks the next cycle. There is no queue.
An SDK-only tier probe completes without claiming a MAX load occurred.

Preparation delegates, timeout closures and fetch callbacks carry their originating cycle.
Old callbacks cannot arm/complete a new slot. A local MAX timeout or unresolved presentation
requires process restart; it does not cancel MAX internals. No re-arm after unknown completion.
Asterise's generation is NOT a MAX request token. An unrelated same-unit load, or an SDK
invocation from an earlier load arriving during a newer armed load, remains indistinguishable
through public MAX parameters. This bounded cooperative test is not a solution to that API gap.

Prebid/MAX initialization and publisher listeners are set once per process. MAX-first starts
with one NO_DEMAND cycle before Prebid initialization; the next explicit cycle initializes
Prebid once. Original B2 adapter initialization/COPPA behavior is retained. B3 records actual
INITIALIZED_UNKNOWN/SUCCESS/FAILURE separately; it does not change recovery behavior.

Original ParametersChecker, ListenersCreator, manager and Prebid Core renderer are reused.
Only the existing manager cache-pop marker is renamed B3. Synthetic capture remains video,
price3 and hb_pb=3.00. Original custom-parameter comparison is unchanged. Claim remains
synchronous/bounded, consumes once and never waits for PBS. Mismatching tiers do not consume.

SDK preparation timeout: 2000ms plus a 2500ms fixture deadline. Prebid initialization:8s;
official MAX initialization:20s plus3s readiness check. Response eligibility defaults5000ms,
adjustable1000–30000ms and shortened by positive bid expiry. EXPIRE intentionally waits
past that window after a real prepared bid. MAX loading is bounded by a30s observation;
the entire cycle observation is180s (Flutter closes at175s). Timeouts require restart.
There is no background orchestration; the existing B2 one-shot APK scanner stays off the UI thread.

Synthetic override exists only in android/app/src/debug/.../ResearchPreparation.java.
It is fixed/allowlisted, cleared on preparation completion/cleanup/detach, never persisted.
Clearing cannot retract a built/sent request. NO_DEMAND/forced fault cycles perform no auctions,
so the primary sequence does not mix synthetic and ordinary PBS auctions in one process.

## Build preparation (operator only)

Use existing Flutter3.44.9, Gradle8.13, JDK17, SDK35 as B2. Do not upgrade to silence warnings.
Provide ignored checkpoint-b3/android/local.properties with actual flutter.sdk and sdk.dir.
Do not copy .dart_tool/build/.gradle/.cxx/generated registrants from B2.
No SDK key, signing password or consent configuration belongs in Git or command arguments.
Enter the development MAX SDK key in the obscured runtime field only.

PowerShell: preserve your existing $gradle variable pointing to Gradle8.13 gradle.bat.

    $fixture = 'C:/Users/AMlad/AndroidStudioProjects/Prebid-Adapter-Research/checkpoint-b3'
    $flutter = 'C:/Users/AMlad/fvm/versions/3.44.9/bin/flutter.bat'
    $adb = 'C:/Users/AMlad/AppData/Local/Android/sdk/platform-tools/adb.exe'
    Set-Location $fixture
    & $flutter pub get
    & $flutter analyze
    & $gradle -p "$fixture/android" :replacement:testDebugUnitTest :app:verifyCheckpointB3Debug --rerun-tasks --no-build-cache --stacktrace
    & $gradle -p "$fixture/android" :app:verifyCheckpointB3Release --rerun-tasks --no-build-cache --stacktrace

Require:
- CHECKPOINT_B3_DEBUG_GRAPH_PASS and CHECKPOINT_B3_DEBUG_APK_PASS: adapter definitions=1.
- CHECKPOINT_B3_RELEASE_GRAPH_PASS and CHECKPOINT_B3_RELEASE_APK_PASS: adapter definitions=1.
- All focused JVM tests pass. No claim is made that Codex ran them.

The preserved six PendingSlot tests cover matching/mismatch, expiry, revocation/late completion,
cleanup and concurrent claims. CycleGateTest adds sequential reuse, loaded-ad blocking,
genuine-failure recovery, stale generations, timeout/restart and presentation completion tests.

Graph checks resolve the explicit variant runtime graph only at task execution. Both reject
upstream prebid-mobile-sdk-max-adapters, wrong SDK pins and non-hosted/forked Flutter MAX.
Every final APK classes*.dex is counted. AGP's duplicate-class checking is not sufficient.

## Release/R8

Release is non-debuggable, minified, and signed ONLY with the machine-local research debug
signing config; no production key is used. Resource shrinking stays off to avoid adding a
second variable. consumer-rules.pro retains the original adapter and all members/constructor.
No blanket Prebid package keep rules were added. Prebid Core/renderer dependencies remain.

Release verification reads the merged manifest, checks unique adapter DEX identity and the
unchanged runtime name in R8 mapping/configuration reports. It rejects the debug synthetic
fixture ID in release DEX. Inspect app/build/outputs/mapping/release/configuration.txt,
mapping.txt and usage.txt when assessing renderer/controller optimization. Internal classes
can be renamed/inlined; absence of their original DEX names is not automatically a defect.
Do not claim positive release Prebid rendering from the MAX-only release run.

Release native code rejects every scenario except NO_DEMAND. Its ResearchPreparation source
contains no setter/fixture/auction implementation. Debug-only synthetic source is not compiled
into release. The installed preflight checks all base/split DEX definitions plus the adapter's
public AppLovinSdk constructor and superclass/interface compatibility without constructing it.
Release UI and diagnostic build labels do not turn the app debuggable.

## Fresh installation acknowledgement — every APK

WARNING: installing either variant REPLACES com.moonetise.asterise.sample on that Android user.
Use an isolated device/user. Back up operator data independently. Never uninstall, downgrade,
change app ID or clear app data to work around a signature/version conflict.

Choose ONE verified APK. Get fresh acknowledgement separately before installing debug or release:

    $variant = 'debug' # or 'release', after its verification task succeeds
    $ack = Read-Host "This replaces the existing Asterise sample with B3 $variant. Type INSTALL B3"
    if ($ack -ne 'INSTALL B3') { throw 'Installation cancelled' }
    & $adb install -r "$fixture/android/app/build/outputs/apk/$variant/app-$variant.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Install failed; stop, do not uninstall/downgrade/clear data' }

Gradle installDebug/installRelease additionally require
-Pb3AcknowledgeSampleReplacement=YES after that fresh operator acknowledgement, and depend on
their respective verification tasks. No install task has been executed by Codex.

## Physical Android10 procedure

Keep SDK keys/logs/private configuration outside Git. Disable verbose SDK logging. Start each
separate session with a force-stop (never hot reload/restart), then launch:

    & $adb shell am force-stop com.moonetise.asterise.sample
    & $adb shell am start -n com.moonetise.asterise.sample/com.moonetise.research.checkpointb3.MainActivity

### Session1 — primary sequence, DEBUG

1. Select Prebid first and the primary sequence checkbox. Enter the authorized development
   SDK key, confirm already-satisfied privacy/MAX prerequisites, INITIALIZE SESSION ONCE.
2. RUN EXPLICIT CYCLE1: one fresh synthetic preparation and one official MAX load.
3. If TestPBS wins, optionally CLEAR UNUSED PREPARATION, then explicitly SHOW. Require real
   Displayed/Hidden and visibly confirm the expected video. Clearing must not break Show.
4. COPY cycle1 report before advancing. If another network wins, record INCONCLUSIVE for the
   synthetic objective; show/hide normally, acknowledge that outcome and preserve its report.
5. RUN cycle2: NO_DEMAND, no PBS auction. Require provider no-fill and ordinary MAX mediation.
   Show/hide any loaded ad explicitly, or accept a genuine load failure. Export cycle2.
6. RUN cycle3: a NEW synthetic preparation, claim and cache pop; explicit Show/Hidden/visibility.
   Export cycle3. Verify common runId, cycles1/2/3, one fresh capture/pop in1 and3, none in2.
7. No cycle4. No retry after an unresolved timeout. All three cycles must complete for the
   primary sequence to pass; a loaded-but-unshown cycle correctly prevents progression.

This is a deliberately absent-demand middle cycle, not evidence of a real PBS NO_BIDS.
Natural SDK NO_BIDS/TIMEOUT/errors are recorded separately if the actual synthetic fetch fails.

### Session2 — MAX-first, DEBUG

Fresh process. Select MAX first, primary OFF, NO_DEMAND. Initialize once and run cycle1 BEFORE
Prebid initialization. TestPBS must actually be invoked with INIT_STATUS=INITIALIZED_UNKNOWN;
absence of that evidence is INCONCLUSIVE. Complete/export cycle1.
Select SYNTHETIC for cycle2. The next explicit cycle initializes Prebid once, prepares demand
and loads through the same official MAX integration. Require matching claim, genuine
TestPBS load, display/hidden and visible confirmation. Do not reinitialize MAX or modify the
adapter initialization policy if recovery is absent; preserve INCONCLUSIVE/failure evidence.

### Session3 — forced preparation failures, DEBUG

Fresh Prebid-first/custom session. Select PREP_FAILURE for cycle1 and PREP_TIMEOUT for cycle2.
These are labelled LOCAL fault seams: zero auctions, no fabricated response or targeting.
Timeout waits2500ms; failure completes immediately. In each, require one ordinary MAX load
despite absent Moonetise demand, adapter no-fill when reached, and genuine completion.
Show/hide loaded ads before the next cycle. Export each independently.
A local forced timeout is NOT proof of a real remote PBS timeout.

### Session4 — cancellation/expiry/withdrawal, DEBUG

Fresh Prebid-first/custom session, eligibility1000ms if desired:
- CANCEL: obtain a real prepared synthetic bid, revoke it before load, verify owned cache
  removal and subsequent no-fill/ordinary MAX loading.
- EXPIRE: prepare a real bid and wait past eligibility before issuing the one MAX load.
  Require PROVIDER_MISS=EXPIRED, zero matching claims, owned cleanup and normal mediation.
- WITHDRAW: prepare a real bid, withdraw Moonetise-only authorization before load. Verify
  unclaimed cache cleanup and no claim while independent publisher MAX authorization remains.
Complete/show-hide/export each before proceeding. No actual CMP preference is changed.
If preparation produced no bid, these positive cleanup tests remain INCONCLUSIVE.

### Optional Session5 — existing tiers, DEBUG

First corroborate an existing non-3.00 TestPBS interstitial hb_pb tier using existing account
configuration; never create or guess one. If unavailable, mark multi-tier INCONCLUSIVE.
Select TIER_CHECK, enter that verified tier and confirm the attestation checkbox.
The fixture uses a real prepared response, original checker/provider and public parameter
interface: mismatch preserves cache, 3.00 matches/pops once, second claim fails.
No MAX load, creative, impression or simulated MAX callback is produced by this SDK probe.
Its report says scope=SDK_LEVEL_ONLY; it is not a live waterfall result.
For real SYNTHETIC cycles, liveMultiTier can pass only if genuine mediated invocations show
MISMATCH before MATCH. Ordering/other winners may leave this INCONCLUSIVE.

### Session6 — release MAX-only

Install the verified RELEASE APK with fresh acknowledgement. Fresh process. The screen
offers only NO_DEMAND and no Prebid initialization/synthetic control. Initialize official
MAX, run one explicit load, observe adapter no-fill and ordinary MAX mediation, explicitly
Show/Hidden if loaded, then export. Confirm build=RELEASE and non-debuggable manifest.
This validates runtime identity/fallback under R8, not positive Prebid release rendering.

## Reports and decisions

COPY SELECTED REDACTED REPORT exports a JSON header then that cycle's JSONL, including
session-level initialization records. Earlier exports remain operator-held snapshots.
Use REPORT_FORMAT.md; do not concatenate different runIds or relabel the original output.
Storage is bounded at640 fixed-schema records / three cycles; overflow is INCONCLUSIVE.
No SDK keys, response IDs, raw requests/responses/errors, consent strings, identifiers or
revenue amounts are exported. Any diagnostic failure forces the UI report INCONCLUSIVE
while preserving nativeClassification separately.

PASS requires scenario-specific evidence and common pinned graph/unique-class prerequisites.
FAIL includes multiple matching claims/cache pops, failed owned cleanup, operator-confirmed
wrong/missing expected TestPBS creative, or proven binary/class packaging failure.
Other gaps are INCONCLUSIVE with missingEvidence listed. A genuine MAX no-fill is not
automatically FAIL. Other networks can win normally and make Prebid-positive tests inconclusive.
Observe initialization and construction as process evidence; they need not repeat per cycle.

Limits: public MAX callbacks/adapter parameters do not carry the internal generation.
Unexpected cached-ready ads or unresolved old loads require restart. Late native MAX
invocations indistinguishable from a newer same-unit load cannot be excluded by this fixture.
The operator-reviewed findings above establish only the tested paths; TestPBS eligibility,
ordering and fill are not guaranteed for future loads. Later-cycle Prebid contribution,
positive rendering after MAX-first, expiry-specific rejection and live multi-tier matching
remain INCONCLUSIVE. Positive release Prebid rendering, production Flutter integration and
production consent/lifecycle hardening remain outside this milestone. No SDK/settings
redesign or production integration is authorized by closing B3.
