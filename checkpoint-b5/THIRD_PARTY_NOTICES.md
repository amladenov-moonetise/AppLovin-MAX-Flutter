# Prebid source attribution

Prebid Mobile Android SDK MAX adapter 3.4.1
https://github.com/prebid/prebid-mobile-android
Copyright 2018–2021 Prebid.org, Inc.
Licensed under the Apache License, Version 2.0; see LICENSE-Prebid-Apache-2.0.txt.

The published org.prebid:prebid-mobile-sdk-max-adapters:3.4.1 POM declares Apache-2.0.
ParametersChecker.java and ListenersCreator.java are copied unchanged from its source JAR.
MaxInterstitialManager.java is copied with one Moonetise research cache-pop diagnostic marker.
MaxRewardedManager.java is copied from the same 3.4.1 source JAR with only the equivalent
successful cache-pop marker and sanitized load-failure text (codes unchanged) to avoid
passing cache IDs/raw errors into SDK diagnostics. Reward configuration, renderer selection,
and listener event forwarding remain unchanged.
MaxBannerManager.java is copied from the same pinned source JAR with successful cache-pop
and destroy diagnostics, removal of placement logging, and sanitized load-failure text.
Its MediationBannerView renderer creation and original ListenersCreator view/event forwarding
remain unchanged; no renderer or creative markup is copied into Moonetise UI.
PrebidMaxMediationAdapter.java is adapted for the research provider and bounded initialization;
unsupported formats return no-fill. All other B5 coordination/UI code is Moonetise research code.
Existing copyright/license notices are retained. No Prebid Core renderer code is copied or modified.
This fixture is not an upstream Prebid release or a publishable production adapter.

The unchanged official applovin_max package and native SDK dependencies retain their respective
licenses and distribution terms. No MAX SDK binaries are redistributed in this source fixture.
