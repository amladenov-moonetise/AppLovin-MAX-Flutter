package com.applovin.applovin_max;

import androidx.annotation.Nullable;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.ads.MaxRewardedAd;

/** Native-only demand boundary. The publisher owns MAX and actual reward callbacks. */
public interface RewardedPreparation {
    /** Independent publisher MAX request/display permission, not merely Prebid consent. */
    boolean isAuthorized();
    /** Ready for explicit MAX dispatch, with either a current bid or a clean MAX-only route. */
    boolean isReady();
    void clear(MaxRewardedAd exactObject);
    default void onEvent(String event, @Nullable MaxAd ad) {}
}
