package com.moonetise.research.checkpointb2;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.moonetise.research.b2.*;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.TargetingParams;
import org.prebid.mobile.api.data.AdUnitFormat;
import org.prebid.mobile.api.data.InitializationStatus;
import org.prebid.mobile.api.mediation.MediationInterstitialAdUnit;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.display.PrebidMediationDelegate;
import java.util.EnumSet;

/** Prebid preparation/diagnostics only. Official Flutter MAX owns init/load/show/listeners. */
public final class MainActivity extends FlutterActivity {
    private static final String URL = "https://pbs-test.moonetise.com/openrtb2/auction";
    private static final String ACCOUNT = "moonetise-test";
    private static final String CONFIG = "moonetise-mobile-interstitial-test";
    private static final String RESPONSE = "moonetise-test-video-interstitial-3usd";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private MethodChannel channel;
    private boolean starting, destroyed, active, synthetic, initializing, fetching, prepared;
    private MediationInterstitialAdUnit unit;
    private MethodChannel.Result preparationReply;
    private final Runnable runDeadline = () -> {
        active = false; DemandBridge.finish(); stopPreparation();
        PrebidMobile.setStoredAuctionResponse(null);
        B2Trace.close(B2Trace.Category.DEADLINE);
    };

    @Override public void configureFlutterEngine(@NonNull FlutterEngine engine) {
        super.configureFlutterEngine(engine);
        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(),
                "moonetise/adapter_b2_research");
        channel.setMethodCallHandler((call, reply) -> {
            try {
                switch (call.method) {
                    case "begin":
                        if (starting || destroyed || !"com.moonetise.asterise.sample".equals(getPackageName())
                                || (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0
                                || !Boolean.TRUE.equals(call.argument("authorized")))
                            throw new IllegalStateException("RUN_NOT_ALLOWED");
                        String mode = call.argument("mode");
                        if (!"SYNTHETIC".equals(mode) && !"NO_DEMAND".equals(mode))
                            throw new IllegalArgumentException("MODE");
                        Number eligibility = call.argument("eligibilityMs");
                        long milliseconds = eligibility == null ? 5000 : eligibility.longValue();
                        if (milliseconds < 1000 || milliseconds > 30000) throw new IllegalArgumentException("EXPIRY");
                        synthetic = "SYNTHETIC".equals(mode); starting = true;
                        scanner.execute(() -> {
                            int count;
                            try { count = InstalledApkCheck.count(getApplicationContext()); }
                            catch (Exception ignored) { count = -1; }
                            final int definitions = count;
                            main.post(() -> {
                                if (destroyed) return;
                                if (definitions != 1) {
                                    reply.error("APK_CLASS_CHECK_FAILED", "Exactly one adapter definition required.", null);
                                    return;
                                }
                                try {
                                    String run = B2Trace.begin();
                                    DemandBridge.begin(true, milliseconds); active = true;
                                    B2Trace.host("HOST_SCENARIO", synthetic ? "SYNTHETIC" : "NO_DEMAND",
                                            "NONE", "UNKNOWN", "UNKNOWN");
                                    main.postDelayed(runDeadline, B2Trace.WINDOW_MILLIS);
                                    reply.success(run);
                                } catch (RuntimeException ignored) {
                                    reply.error("FRESH_PROCESS_REQUIRED", "Force-stop before another run.", null);
                                }
                            });
                        });
                        break;
                    case "initializePrebid":
                        requireActive();
                        if (initializing) throw new IllegalStateException("ALREADY_STARTED");
                        initializing = true;
                        final boolean[] finished = { false };
                        Runnable initTimeout = () -> {
                            if (finished[0] || destroyed) return;
                            finished[0] = true;
                            B2Trace.record(B2Trace.Event.PREBID_INITIALIZED, B2Trace.Result.TIMEOUT, B2Trace.Category.DEADLINE);
                            reply.success(false);
                        };
                        main.postDelayed(initTimeout, 8000);
                        try {
                            PrebidMobile.setLogLevel(PrebidMobile.LogLevel.NONE);
                            PrebidMobile.setPbsDebug(false);
                            PrebidMobile.setShareGeoLocation(false);
                            TargetingParams.setSendSharedId(false);
                            PrebidMobile.setStoredAuctionResponse(null);
                            PrebidMobile.setPrebidServerAccountId(ACCOUNT);
                            PrebidMobile.setTimeoutMillis(2000);
                            PrebidMobile.initializeSdk(getApplicationContext(), URL, status -> main.post(() -> {
                                if (finished[0] || destroyed) return;
                                finished[0] = true; main.removeCallbacks(initTimeout);
                                boolean ready = active && (status == InitializationStatus.SUCCEEDED
                                        || status == InitializationStatus.SERVER_STATUS_WARNING)
                                        && PrebidMobile.isSdkInitialized();
                                B2Trace.record(B2Trace.Event.PREBID_INITIALIZED,
                                        ready ? B2Trace.Result.SUCCESS : B2Trace.Result.FAILED,
                                        ready ? B2Trace.Category.NONE : B2Trace.Category.PREPARATION);
                                reply.success(ready);
                            }));
                        } catch (RuntimeException ignored) { initTimeout.run(); }
                        break;
                    case "prepare":
                        requireActive();
                        if (prepared) throw new IllegalStateException("ALREADY_PREPARED");
                        prepared = true; preparationReply = reply;
                        if (!synthetic) {
                            finishPreparation("NO_BID"); break; // Deliberately zero PBS auctions.
                        }
                        if (!PrebidMobile.isSdkInitialized()) { finishPreparation("FAILED"); break; }
                        fetching = true;
                        main.postDelayed(fetchTimeout, 2500);
                        try {
                            // Fixed allowlisted process-global override; one run per process.
                            PrebidMobile.setStoredAuctionResponse(RESPONSE);
                            unit = new MediationInterstitialAdUnit(getApplicationContext(), CONFIG,
                                    EnumSet.of(AdUnitFormat.BANNER, AdUnitFormat.VIDEO), new PrebidMediationDelegate() {
                                @Override public void handleKeywordsUpdate(HashMap<String, String> keywords) {}
                                @Override public boolean canPerformRefresh() { return false; }
                                @Override public void setResponseToLocalExtras(BidResponse response) {
                                    DemandBridge.capture(response);
                                }
                            });
                            unit.fetchDemand(result -> main.post(() -> {
                                if (fetching && !destroyed) {
                                    String outcome = "NO_BIDS".equals(result.name()) ? "NO_BID"
                                            : "TIMEOUT".equals(result.name()) ? "TIMEOUT" : "FAILED";
                                    // A captured valid response overrides this fallback to BID.
                                    // SUCCESS with an incompatible/missing fixture remains FAILED.
                                    finishPreparation(outcome);
                                }
                            }));
                        } catch (RuntimeException ignored) { finishPreparation("FAILED"); }
                        break;
                    case "arm":
                        requireActive(); DemandBridge.arm(); reply.success(null); break;
                    case "terminal":
                        DemandBridge.finish(); reply.success(null); break;
                    case "invalidate":
                        active = false; DemandBridge.finish(); stopPreparation();
                        PrebidMobile.setStoredAuctionResponse(null); reply.success(null); break;
                    case "event":
                        B2Trace.host(call.argument("event"), call.argument("result"),
                                call.argument("category"), call.argument("network"), call.argument("state"));
                        reply.success(null); break;
                    case "export":
                        HashMap<String, String> report = new HashMap<>();
                        report.put("classification", B2Trace.classification());
                        report.put("jsonl", B2Trace.exportJsonLines());
                        reply.success(report); break;
                    case "close":
                        runDeadline.run(); reply.success(null); break;
                    default: reply.notImplemented();
                }
            } catch (RuntimeException ignored) {
                reply.error("RESEARCH_STATE_ERROR", "Use one authorized run per fresh debug process.", null);
            }
        });
    }
    private final Runnable fetchTimeout = () -> {
        if (fetching && !destroyed) finishPreparation("TIMEOUT");
    };
    private void requireActive() {
        if (!active || destroyed) throw new IllegalStateException("RUN_CLOSED");
    }
    private void finishPreparation(String fallback) {
        main.removeCallbacks(fetchTimeout);
        fetching = false;
        boolean bid = DemandBridge.preparationFinished();
        stopPreparation();
        PrebidMobile.setStoredAuctionResponse(null);
        String outcome = bid ? "BID" : fallback;
        B2Trace.record(B2Trace.Event.PREPARATION_RESULT, B2Trace.Result.valueOf(outcome),
                bid || "NO_BID".equals(outcome) ? B2Trace.Category.NONE : B2Trace.Category.PREPARATION);
        MethodChannel.Result pending = preparationReply; preparationReply = null;
        if (pending != null) pending.success(outcome);
    }
    private void stopPreparation() {
        fetching = false; main.removeCallbacks(fetchTimeout);
        MediationInterstitialAdUnit old = unit; unit = null;
        if (old != null) {
            try { old.destroy(); } catch (RuntimeException ignored) {}
        }
    }
    @Override protected void onDestroy() {
        destroyed = true; active = false; DemandBridge.finish(); stopPreparation();
        PrebidMobile.setStoredAuctionResponse(null);
        B2Trace.close(B2Trace.Category.DETACHED);
        main.removeCallbacksAndMessages(null);
        if (channel != null) channel.setMethodCallHandler(null);
        scanner.shutdown();
        super.onDestroy();
    }
}
