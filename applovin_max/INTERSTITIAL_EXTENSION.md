# Android interstitial extension — EXT-A2

This maintained 4.5.1 fork replaces the original Flutter MAX plugin. Native MAX
remains 13.3.1. The plugin owns all native ad objects and forwards accepted events
to the publisher's existing Dart listeners, including revenue attribution.
EXT-A2 adds a trusted native preparation boundary; the fork still has no Prebid/Core
dependency or auction implementation. Attempt identity and SDK initialization are
not consent or legal advertising authorization.

## Explicit operations

- reserveInterstitial(id) returns an opaque attempt ID for a demonstrably idle
  exact plugin-owned generation.
- loadInterstitial(id, attemptId: attempt) explicitly dispatches at most once.
- showInterstitial(id, attemptId: attempt) explicitly dispatches at most once.
- retireInterstitial(id, attemptId: attempt) invalidates before SDK cleanup.
  True means the plugin detached its listeners and destroy() returned successfully.
  False means retirement is deferred, not cancelled or completed. Cleanup failure
  throws INTERSTITIAL_CLEANUP_FAILED and quarantines the ID until process restart.
  Stale attempt/generation/engine requests cannot destroy a replacement.

Load/show futures acknowledge dispatch, not load success, presentation or an
impression. Keep publisher consent/eligibility checks and normal MAX callbacks.

## Deadlines and current validity

Five seconds is only a pre-load reservation deadline. At the exact deadline,
beforeLoad fails closed. Successful dispatch consumes this deadline; slow MAX
loading and a delayed explicit Show do not expire merely because five seconds
elapsed. There is no new automatic load/show timer or retry. The publisher remains
responsible for bounded MAX load observation and explicitly retiring obsolete work.

A loaded coordinated ad still requires the same current attempt/generation/engine,
no invalidation and native MAX readiness before Show. Host detach, explicit
retirement, uncertain SDK state or MAX expired-ad replacement invalidates the
attempt. An invalidated ad never becomes ordinary MAX-only on that retained
object. Ordinary ID-only calls retain their upstream path on uncoordinated units.

The fork itself stores no cached bids. Removing the preparation deadline
from loaded-ad validity does NOT authorize unbounded bid retention.
The EXT-A2 native provider must supply current authorization,
configuration revisions and its own bid freshness/expiry policy at the native
load/show boundary. Invalidating a retained bid requires retirement, not reuse as
ordinary MAX demand. This production authorization is not implemented here.

## Retirement and safe reuse

| Object state | Retirement behavior |
| --- | --- |
| Reserved / expired before load | Invalidate; detach listeners; destroy exact object. |
| Loading | Invalidate first; destroy exact object; late load callbacks are rejected. |
| Loaded | Invalidate; suppress readiness/Show; destroy exact object. |
| Show dispatched / showing | Block further operations; preserve genuine callbacks; defer cleanup. |
| Hidden before revenue | Remain deferred until the legitimate revenue callback arrives. |
| Revenue before hidden | Remain deferred until HIDDEN arrives. |
| Display failure before display | Accepted terminal failure permits cleanup. |
| Cleanup failure | Retain guard/object reference; do not retry or permit replacement. |
| Cleanup completed | Release only the matching generation's guard; retain a scalar retirement receipt. |

Cleanup runs on the Android platform thread, outside registry locks. Terminal
callbacks are forwarded first. Callback-originated cleanup is queued beyond that
callback; an explicit native operation may also flush cleanup after its SDK call
returns. No fixed delay is used as proof of callback completion. Normal
completion/failure also requests retirement automatically. Explicit retirement
can be repeated: completed cleanup returns true without another destroy, until a
new object generation or engine makes that old handle stale.

After successful cleanup, the SDK object is removed only if it is still the
plugin's exact map value. Registry identity metadata permits a fresh generation
on the next explicit publisher operation. The plugin never automatically creates,
loads or shows a replacement. Old callbacks cannot restore new readiness.
Publisher per-object extra parameters are not cloned to replacement objects:
reapply required settings through the existing setter before the next reservation
or load. Local response extras must never be copied into a fresh generation.
Global MAX initialization/settings and publisher Dart listeners remain unchanged.

