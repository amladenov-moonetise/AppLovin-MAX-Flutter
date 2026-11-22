# B5 source provenance and compatibility boundary

Base: B4 944dbe3d5fb9b26d219e4f4aab8b718f5ed89e74, repository
amladenov-moonetise/AppLovin-MAX-Flutter. Protected Android reference inspected at
release/android-ads 311cde2b88a622fef0f5d35cb9fd4b90776de570.
The MAX fork reference release/android-max 6d31ab6 is not used as a dependency.

## Exact pinned Prebid sources

org.prebid:prebid-mobile-sdk-max-adapters:3.4.1 sources JAR
(SHA-1 cache directory 86b7c411826c6fb472da4f5dd0871a12aa439391):
- com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java:
  loadAdViewAd(parameters, MaxAdFormat, Activity, MaxAdViewAdapterListener) creates
  MaxBannerManager and delegates loadAd. onDestroy destroys the manager.
- com/applovin/mediation/adapters/prebid/managers/MaxBannerManager.java:
  loadAd checks original keywords and destructively gets the cached response through
  ParametersChecker. Original supported labels are BANNER and MREC, not LEADER.
  showBanner creates AdUnitConfiguration(BANNER) and MediationBannerView, delivering
  that actual View through maxListener.onAdViewAdLoaded(adView).
  destroy calls PrebidDestroyable.destroy on its view.
- prebid/ListenersCreator.java: createBannerListener forwards original
  loaded/displayed/display-failed/clicked/expanded/collapsed/hidden contracts.
  These helpers and the original ParametersChecker remain unchanged from B4.

org.prebid:prebid-mobile-sdk-core:3.4.1 sources JAR
(SHA-1 cache directory 9d3f9d676d25b5c80ec09bb38ce5d2c05eeea010):
- org/prebid/mobile/api/mediation/MediationBannerAdUnit.java:
  constructor(Context, String configId, AdSize, PrebidMediationDelegate);
  BANNER defaults; setRefreshInterval(0), stopRefresh and destroy are public.
- org/prebid/mobile/api/mediation/MediationBaseAdUnit.java, onResponseReceived:
  cache.putBidResponse(response), then delegate keyword/response updates and fetch completion.
- org/prebid/mobile/rendering/bidding/display/BidResponseCache.java:
  getKeywords is non-destructive; popBidResponse removes the response. Expiry is
  not enforced at every cache lookup, so the pending slot has a monotonic deadline.
- org/prebid/mobile/rendering/bidding/data/bid/Bid.java:
  getWidth/getHeight are creative DIPs, getType is ext.prebid.type.
- org/prebid/mobile/rendering/bidding/display/MediationBannerView.java:
  existing container delegates rendering to PluginRendererFactory.createBannerAdView
  and retains the SDK's win-notice/destroy behavior. It remains supplied unchanged
  by Prebid Core, along with its renderer/OMID dependencies.

MAX 13.3.1 public binary interface inspected from cached classes.jar:
com/applovin/mediation/MaxAdFormat and
com/applovin/mediation/adapter/listeners/MaxAdViewAdapterListener.
The latter exposes onAdViewAdLoaded(android.view.View), load-failed, displayed,
display-failed, clicked, hidden, expanded and collapsed. It has no adapter revenue
callback to invent; publisher revenue comes from MAX's genuine revenue listener.
MaxAdFormat supplies getLabel and getSize; B5 admits only actual BANNER / 320x50.

## Official Flutter MAX 4.5.1, unchanged

Hosted pub.dev package:
- lib/src/max_ad_view.dart: MaxAdView with adFormat, width/height, extraParameters,
  isAdaptiveBannerEnabled and isAutoRefreshEnabled. Mounting AndroidView causes
  native creation. The widget has no explicit manual first-load method.
- android/.../AppLovinMAXAdViewFactory.java: banner maps to device-specific
  BANNER or LEADER; width/height in Dart do not select the native format.
- AppLovinMAXAdView.java: constructor applies extras and refresh flag, attaches the
  widget, then calls widget.loadAd. Ordinary platform-view dispose removes the
  instance and calls widget.destroy. B5 uses neither preload nor private registry access.
- AppLovinMAXAdViewWidget.java: owns the native MAX AdView/listeners, sets
  allow_pause_auto_refresh_immediately=true, stops refresh, and exposes genuine
  loaded/failed/clicked/expanded/collapsed/revenue events to Flutter.
  Its deprecated displayed/hidden callbacks are empty; Flutter's banner listener
  does not expose fullscreen-style DISPLAYED/HIDDEN acceptance events.

