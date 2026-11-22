# B4 source provenance and modification boundary

Base: committed B3 70540f41dd5295fde37b766b4420ec1079a22f13 in
amladenov-moonetise/AppLovin-MAX-Flutter. B4 is isolated; completed fixtures are read-only.

## Pinned source inspected

- org.prebid:prebid-mobile-sdk-max-adapters:3.4.1 sources JAR,
  SHA-1 path 86b7c411826c6fb472da4f5dd0871a12aa439391:
  - com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java:
    loadRewardedAd creates MaxRewardedManager; showRewardedAd calls its showAd
    on the Activity UI thread; onDestroy destroys that manager.
  - com/applovin/mediation/adapters/prebid/managers/MaxRewardedManager.java:
    loadAd checks original keywords, pops response, sets rewarded configuration,
    installs RewardManager listener, uses PluginRendererFactory and loads controller.
    createRewardedListener forwards ready/clicked/displayed/hidden/failed and
    onUserEarnedReward to MAX onUserRewarded, with original reward amount/label.
    Reward forwarding is in this manager, not ListenersCreator.
  - prebid/ParametersChecker.java and prebid/ListenersCreator.java are retained
    byte-for-byte from B3. Original targeting comparison is unchanged.
- org.prebid:prebid-mobile-sdk-core:3.4.1 sources JAR,
  SHA-1 path 9d3f9d676d25b5c80ec09bb38ce5d2c05eeea010:
  - org/prebid/mobile/api/mediation/MediationRewardedVideoAdUnit.java:
    VAST-only rewarded configuration; onResponseReceived puts response.getId()
    in BidResponseCache, calls mediation delegate, then fetch completion.
  - org/prebid/mobile/rendering/bidding/display/InterstitialController.java:
    original Prebid controller; remains supplied by the unchanged SDK.
    PluginRendererFactory, RewardManager, creative views and OMID dependencies
    remain supplied by pinned Prebid. No second renderer is implemented.
- Official hosted applovin_max 4.5.1:
  lib/applovin_max.dart rewarded section (600–659): setRewardedAdListener,
  loadRewardedAd, isRewardedAdReady, showRewardedAd.
  lib/src/ad_listeners.dart: RewardedAdListener.onAdReceivedRewardCallback.
  Native plugin implementation remains unmodified and owns MAX rewarded objects.
- Protected Asterise reference, inspected read-only:
  sample-app/SYNTHETIC_VIDEO_TESTING.md (rewarded fixture/approved account+endpoint);
  asterise-max/REWARDED.md; AndroidRewardedClient.kt and RewardedCoordinator.kt.
  The reference uses MAX 13.6.3 and native exclusive ownership. B4 uses MAX 13.3.1,
  does not copy that ownership model, does not acquire a MaxRewardedAd and does not
  add a native rewarded listener to a publisher object.

## Verified existing approved fixture

- Endpoint: https://pbs-test.moonetise.com/openrtb2/auction
- Account: moonetise-test
- Stored impression/config: moonetise-mobile-rewarded-test
- Fixed stored auction response: moonetise-test-video-rewarded-3usd
- MAX rewarded: 691ff46cc460ce8e
- Existing reference evidence: video, numeric price 3, hb_pb=3.00, rewarded request.
  This is reference evidence, NOT a completed B4 request or reward test.

Only debug ResearchPreparation activates that fixed override, after operator
authorization. Prebid's override is process-global; stop/detach clears it but cannot
retract in-flight work. One format/scenario per fresh process is mandatory.

## B4 changes

- Original-name adapter adds rewarded provider lookup and original rewarded manager
  load/show/destroy. Existing B3 interstitial load/show/provider remain source-identical
  after research package/trace renaming.
- MaxRewardedManager is copied from the exact adapter source, with one successful
  cache-pop marker and sanitized load-failure messages only (codes unchanged).
  This avoids forwarding response IDs/raw exception messages into MAX diagnostics.
  Reward configuration and callbacks are unchanged. No manual reward is issued.
- RewardedDemandBridge is the bounded B3 slot mechanism scoped to the rewarded ID.
  Rewarded load entry point selects it; interstitial entry point selects DemandBridge.
  MAX response parameters have no independent format or unique publisher-load token.
  Format comes from the invoked MAX adapter method, not an invented parameter.
- ResponseParameters preserves all public MAX mediation fields except adding the
  prepared response ID to a copied local-extras map.
- Native activity supplies preparation and diagnostics only. Official Flutter MAX
  owns initialize/load/show and the publisher callback.
- A publisher reward is recorded only from official onAdReceivedRewardCallback.
  Pinned renderer reward policy is unchanged; this is callback-forwarding evidence,
  not proof of economic entitlement, completion verification or fraud prevention.

Apache-2.0 license and notices are retained. No MAX binaries or keys are embedded.
