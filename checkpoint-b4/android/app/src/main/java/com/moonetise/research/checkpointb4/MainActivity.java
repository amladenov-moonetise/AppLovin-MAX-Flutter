package com.moonetise.research.checkpointb4;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapters.MediationAdapterBase;
import com.applovin.sdk.AppLovinSdk;
import com.moonetise.research.b4.*;
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
    private static final CycleGate gate = new CycleGate();
    private MethodChannel channel;
    private boolean opening, opened, destroyed, initStarted, prebidReady;
    private Boolean initResult;
    private long preparedGeneration;
    private static boolean cycleUsed;
    private final RewardObservation reward = new RewardObservation();
    private B4Trace.Scenario scenario;
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
        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), "moonetise/adapter_b4_research");
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
                                Class<?> adapter = Class.forName(B4Trace.ADAPTER, false, getClassLoader());
                                adapter.getConstructor(AppLovinSdk.class);
                                valid = InstalledApkCheck.count(getApplicationContext()) == 1
                                        && MediationAdapterBase.class.isAssignableFrom(adapter)
                                        && MaxRewardedAdapter.class.isAssignableFrom(adapter);
                            } catch (Exception | LinkageError ignored) { valid = false; }
                            final boolean accepted = valid;
                            main.post(() -> {
                                if (destroyed) return;
                                if (!accepted) {
                                    reply.error("APK_CLASS_CHECK_FAILED", "Unique compatible adapter required.", null); return;
                                }
                                try {
                                    String run = B4Trace.begin(!BuildConfig.DEBUG); opened = true;
                                    B4Trace.recordFor(0, B4Trace.Event.INSTALLED_APK_CHECK,
                                            B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
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
                            B4Trace.recordFor(0, B4Trace.Event.PREBID_INITIALIZED, B4Trace.Result.TIMEOUT, B4Trace.Category.DEADLINE);
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
                                B4Trace.recordFor(0, B4Trace.Event.PREBID_INITIALIZED,
                                        prebidReady ? B4Trace.Result.SUCCESS : B4Trace.Result.FAILED, B4Trace.Category.NONE);
                                reply.success(prebidReady);
                            }));
                        } catch (RuntimeException ignored) { initTimeout.run(); }
                        break;
                    case "begin":
                        requireOpen();
                        if (!Boolean.TRUE.equals(call.argument("authorized"))) throw new IllegalStateException("MAX_NOT_AUTHORIZED");
                        B4Trace.Scenario next = B4Trace.Scenario.valueOf(call.argument("scenario"));
                        if (next != B4Trace.Scenario.NO_DEMAND && next != B4Trace.Scenario.SYNTHETIC)
                            throw new IllegalStateException("B4_SCENARIO_NOT_SUPPORTED");
                        if (cycleUsed) throw new IllegalStateException("FRESH_PROCESS_REQUIRED");
                        if (!BuildConfig.DEBUG && next != B4Trace.Scenario.NO_DEMAND)
                            throw new IllegalStateException("RELEASE_MAX_ONLY");
                        Number window = call.argument("eligibilityMs");
                        long ms = window == null ? 5000 : window.longValue();
                        if (ms < 1000 || ms > 30000) throw new IllegalArgumentException("EXPIRY");
                        long id = gate.begin(); cycleUsed = true; scenario = next;
                        B4Trace.cycle(id, scenario);
                        RewardedDemandBridge.begin(id, true, ms);
                        // Bounded preparation/load only. A loaded creative may wait for explicit Show.
                        reply.success(id);
                        break;
                    case "prepare":
                        long preparationId = id(call.argument("cycle"));
                        if (!gate.preparing(preparationId) || preparedGeneration == preparationId)
                            throw new IllegalStateException("PREPARATION_NOT_ALLOWED");
                        preparedGeneration = preparationId;
                        PrepCall p = new PrepCall(preparationId, reply); pending = p;
                        B4Trace.recordFor(p.id, B4Trace.Event.PREPARATION_STARTED, B4Trace.Result.REQUESTED, B4Trace.Category.NONE);
                        if (scenario == B4Trace.Scenario.NO_DEMAND) { finish(p, B4Trace.Result.NO_BID); break; }
                        if (!BuildConfig.DEBUG || !prebidReady || !PrebidMobile.isSdkInitialized()) {
                            finish(p, B4Trace.Result.FAILED); break;
                        }
                        p.timeout = () -> finish(p, B4Trace.Result.TIMEOUT);
                        main.postDelayed(p.timeout, 2500);
                        try {
                            p.request = new ResearchPreparation(getApplicationContext(), p.id,
                                    result -> main.post(() -> {
                                if (pending != p || p.ended || !gate.preparing(p.id)) {
                                    B4Trace.recordFor(p.id, B4Trace.Event.STALE_CALLBACK,
                                            B4Trace.Result.LATE_IGNORED, B4Trace.Category.NONE); return;
                                }
                                finish(p, "NO_BIDS".equals(result) ? B4Trace.Result.NO_BID
                                        : "TIMEOUT".equals(result) ? B4Trace.Result.TIMEOUT : B4Trace.Result.FAILED);
                            }));
                            p.request.start();
                        } catch (RuntimeException ignored) { finish(p, B4Trace.Result.FAILED); }
                        break;
                    case "requestLoad":
                        long loading = id(call.argument("cycle"));
                        if (!gate.requestLoad(loading)) throw new IllegalStateException("LOAD_NOT_ALLOWED");
                        try { RewardedDemandBridge.arm(loading); }
                        catch (RuntimeException ignored) { RewardedDemandBridge.finish(loading); }
                        main.postDelayed(() -> {
                            if (gate.loading(loading)) unresolved(loading, B4Trace.Category.DEADLINE);
                        }, 30000);
                        reply.success(true); break;
                    case "terminal":
                        long terminal = id(call.argument("cycle"));
                        boolean loaded = Boolean.TRUE.equals(call.argument("loaded"));
                        boolean accepted = gate.terminal(terminal, loaded);
                        if (accepted) {
                            RewardedDemandBridge.finish(terminal);
                            if (!loaded) complete(terminal);
                        }
                        reply.success(accepted); break;
                    case "show":
                        boolean showing = gate.show(id(call.argument("cycle")));
                        if (showing) reward.show();
                        reply.success(showing); break;
                    case "publisherReward":
                        long rewardId = id(call.argument("cycle"));
                        if (!gate.current(rewardId) || !reward.callback())
                            throw new IllegalStateException("UNEXPECTED_REWARD_CALLBACK");
                        B4Trace.recordFor(rewardId, B4Trace.Event.HOST_REWARD, B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
                        reply.success(true); break;
                    case "hidden":
                        long hidden = id(call.argument("cycle"));
                        boolean completed = gate.hidden(hidden);
                        if (completed) { reward.hidden(); RewardedDemandBridge.finish(hidden); complete(hidden); }
                        reply.success(completed); break;
                    case "clearUnused":
                        long cleared = id(call.argument("cycle"));
                        if (!gate.current(cleared)) throw new IllegalStateException("STALE_CYCLE");
                        RewardedDemandBridge.finish(cleared);
                        B4Trace.recordFor(cleared, B4Trace.Event.CONTRIBUTION_CLEARED, B4Trace.Result.CLOSED, B4Trace.Category.NONE);
                        reply.success(null); break;
                    case "invalidate":
                        unresolved(gate.generation(), B4Trace.Category.DETACHED);
                        reply.success(null); break;
                    case "event":
                        B4Trace.host(id(call.argument("cycle")), call.argument("event"), call.argument("result"),
                                call.argument("category"), call.argument("network"), call.argument("state"));
                        reply.success(null); break;
                    case "export":
                        reply.success(B4Trace.report(id(call.argument("cycle")))); break;
                    default: reply.notImplemented();
                }
            } catch (RuntimeException ignored) {
                reply.error("RESEARCH_STATE_ERROR", "Operation rejected; inspect cycle state or restart.", null);
            }
        });
    }
    private static long id(Number value) { return value == null ? 0 : value.longValue(); }
    private void requireOpen() { if (!opened || destroyed) throw new IllegalStateException("SESSION_CLOSED"); }
    private void complete(long id) {
        B4Trace.recordFor(id, B4Trace.Event.CYCLE_COMPLETE, B4Trace.Result.SUCCESS, B4Trace.Category.NONE);
    }
    private void finish(PrepCall p, B4Trace.Result fallback) {
        if (pending != p || p.ended || !gate.preparing(p.id) || destroyed) return;
        p.ended = true;
        if (p.timeout != null) main.removeCallbacks(p.timeout);
        if (p.request != null) p.request.stop();
        ResearchPreparation.clearOverride();
        boolean bid = RewardedDemandBridge.preparationFinished(p.id);
        B4Trace.Result outcome = bid ? B4Trace.Result.BID : fallback;
        B4Trace.recordFor(p.id, B4Trace.Event.PREPARATION_RESULT, outcome, B4Trace.Category.PREPARATION);
        pending = null;
        p.reply.success(outcome.name());
    }
    private void unresolved(long id, B4Trace.Category reason) {
        if (id <= 0 || !gate.current(id) || gate.state() == CycleGate.State.COMPLETE) return;
        gate.unresolved(id); RewardedDemandBridge.finish(id);
        PrepCall p = pending; pending = null;
        if (p != null) {
            p.ended = true;
            if (p.timeout != null) main.removeCallbacks(p.timeout);
            if (p.request != null) p.request.stop();
            p.reply.error("RESTART_REQUIRED", "Cycle unresolved; do not load again.", null);
        }
        ResearchPreparation.clearOverride();
        B4Trace.recordFor(id, B4Trace.Event.HOST_ERROR, B4Trace.Result.TIMEOUT, reason);
    }
    @Override protected void onDestroy() {
        unresolved(gate.generation(), B4Trace.Category.DETACHED);
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        ResearchPreparation.clearOverride();
        if (channel != null) channel.setMethodCallHandler(null);
        scanner.shutdown();
        super.onDestroy();
    }
}
