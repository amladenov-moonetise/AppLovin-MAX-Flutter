# B5 banner evidence and classification

Status: B5 CLOSED as an operator-reviewed synthetic banner E2E PASS; see README.md for
exact operator-supplied run IDs and observations. All original automatic classifications
remain INCONCLUSIVE. SDK/callback-level AppLovin fallback and cancellation/cache cleanup
have separate reviewed PASS findings; no missing visual or callback evidence is invented.
The schema and automatic acceptance semantics below are unchanged.

Each COPY REDACTED REPORT produces a header plus bounded JSONL events. Keep each
export as originally emitted. A later operator-reviewed assessment is a separate
record and must not rewrite automatic classifications or original evidence.

## Fields

Header: runId, cycle, scenario, format=BANNER, unitSuffix=a5bb, size=320x50,
classification, missingEvidence, revenueCallback (OBSERVED/NOT_OBSERVED),
cancellationObserved, hostDiagnosticsHealthy and publisherRevenueCallbacks.
nativeClassification is retained when host diagnostics prevent accepting a native
result. An existing native FAIL is never downgraded away.

Events: timestamp, monotonic elapsedMs, build, expected banner format/size, adapter
class, MAX version, enumerated result/category/network/loadState.
ADAPTER_FORMAT records BANNER / LEADER / MREC / OTHER_FORMAT from the actual MAX
callback. FORMAT_SIZE_CHECK=MATCH means exact unit+BANNER+320x50.
CAPTURED=FRESH means the actual cached bid passed banner type/320x50/price/hb_pb/TTL checks.
SLOT_CLAIM=MATCH means original targeting comparison and atomic single-use claim.
CACHE_POP=CONSUMED is emitted only after original successful ParametersChecker cache pop.
ADAPTER_DELEGATED=SUCCESS means manager.loadAd returned, not that rendering succeeded.

No keys, raw response IDs, consent strings, device/advertising IDs, raw request/bid JSON,
arbitrary targeting, raw SDK errors or revenue amounts/labels are exported.

## PASS A — NO_DEMAND

Require installed APK uniqueness and official MAX initialization; one explicit
publisher mount/load request; genuine publisher MAX loaded/failed; actual BANNER/320x50
adapter invocation and prompt ADAPTER_NO_FILL with no prepared claim; ordinary other-network
load or waterfall failure evidence; publisher widget removal and positive operator
disposal review. Fill is not guaranteed.

The automated classifier conservatively requires TestPBS invocation evidence.
If MAX never reaches TestPBS, record INCONCLUSIVE for that assertion even if an
ordinary banner filled. Do not infer an adapter no-fill from absence of Prebid demand.

## PASS B — SYNTHETIC

Require:
- Actual approved PREPARATION_RESULT=BID and one CAPTURED=FRESH.
- Actual BANNER/320x50/unit match and one original-targeting SLOT_CLAIM=MATCH.
- Exactly one actual CACHE_POP=CONSUMED; original manager delegation.
- Genuine publisher LOADED, selected TestPBS and CLASS_MATCH / AD_LOADED waterfall.
- Operator confirms expected synthetic 320x50 creative in the official MAX view.
- Publisher removes the actual widget, original manager destroy is observed, and
  operator confirms no observed disposal corruption.

HOST_VIEW_MOUNT_REQUESTED is a publisher request, not a native creation acknowledgement.
HOST_VIEW_REMOVED means the Flutter subtree was removed at a frame boundary.
BANNER_MANAGER_DESTROYED is the actual original manager destroy invocation.
Neither alone proves absence of memory leaks; acceptance is bounded visual/lifecycle evidence.
No public plugin native-disposal acknowledgement is invented.

Revenue is optional evidence from the actual official publisher callback. Missing
revenue does not fail the test. Flutter's supported banner listener has no fullscreen
displayed/hidden contract; do not manufacture those events.

## FAIL / INCONCLUSIVE

FAIL: binary incompatibility or multiple adapter definitions (preflight/build);
repeated claim/pop; fake callbacks; explicit operator rejection of visibility/size;
observed disposal corruption or failed owned-cache cleanup.
A crash may require a separate operator FAIL finding when no export is possible.

INCONCLUSIVE: missing required evidence, incompatible/unknown SDK format, no TestPBS
tier, another winner, no eligible demand, unavailable/incompatible remote fixture,
unresolved load, unavailable waterfall, unexpected extra publisher load callbacks,
missing visibility/disposal review or host diagnostic failure.
Repeated tier adapter evaluations are not repeated publisher loads and may mismatch
without consuming demand.

## Cancellation supplement

Use a separate fresh-process synthetic run with Pause after preparation enabled.
Cancel before mounting. Capture CONTRIBUTION_CLEARED=CANCELLED and, if a bid was
already captured, OWNED_CACHE_CLEARED=SUCCESS. A subsequent explicit MAX widget mount
must not claim the cancelled response. This is cancellation evidence, not PASS B;
the original SYNTHETIC report will normally remain INCONCLUSIVE.
Expiry/cancellation/size isolation also have SDK-free JVM checks, never described
as a live MAX waterfall test.

## Separate operator finding

Record source revision/working-tree identity, original automatic classification,
actual executed build/JVM/APK results, installed uniqueness, each observed preparation/
claim/pop/selection/callback, optional revenue receipt, visible size/creative and disposal.
Do not assert release banner rendering, refresh reliability, in-process sequential
loads, real CMP synchronization or other formats based on this checkpoint.
Completed B3/B4 reports and their reviewed findings remain unchanged.
