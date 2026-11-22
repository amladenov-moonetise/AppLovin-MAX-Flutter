package com.moonetise.research.checkpointb5;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapters.MediationAdapterBase;
import com.applovin.sdk.AppLovinSdk;
import com.moonetise.research.b5.*;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.util.*;
import java.util.concurrent.*;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.TargetingParams;
import org.prebid.mobile.api.data.InitializationStatus;

/** Publisher MAX calls stay exclusively in official Flutter MAX. */
public final class MainActivity extends FlutterActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private MethodChannel channel;
    private boolean opening, opened, destroyed, initStarted, prebidReady;
    private Boolean initResult;
    private static boolean cycleUsed;
    private long generation;
    private boolean prepared, loadRequested, terminal, disposed, invalidated, cancelled;
    private B5Trace.Scenario scenario;
    private PrepCall pending;
    private static final class PrepCall {
        final long id;
        final MethodChannel.Result reply;
        ResearchPreparation request;
        Runnable timeout;
        boolean ended;
        PrepCall(long id, MethodChannel.Result reply) { this.id = id; this.reply = reply; }
    }
    @Override public void configureFlutterEngine(@NonNull FlutterEngine engine) {
        super.configureFlutterEngine(engine);
        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), "moonetise/adapter_b5_research");
        channel.setMethodCallHandler((call, reply) -> {
            try {
                switch (call.method) {
                    case "open":
                        if (opening || opened || destroyed || !Boolean.TRUE.equals(call.argument("authorized"))
                                || !"com.moonetise.asterise.sample".equals(getPackageName()))
                            throw new IllegalStateException("SESSION_NOT_ALLOWED");
                        opening = true;
                        scanner.execute(() -> {
                            boolean valid;
                            try {
                                Class<?> adapter = Class.forName(B5Trace.ADAPTER, false, getClassLoader());
                                adapter.getConstructor(AppLovinSdk.class);
                                valid = InstalledApkCheck.count(getApplicationContext()) == 1
                                        && MediationAdapterBase.class.isAssignableFrom(adapter)
                                        && MaxAdViewAdapter.class.isAssignableFrom(adapter);
                            } catch (Exception | LinkageError ignored) { valid = false; }
                            final boolean accepted = valid;
                            main.post(() -> {
                                if (destroyed) return;
                                if (!accepted) {
                                    reply.error("APK_CLASS_CHECK_FAILED", "Unique compatible adapter required.", null); return;
                                }
                                try {
                                    String run = B5Trace.begin(!BuildConfig.DEBUG); opened = true;
                                    B5Trace.recordFor(0, B5Trace.Event.INSTALLED_APK_CHECK,
                                            B5Trace.Result.SUCCESS, B5Trace.Category.NONE);
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("runId", run); info.put("release", !BuildConfig.DEBUG);
                                    info.put("definitions", 1); info.put("constructor", true);
                                    reply.success(info);
                                } catch (RuntimeException ignored) {
                                    reply.error("FRESH_PROCESS_REQUIRED", "Use a fresh process.", null);
                                }
                            });
                        });
                        break;
                    case "initializePrebid":
                        requireOpen();
                        if (initStarted) {
                            if (initResult == null) throw new IllegalStateException("INITIALIZATION_PENDING");
                            reply.success(initResult); break;
                        }
                        initStarted = true;
                        final boolean[] done = { false };
                        Runnable initTimeout = () -> {
                            if (done[0] || destroyed) return;
                            done[0] = true; initResult = false;
                            B5Trace.recordFor(0, B5Trace.Event.PREBID_INITIALIZED, B5Trace.Result.TIMEOUT, B5Trace.Category.DEADLINE);
                            reply.success(false);
                        };
                        main.postDelayed(initTimeout, 8000);
                        try {
                            PrebidMobile.setLogLevel(PrebidMobile.LogLevel.NONE);
                            PrebidMobile.setPbsDebug(false);
                            PrebidMobile.setShareGeoLocation(false);
                            TargetingParams.setSendSharedId(false);
                            ResearchPreparation.clearOverride();
                            PrebidMobile.setPrebidServerAccountId("moonetise-test");
                            PrebidMobile.setTimeoutMillis(2000);
                            PrebidMobile.initializeSdk(getApplicationContext(),
                                    "https://pbs-test.moonetise.com/openrtb2/auction", status -> main.post(() -> {
                                if (done[0] || destroyed) return;
                                done[0] = true; main.removeCallbacks(initTimeout);
                                prebidReady = (status == InitializationStatus.SUCCEEDED
                                        || status == InitializationStatus.SERVER_STATUS_WARNING) && PrebidMobile.isSdkInitialized();
                                initResult = prebidReady;
                                B5Trace.recordFor(0, B5Trace.Event.PREBID_INITIALIZED,
                                        prebidReady ? B5Trace.Result.SUCCESS : B5Trace.Result.FAILED, B5Trace.Category.NONE);
                                reply.success(prebidReady);
                            }));
                        } catch (RuntimeException ignored) { initTimeout.run(); }
                        break;
                    case "begin":
                        requireOpen();
                        if (!Boolean.TRUE.equals(call.argument("authorized")) || cycleUsed)
                            throw new IllegalStateException("FRESH_AUTHORIZED_PROCESS_REQUIRED");
                        scenario = B5Trace.Scenario.valueOf(call.argument("scenario"));
                        if (scenario != B5Trace.Scenario.NO_DEMAND && scenario != B5Trace.Scenario.SYNTHETIC)
                            throw new IllegalArgumentException("SCENARIO");
                        if (!BuildConfig.DEBUG && scenario != B5Trace.Scenario.NO_DEMAND)
                            throw new IllegalStateException("RELEASE_MAX_ONLY");
                        Number window = call.argument("eligibilityMs");
                        long ms = window == null ? 5000 : window.longValue();
                        if (ms < 1000 || ms > 30000) throw new IllegalArgumentException("EXPIRY");
                        generation = 1; cycleUsed = true;
                        B5Trace.cycle(generation, scenario);
                        BannerDemandBridge.begin(generation, true, ms);
                        reply.success(generation); break;
                    case "prepare":
                        requireCycle(call.argument("cycle"));
                        if (prepared || loadRequested || disposed || invalidated) throw new IllegalStateException("PREPARATION_NOT_ALLOWED");
                        prepared = true;
                        PrepCall p = new PrepCall(generation, reply); pending = p;
                        B5Trace.recordFor(p.id, B5Trace.Event.PREPARATION_STARTED, B5Trace.Result.REQUESTED, B5Trace.Category.NONE);
                        if (scenario == B5Trace.Scenario.NO_DEMAND) { finish(p, B5Trace.Result.NO_BID); break; }
                        if (!BuildConfig.DEBUG || !prebidReady || !PrebidMobile.isSdkInitialized()) {
                            finish(p, B5Trace.Result.FAILED); break;
                        }
                        p.timeout = () -> finish(p, B5Trace.Result.TIMEOUT);
                        main.postDelayed(p.timeout, 2500);
                        try {
                            p.request = new ResearchPreparation(getApplicationContext(), p.id,
                                    result -> main.post(() -> {
                                if (pending != p || p.ended || disposed || invalidated) {
                                    B5Trace.recordFor(p.id, B5Trace.Event.STALE_CALLBACK,
                                            B5Trace.Result.LATE_IGNORED, B5Trace.Category.NONE); return;
                                }
                                finish(p, "NO_BIDS".equals(result) ? B5Trace.Result.NO_BID
                                        : "TIMEOUT".equals(result) ? B5Trace.Result.TIMEOUT : B5Trace.Result.FAILED);
                            }));
                            p.request.start();
                        } catch (RuntimeException ignored) { finish(p, B5Trace.Result.FAILED); }
                        break;
                    case "cancelPreparation":
                        requireCycle(call.argument("cycle"));
                        if (!prepared || loadRequested || disposed || invalidated)
                            throw new IllegalStateException("CANCEL_BEFORE_LOAD_ONLY");
                        cancelled = true;
                        if (pending != null) finish(pending, B5Trace.Result.CANCELLED);
                        else BannerDemandBridge.revoke(generation, B5Trace.Result.CANCELLED);
                        reply.success(true); break;
                    case "requestLoad":
                        requireCycle(call.argument("cycle"));
                        if (!prepared || pending != null || loadRequested || disposed || invalidated)
                            throw new IllegalStateException("LOAD_NOT_ALLOWED");
                        try { BannerDemandBridge.arm(generation); }
                        catch (RuntimeException unavailable) { BannerDemandBridge.finish(generation); }
                        loadRequested = true;
                        main.postDelayed(() -> {
                            if (!terminal && !disposed) invalidate(B5Trace.Result.TIMEOUT, B5Trace.Category.DEADLINE);
                        }, 30000);
                        reply.success(true); break;
                    case "terminal":
                        requireCycle(call.argument("cycle"));
                        boolean accepted = loadRequested && !terminal && !disposed && !invalidated;
                        if (accepted) {
                            terminal = true;
                            BannerDemandBridge.finish(generation); // Never destroys a claimed renderer.
                        }
                        reply.success(accepted); break;
                    case "clearUnused":
                        requireCycle(call.argument("cycle"));
                        BannerDemandBridge.finish(generation); reply.success(null); break;
                    case "disposeContribution":
                        requireCycle(call.argument("cycle"));
                        disposed = true;
                        stopPreparation();
                        BannerDemandBridge.finish(generation);
                        ResearchPreparation.clearOverride();
                        reply.success(true); break;
                    case "invalidate":
                        invalidate(B5Trace.Result.FAILED, B5Trace.Category.DETACHED);
                        reply.success(null); break;
                    case "event":
                        B5Trace.host(id(call.argument("cycle")), call.argument("event"), call.argument("result"),
                                call.argument("category"), call.argument("network"), call.argument("state"));
                        reply.success(null); break;
                    case "export":
                        reply.success(B5Trace.report(id(call.argument("cycle")))); break;
                    default: reply.notImplemented();
                }
            } catch (RuntimeException ignored) {
                reply.error("RESEARCH_STATE_ERROR", "Operation rejected; restart if unresolved.", null);
            }
        });
    }
    private static long id(Number n) { return n == null ? 0 : n.longValue(); }
    private void requireOpen() { if (!opened || destroyed) throw new IllegalStateException("SESSION_CLOSED"); }
    private void requireCycle(Number n) {
        requireOpen();
        if (generation == 0 || generation != id(n)) throw new IllegalStateException("STALE_CYCLE");
    }
    private void finish(PrepCall p, B5Trace.Result fallback) {
        if (pending != p || p.ended || destroyed) return;
        p.ended = true;
        if (p.timeout != null) main.removeCallbacks(p.timeout);
        if (p.request != null) p.request.stop();
        ResearchPreparation.clearOverride();
        boolean bid = BannerDemandBridge.preparationFinished(p.id);
        B5Trace.Result result = bid ? B5Trace.Result.BID : fallback;
        B5Trace.recordFor(p.id, B5Trace.Event.PREPARATION_RESULT, result, B5Trace.Category.PREPARATION);
        if (cancelled) { BannerDemandBridge.revoke(p.id, B5Trace.Result.CANCELLED); result = B5Trace.Result.CANCELLED; }
        pending = null; p.reply.success(result.name());
    }
    private void stopPreparation() {
        PrepCall p = pending; pending = null;
        if (p != null && !p.ended) {
            p.ended = true;
            if (p.timeout != null) main.removeCallbacks(p.timeout);
            if (p.request != null) p.request.stop();
            p.reply.success(B5Trace.Result.CANCELLED.name());
        }
    }
    private void invalidate(B5Trace.Result result, B5Trace.Category category) {
        if (generation == 0 || invalidated) return;
        invalidated = true;
        stopPreparation(); BannerDemandBridge.finish(generation); ResearchPreparation.clearOverride();
        B5Trace.recordFor(generation, B5Trace.Event.HOST_ERROR, result, category);
    }
    @Override protected void onDestroy() {
        invalidate(B5Trace.Result.FAILED, B5Trace.Category.DETACHED);
        destroyed = true;
        main.removeCallbacksAndMessages(null); ResearchPreparation.clearOverride();
        if (channel != null) channel.setMethodCallHandler(null);
        scanner.shutdown();
        super.onDestroy();
    }
}
