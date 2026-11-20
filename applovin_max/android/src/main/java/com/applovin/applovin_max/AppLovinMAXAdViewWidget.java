package com.applovin.applovin_max;

import android.content.Context;
import android.widget.FrameLayout;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxAdViewConfiguration;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class AppLovinMAXAdViewWidget
        extends FrameLayout
        implements MaxAdListener, MaxAdViewAdListener, MaxAdRevenueListener
{
    private final MaxAdView adView;
    private final AppLovinMAX owner;
    private final boolean coordinated;
    private boolean destroyed;
    private final boolean   shouldPreloadWidget;

    @Nullable
    private AppLovinMAXAdView containerView;

    public AppLovinMAXAdViewWidget(final String adUnitId, final MaxAdFormat adFormat, final boolean isAdaptive, final Context context)
    {
        this( adUnitId, adFormat, isAdaptive, false, context );
    }

    public AppLovinMAXAdViewWidget(final String adUnitId, final MaxAdFormat adFormat, final boolean isAdaptive, final boolean shouldPreloadWidget, final Context context)
    {
        this(adUnitId, adFormat, isAdaptive, shouldPreloadWidget, context, AppLovinMAX.getInstance());
    }

    AppLovinMAXAdViewWidget(final String adUnitId, final MaxAdFormat adFormat, final boolean isAdaptive,
                           final boolean shouldPreloadWidget, final Context context, final AppLovinMAX owner)
    {
        super( context );
        this.owner = owner;
        coordinated = owner.bannerOperations.managed(adUnitId);
        owner.bannerOperations.beforeCreate(adUnitId);

        this.shouldPreloadWidget = shouldPreloadWidget;

        MaxAdViewConfiguration.Builder builder = MaxAdViewConfiguration.builder();

        if ( adFormat.isBannerOrLeaderAd() )
        {
            if ( isAdaptive )
            {
                builder.setAdaptiveType( MaxAdViewConfiguration.AdaptiveType.ANCHORED );
            }
            else
            {
                builder.setAdaptiveType( MaxAdViewConfiguration.AdaptiveType.NONE );
            }
        }

        adView = new MaxAdView( adUnitId, adFormat, builder.build() );
        adView.setListener( this );
        adView.setRevenueListener( this );

        // Set this extra parameter to work around a SDK bug that ignores calls to stopAutoRefresh()
        adView.setExtraParameter( "allow_pause_auto_refresh_immediately", "true" );

        adView.stopAutoRefresh();

        addView( adView );

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );

        adView.setLayoutParams( params );
        owner.bannerOperations.bind(adUnitId, adView, adFormat, isAdaptive);
    }

    public MaxAdView getAdView()
    {
        return adView;
    }

    public String getAdUnitId()
    {
        return adView.getAdUnitId();
    }

    public void setPlacement(@Nullable final String value)
    {
        adView.setPlacement( value );
    }

    public void setCustomData(@Nullable final String value)
    {
        adView.setCustomData( value );
    }

    boolean isCoordinated() { return coordinated; }

    public void setAutoRefreshEnabled(final boolean enabled)
    {
        if ( enabled )
        {
            if (coordinated) { adView.stopAutoRefresh(); return; }
            adView.startAutoRefresh();
        }
        else
        {
            adView.stopAutoRefresh();
        }
    }

    public void setExtraParameters(@Nullable final Map<String, Object> extraParameters)
    {
        if ( extraParameters == null ) return;

        for ( Map.Entry<String, Object> entry : extraParameters.entrySet() )
        {
            adView.setExtraParameter( entry.getKey(), (String) entry.getValue() );
        }
    }

    public void setLocalExtraParameters(@Nullable final Map<String, Object> localExtraParameters)
    {
        if ( localExtraParameters == null ) return;

        for ( Map.Entry<String, Object> entry : localExtraParameters.entrySet() )
        {
            adView.setLocalExtraParameter( entry.getKey(), entry.getValue() );
        }
    }

    public boolean hasContainerView()
    {
        return containerView != null;
    }

    public void attachAdView(AppLovinMAXAdView view)
    {
        containerView = view;
    }

    void requireMountOwner(AppLovinMAX candidate) {
        if (coordinated && (owner != candidate || !owner.bannerOperations.callbackAllowed(adView, true)))
            throw new IllegalStateException("BANNER_STALE_VIEW");
    }

    public void detachAdView()
    {
        containerView = null;
        if (coordinated && !destroyed) owner.bannerOperations.invalidateView(adView);
    }

    public void initialLoad()
    {
        if (!owner.bannerOperations.deferInitialLoad(adView)) loadAd();
    }

    public void loadAd() { loadAd(null); }

    public void loadAd(String bannerAttemptId)
    {
        owner.bannerOperations.beforeLoad(adView, bannerAttemptId);
        adView.loadAd();
        owner.bannerOperations.afterLoad(adView);
        if (coordinated) adView.stopAutoRefresh();
    }

    public void destroy()
    {
        if (destroyed) return;
        destroyed = true;
        detachAdView();
        owner.bannerOperations.release(adView);

        adView.setListener( null );
        adView.setRevenueListener( null );
        adView.destroy();
    }

    @Override
    public void onAdLoaded(@NonNull final MaxAd ad)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        owner.bannerOperations.complete(adView, ad);
        Map<String, Object> params = owner.getAdInfo( ad );
        params.put( "adViewId", hashCode() );

        if ( shouldPreloadWidget )
        {
            // Copy the `params` for the next sending, as they are consumed (i.e., released) by
            // `MethodChannel.invokeMethod()` through `fireCallback()`.
            owner.fireCallback( "OnWidgetAdViewAdLoadedEvent", Map.copyOf( params ) );
        }

        if ( containerView != null )
        {
            containerView.sendEvent( "OnAdViewAdLoadedEvent", params );
        }
    }

    @Override
    public void onAdLoadFailed(@NonNull final String adUnitId, @NonNull final MaxError error)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        owner.bannerOperations.complete(adView, null);
        Map<String, Object> params = owner.getAdLoadFailedInfo( adUnitId, error );
        params.put( "adViewId", hashCode() );

        if ( shouldPreloadWidget )
        {
            // Copy the `params` for the next sending, as they are consumed (i.e., released) by
            // `MethodChannel.invokeMethod()` through `fireCallback()`.
            owner.fireCallback( "OnWidgetAdViewAdLoadFailedEvent", Map.copyOf( params ) );
        }

        if ( containerView != null )
        {
            containerView.sendEvent( "OnAdViewAdLoadFailedEvent", params );
        }
    }

    @Override
    public void onAdClicked(@NonNull final MaxAd ad)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        if ( containerView != null )
        {
            Map<String, Object> params = owner.getAdInfo( ad );
            params.put( "adViewId", hashCode() );

            containerView.sendEvent( "OnAdViewAdClickedEvent", params );
        }
    }

    @Override
    public void onAdExpanded(@NonNull final MaxAd ad)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        if ( containerView != null )
        {
            Map<String, Object> params = owner.getAdInfo( ad );
            params.put( "adViewId", hashCode() );

            containerView.sendEvent( "OnAdViewAdExpandedEvent", params );
        }
    }

    @Override
    public void onAdCollapsed(@NonNull final MaxAd ad)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        if ( containerView != null )
        {
            Map<String, Object> params = owner.getAdInfo( ad );
            params.put( "adViewId", hashCode() );

            containerView.sendEvent( "OnAdViewAdCollapsedEvent", params );
        }
    }

    @Override
    public void onAdRevenuePaid(@NonNull final MaxAd ad)
    {
        if (!owner.bannerOperations.callbackAllowed(adView, coordinated)) return;
        if ( containerView != null )
        {
            Map<String, Object> params = owner.getAdInfo( ad );
            params.put( "adViewId", hashCode() );

            containerView.sendEvent( "OnAdViewAdRevenuePaidEvent", params );
        }
    }

    /// Deprecated Callbacks

    @Override
    public void onAdDisplayed(@NonNull final MaxAd ad) { }

    @Override
    public void onAdDisplayFailed(@NonNull final MaxAd ad, @NonNull final MaxError error) { }

    @Override
    public void onAdHidden(@NonNull final MaxAd ad) { }
}
