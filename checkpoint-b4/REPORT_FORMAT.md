# B4 rewarded acceptance evidence

B4 is closed as an operator-reviewed Android rewarded E2E PASS; see README.md for
completed findings and inspected build evidence. Original exported automatic
INCONCLUSIVE classifications remain unchanged. This document retains the automatic
report schema/criteria; the separate operator finding does not rewrite emitted reports.

COPY REDACTED REPORT exports a JSON header followed by the fixed-schema JSONL
events for the session and single cycle. Store A and B independently in local ignored
.jsonl/.log files. Do not rewrite an emitted classification. Record any later
operator-reviewed finding separately with its scope and original evidence reference.

Header fields:
- runId (random research session), cycle, scenario (NO_DEMAND or SYNTHETIC).
- classification: PASS / FAIL / INCONCLUSIVE; missingEvidence lists absent requirements.
- scope=MAX_RUNTIME; liveMultiTier remains a separate observation.
- hostDiagnosticsHealthy; nativeClassification only if host diagnostics caused downgrade.
- publisherRewardCallbacks; rewardEvidenceSource=OFFICIAL_FLUTTER_MAX_LISTENER.
- entitlementsGranted=false. No amounts, labels, raw revenue or entitlement IDs.

Events include timestamp, monotonic elapsedMs, format=REWARDED, build,
original adapter class, MAX version, enumerated result/category/network/loadState.
No raw errors, request/response JSON, cache IDs, SDK keys, consent strings or
device/advertising identifiers are exported.

## A — NO_DEMAND

Require installed base/split uniqueness, official MAX initialization, replacement
construction/initialization/invocation, PREPARATION_RESULT=NO_BID from the no-auction
branch, no matching claim, PROVIDER_MISS=MISSING, ADAPTER_NO_FILL=MISSING, genuine
publisher LOADED or FAILED callback, TestPBS matching adapter AD_FAILED_TO_LOAD
and ordinary other-network mediation evidence. Loading/display from another network
is acceptable. A genuine no-fill waterfall can establish continuation without fill.
Show is optional for A; a ready ad need not be shown to establish load continuation.
If TestPBS is skipped, missing invocation evidence is INCONCLUSIVE, not failure.

## B — SYNTHETIC

Require installed uniqueness, official MAX initialization, replacement invocation;
actual PREPARATION_RESULT=BID and one CAPTURED=FRESH;
SLOT_CLAIM=MATCH once; CACHE_POP=CONSUMED once;
ADAPTER_DELEGATED=SUCCESS; genuine publisher HOST_LOAD_RESULT=LOADED;
HOST_SELECTED_NETWORK=TESTPBS; HOST_WATERFALL CLASS_MATCH / TESTPBS / AD_LOADED;
one explicit HOST_SHOW_REQUESTED; genuine HOST_DISPLAYED; exactly one HOST_REWARD
from official Flutter MAX's onAdReceivedRewardCallback; genuine HOST_HIDDEN; and
HOST_OPERATOR_VISIBLE=YES from explicit operator review.

Reward may precede or follow HIDDEN. Closing, clocks, display, cache claims, synthetic
PBS completion and renderer readiness never generate publisher reward observations.
Missing reward leaves B INCONCLUSIVE; do not generate an event to satisfy acceptance.
This is callback delivery validation, not server-side completion or entitlement verification.
Duplicate actual reward callbacks remain visible and classify FAIL; no currency is granted.

## Failure and limits

Repeated claims/pops/publisher loads/rewards, failed owned cache cleanup or an explicit
operator rejection of the expected creative classify FAIL. Binary incompatibility or
multiple adapter definitions fail preflight/build and must be recorded separately if
a report cannot be exported. No installation is authorized after a packaging failure.

Another network winning, unavailable TestPBS tier, PBS/init/config/network failures,
expired response, missing waterfall/reward/operator confirmation, a load timeout or
diagnostic failure classify INCONCLUSIVE. Do not change dashboard settings to force PASS.
An unresolved load requires process restart; no automatic second run.

Recommended separate operator review record:
- B4 source commit or reviewed working-tree identification.
- Build/JVM/analyze outcomes and debug/release packaging counts (only actually executed).
- Installed base/split uniqueness observation.
- Scenario/runId and original automatic classification.
- Verified preparation, claim, cache pop, selected adapter, loaded/displayed/reward/hidden.
- Explicit operator visible-creative finding, with no sensitive screenshot/export.
- Operator-reviewed PASS/FAIL/INCONCLUSIVE for the bounded objectives and exact limitations.

All completed B2/B3 original classifications and reviewed findings remain untouched.
