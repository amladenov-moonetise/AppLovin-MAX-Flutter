package com.applovin.applovin_max;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.ads.MaxAdView;
import java.util.function.Consumer;
import androidx.annotation.Nullable;

/** Trusted native extension only. No SDK initialization, creation, load or refresh ownership transfer. */
public interface BannerPreparation {
    boolean isAuthorized();
    boolean isReady();
    default boolean isDisplayAuthorized() { return isAuthorized(); }
    void onViewCreated(Access access);
    void clear(MaxAdView exactView);
    void onEvent(String event, @Nullable MaxAd ad);
    interface Access {
        int getWidthDp();
        int getHeightDp();
        /** Native attachment/visibility/clip corroboration; publisher viewport intent is separate. */
        boolean isRefreshEligible();
        /** Opaque, one-use load handle. Never a Prebid response-cache identifier. */
        @Nullable String getLoadHandle();
        long begin();
        boolean isCurrent(long cycle);
        boolean withCurrentObject(long cycle, Consumer<MaxAdView> write);
        void invalidate();
    }
}
