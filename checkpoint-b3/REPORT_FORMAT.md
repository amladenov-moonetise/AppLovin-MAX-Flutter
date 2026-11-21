# B3 acceptance report format — not a captured run

Preserve one exported report per runId/cycle. The header is computed evidence, not operator
approval. Never change the original classification or JSONL. Add operator review separately.

Header fields:
- runId: ephemeral research-session UUID; cycle:1..3.
- scenario: SYNTHETIC / NO_DEMAND / PREP_FAILURE / PREP_TIMEOUT / CANCEL / EXPIRE / WITHDRAW / TIER_CHECK.
- scope: MAX_RUNTIME or SDK_LEVEL_ONLY.
- classification: PASS / FAIL / INCONCLUSIVE.
- missingEvidence: fixed predicate names; empty only when all automatic checks passed.
- liveMultiTier: separate PASS/INCONCLUSIVE; SDK probe cannot satisfy live multi-tier.
- requestedInitializationOrder: publisher fixture choice.
- hostDiagnosticsHealthy: false invalidates acceptance; nativeClassification preserved when overridden.
- jsonl is emitted as subsequent lines rather than repeated inside the header.

Each JSONL line contains runId, cycle(0=process initialization), timestamp/elapsedMs, scenario,
DEBUG/RELEASE build, fixed adapter class, MAX version, enum event/result/category/network/state.
No raw keys, response IDs, request data, device/advertising IDs, consent or revenue amounts.

Operator review (append separately, never rewrite export):
- Device Android version (no private device identifiers):
- Variant and APK uniqueness verification:
- Official plugin / MAX / Prebid pins verified:
- Existing TestPBS account/network association corroborated:
- Cycles1..3 came from the SAME process/session:
- Initialization order actually observed (include actual INIT_STATUS):
- Preparation and fresh capture evidence:
- Matching claim/cache pop counts:
- Ordinary MAX fallback evidence:
- Genuine loaded/failed/displayed/hidden callbacks:
- Expected synthetic video visibly confirmed, or not applicable:
- Cleanup/expiry/withdrawal result:
- Live multi-tier result vs SDK-only probe:
- Automated classification (verbatim):
- Separate operator-reviewed decision and bounded rationale:

Do not mark TestPBS rendering from a bid alone or another selected network.
A source/template/example is not captured evidence. This file remains a template.

B3 is closed as a separate operator-reviewed V1 reliability milestone; see README.md for
the completed findings and explicitly INCONCLUSIVE sub-objectives. Preserve every original
export's classification, missingEvidence and JSONL unchanged. The README acceptance record
does not normalize automatic outputs or claim full positive contribution on every cycle.
No build/device test was rerun and no implementation/classifier logic was edited for closure.