B5 prepares/arms before mounting one official widget, with adaptive and refresh OFF,
explicit width=320/height=50 and extra force_banner=true. The widget instance is kept
stable across parent report/UI rebuilds. No substitute MAX object or creative view
is created by Asterise. The only new rendered View is the original Prebid manager's
SDK MediationBannerView, supplied to MAX through the normal adapter contract.

## Protected Android banner evidence

Read-only files:
- asterise-max/src/main/java/com/moonetise/asterise/max/AsteriseMaxBanner.kt:
  managed native view uses explicit MaxAdFormat.BANNER and force_banner=true before load.
- MediatedBannerSize.kt and BannerDemand.kt: fixed 320x50 DIP size, fit validation,
  MediationBannerAdUnit(AdSize(320,50)), refresh interval zero and stop/destroy.
- sample-app/.../SyntheticVideoTestMode.kt: allowlisted banner endpoint/account,
  config/unit and fixed response below.
- sample-app/src/debug/.../BannerWireDiagnostics.kt: request size and response
  price=3, hb_pb=3.00, hb_size=320x50, type=banner diagnostic predicates.

The protected native reference uses MAX 13.6.3 and owns its native banner directly.
B5 keeps MAX 13.3.1 and publisher ownership through the official Flutter plugin.
The native reference's object ownership/refresh coordinator is not copied.

## Approved fixture and unresolved live prerequisites

- Application: com.moonetise.asterise.sample
- MAX banner: cecda62026b8a5bb
- PBS: https://pbs-test.moonetise.com/openrtb2/auction
- Account: moonetise-test
- Stored impression/config: moonetise-mobile-banner-test
- Stored auction response: moonetise-test-display-banner-3usd
- Source/reference contract: banner, 320x50, price 3, hb_pb=3.00.

These identifiers are verified from existing approved source, not fabricated.
No remote request/dashboard read or write was performed. Current remote fixture
availability, actual bid w/h/type and live TestPBS tier configuration remain runtime
prerequisites. B5 checks actual SDK response type=banner, w=320, h=50, price=3 and
hb_pb=3.00 before capture; incomplete/incompatible responses are removed from its
owned cache path and MAX remains independently eligible. It does not infer missing
dimensions from hb_size or generate markup.

The official tablet factory starts with LEADER. Passing force_banner=true before load
is the existing public mechanism, but source alone does not establish the resulting
MAX 13.3.1 adapter format on the target tablet. ADAPTER_FORMAT and FORMAT_SIZE_CHECK
record the actual invocation. Unsupported format/size never consumes the slot.
If LEADER persists, preserve INCONCLUSIVE and report the public-view boundary; do
not coerce format, access private objects, patch the plugin or alter the dashboard.

## Closure evidence boundary

B5 is now operator-reviewed synthetic banner E2E PASS. See README.md for exact run IDs.
The tablet synthetic run supplied actual BID, matching 320x50 BANNER invocation, one
claim/pop, original manager delegation, class-matched loaded waterfall, genuine
publisher load/revenue, operator visibility and manager/view disposal observations.
This resolves the initial tablet-format/fixture question for that bounded run; it
does not establish behavior for every device, a future remote configuration, release
runtime rendering or controlled refresh.

Cancellation/cache cleanup and phone AppLovin fallback have separate reviewed PASS
findings. Phone fallback is SDK/callback evidence without explicit visual confirmation.
No external dashboard verification or missing callback is inferred. Every automatic
report remains INCONCLUSIVE; original exports are not recreated in this source tree.
No implementation/dependency/renderer changes or new tests were performed for closure.

## Modified surface

- Same-name adapter adds only banner provider/manager load and manager destruction.
  Interstitial/rewarded load/show/provider/helper bodies are retained after package/trace renaming.
- MaxBannerManager is copied from exact 3.4.1 with cache-pop/destroy markers,
  sanitized load-error text (codes unchanged) and placement logging removed.
  Original renderer creation, view delivery, original listener and destroy logic remain.
- BannerDemandBridge/BannerScope add a format/size-scoped B3/B4 pending slot.
  Original matching and manager cache pop are reused; the provider does not render.
- MainActivity, ResearchPreparation, trace and Flutter UI are research scaffolding.
  No public production Asterise APIs, live consent bridge or entitlement logic are added.

See THIRD_PARTY_NOTICES.md and LICENSE-Prebid-Apache-2.0.txt.
This is modified research code, not an upstream adapter release.