destroy() is a void public SDK call. Returning normally establishes local logical
retirement, not proof that remote/network work was cancelled. Queued obsolete
callbacks remain generation-guarded. No cleanup is forced on a showing ad.
If HIDDEN/revenue or an appropriate terminal callback never arrives, the
coordinated ID can remain blocked until process restart; a timer cannot fabricate
completion. Already queued legitimate presentation receipts cannot update a
replacement. Detached-engine callbacks are never replayed to a new Dart engine.

Engine/Activity detach invalidates coordinated work and cleans non-showing
objects. Showing objects keep their native callback sink until terminal handling.
Uncoordinated objects are not destroyed by this extension.

## EXT-A2 native preparation boundary — implemented, operator validation pending

The host obtains this engine's AppLovinMAX plugin instance and calls
attachInterstitialPreparation(id, attempt, preparation). It attaches at most one
InterstitialPreparation to the current exact idle binding. PreparationAccess
permits guarded, platform-thread writes to that reserved object via withCurrentObject.
All asynchronous demand delegate writes MUST use that access; retaining the object
and later writing directly violates this trusted-native contract.

isAuthorized and isReady are checked immediately before coordinated native Load/Show.
Exceptions fail closed. Exact binding/attempt/generation/epoch checks remain in the
existing registry. A preparation cannot initialize MAX, load/show, construct a
replacement or take publisher listeners. No provider registry or Dart object/cache
API is added.

The native preparation owns its response association/freshness and implements clear.
Existing retirement invokes clear(exactObject) before detach/destroy/releasing the
guard; cleanup failure uses the existing quarantine. Showing cleanup remains deferred.
onEvent provides native observation while accepted publisher callbacks still forward
normally. Provider event failures invalidate the attempt without suppressing legitimate
publisher presentation receipts. Ordinary uncoordinated ID-only behavior is unchanged.

onCallbackObserved is a diagnostic-only tap after the existing identity/format check
and callback acceptance decision. It reports native receipt, acceptance and engine
forwarding eligibility independently. Exceptions are ignored; diagnostics cannot
change acceptance, publisher forwarding or retirement. A renderer's internal log
does not synthesize a MAX callback.

The Asterise debug acceptance fixture implements the Prebid-specific provider with
3.4.1 and the approved test configuration. No new build, test or live rendering
result is claimed for EXT-A2.

## Earlier conceptual contract (superseded by EXT-A2 above)

A minimal future provider needs the following conceptual interface, not a new
owner and not a second Flutter MAX plugin:

1. prepare(AttemptContext, Completion) returns a cancellable cleanup handle.
   Context contains exact MAX ID, opaque attempt, immutable generation/epoch and
   a monotonic preparation deadline. Completion returns an opaque prepared-state
   handle or a sanitized bypass/error; it never loads/shows.
2. isCurrent(AttemptContext, preparedState) is cheap, synchronous and fail-closed.
   It checks current publisher authorization, privacy/config revisions and demand
   freshness before native Load and again immediately before native Show.
3. clear(AttemptContext, preparedState) seals late writes and removes only that
   attempt's response association/cache state. Cleanup must finish before the
   coordinator releases its process lease or this fork releases the object guard.

Exact-object association would execute through a guarded native plugin boundary;
no native object, cache ID or raw bid payload crosses Dart. A provider cannot
initialize MAX, acquire another object/singleton, replace publisher listeners,
grant consent or trigger loads/shows. A late completion cannot revive a retired
generation. Uncertain provider cleanup must quarantine rather than release.
The project owner explicitly approved skipping standalone fake preparation for EXT-A2.

## Operator validation

No new checks were executed by Codex. Retain the registry runner and run both Java
mains, now compiling InterstitialRetirement.java as well. Run the focused Dart
channel file. Then compile in an acceptance host consuming this fork as its sole
MAX plugin and validate API 24/25 and Android 10 behavior, including load
invalidation, delayed Show, both HIDDEN/revenue orders, recreation/detach and safe
same-ID reuse. Unit tests use fake cleanup boundaries, not real SDK destruction.
No live demand/rendering or publisher Flutter integration is established here.
