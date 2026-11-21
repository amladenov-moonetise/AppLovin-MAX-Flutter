package com.moonetise.research.checkpointb3;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapters.MediationAdapterBase;
import com.applovin.sdk.AppLovinSdk;
import com.moonetise.research.b3.*;
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
    private long eligibilityMs, preparedGeneration;
    private B3Trace.Scenario scenario;
    private String otherTier;
    private PrepCall pending;
    private Runnable cycleDeadline;
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
        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), "moonetise/adapter_b3_research");
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
                                Class<?> adapter = Class.forName(B3Trace.ADAPTER, false, getClassLoader());
                                adapter.getConstructor(AppLovinSdk.class);
                                valid = InstalledApkCheck.count(getApplicationContext()) == 1
                                        && MediationAdapterBase.class.isAssignableFrom(adapter)
                                        && MaxInterstitialAdapter.class.isAssignableFrom(adapter);
                            } catch (Exception | LinkageError ignored) { valid = false; }
                            final boolean accepted = valid;
                            main.post(() -> {
                                if (destroyed) return;
                                if (!accepted) {
                                    reply.error("APK_CLASS_CHECK_FAILED", "Unique compatible adapter required.", null); return;
                                }
                                try {
                                    String run = B3Trace.begin(!BuildConfig.DEBUG); opened = true;
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
                            B3Trace.recordFor(0, B3Trace.Event.PREBID_INITIALIZED, B3Trace.Result.TIMEOUT, B3Trace.Category.DEADLINE);
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
                                B3Trace.recordFor(0, B3Trace.Event.PREBID_INITIALIZED,
                                        prebidReady ? B3Trace.Result.SUCCESS : B3Trace.Result.FAILED, B3Trace.Category.NONE);
                                reply.success(prebidReady);
                            }));
                        } catch (RuntimeException ignored) { initTimeout.run(); }
                        break;
                    case "begin":
                        requireOpen();
                        if (!Boolean.TRUE.equals(call.argument("authorized"))) throw new IllegalStateException("MAX_NOT_AUTHORIZED");
                        B3Trace.Scenario next = B3Trace.Scenario.valueOf(call.argument("scenario"));
                        if (!BuildConfig.DEBUG && next != B3Trace.Scenario.NO_DEMAND)
                            throw new IllegalStateException("RELEASE_MAX_ONLY");
                        Number window = call.argument("eligibilityMs");
                        long ms = window == null ? 5000 : window.longValue();
                        if (ms < 1000 || ms > 30000) throw new IllegalArgumentException("EXPIRY");
                        otherTier = call.argument("otherTier");
                        if (next == B3Trace.Scenario.TIER_CHECK && (!Boolean.TRUE.equals(call.argument("tierVerified"))
                                || otherTier == null || !otherTier.matches("(0|[1-9][0-9]{0,2})\\.[0-9]{2}")
                                || "3.00".equals(otherTier))) throw new IllegalArgumentException("VERIFY_EXISTING_TIER");
                        long id = gate.begin(); scenario = next; eligibilityMs = ms;
                        B3Trace.cycle(id, scenario);
                        DemandBridge.begin(id, true, ms);
                        cycleDeadline = () -> unresolved(id, B3Trace.Category.DEADLINE);
                        main.postDelayed(cycleDeadline, B3Trace.WINDOW_MILLIS);
                        reply.success(id);
                        break;
                    case "prepare":
                        long preparationId = id(call.argument("cycle"));
                        if (!gate.preparing(preparationId) || preparedGeneration == preparationId)
                            throw new IllegalStateException("PREPARATION_NOT_ALLOWED");
                        preparedGeneration = preparationId;
                        PrepCall p = new PrepCall(preparationId, reply); pending = p;
                        B3Trace.recordFor(p.id, B3Trace.Event.PREPARATION_STARTED, B3Trace.Result.REQUESTED, B3Trace.Category.NONE);
                        if (scenario == B3Trace.Scenario.NO_DEMAND) { finish(p, B3Trace.Result.NO_BID); break; }
                        if (scenario == B3Trace.Scenario.PREP_FAILURE) { finish(p, B3Trace.Result.FORCED_FAILURE); break; }
                        if (scenario == B3Trace.Scenario.PREP_TIMEOUT) {
                            p.timeout = () -> finish(p, B3Trace.Result.FORCED_TIMEOUT);
                            main.postDelayed(p.timeout, 2500); break;
                        }
                        if (!BuildConfig.DEBUG || !prebidReady || !PrebidMobile.isSdkInitialized()) {
                            finish(p, B3Trace.Result.FAILED); break;
                        }
                        p.timeout = () -> finish(p, B3Trace.Result.TIMEOUT);
                        main.postDelayed(p.timeout, 2500);
                        try {
                            p.request = new ResearchPreparation(getApplicationContext(), p.id,
                                    result -> main.post(() -> {
                                if (pending != p || p.ended || !gate.preparing(p.id)) {
                                    B3Trace.recordFor(p.id, B3Trace.Event.STALE_CALLBACK,
                                            B3Trace.Result.LATE_IGNORED, B3Trace.Category.NONE); return;
                                }
                                finish(p, "NO_BIDS".equals(result) ? B3Trace.Result.NO_BID
                                        : "TIMEOUT".equals(result) ? B3Trace.Result.TIMEOUT : B3Trace.Result.FAILED);
                            }));
                            p.request.start();
                        } catch (RuntimeException ignored) { finish(p, B3Trace.Result.FAILED); }
                        break;
                    case "requestLoad":
                        long loading = id(call.argument("cycle"));
                        if (!gate.requestLoad(loading)) throw new IllegalStateException("LOAD_NOT_ALLOWED");
                        try { DemandBridge.arm(loading); }
                        catch (RuntimeException ignored) { DemandBridge.finish(loading); }
                        main.postDelayed(() -> {
                            if (gate.loading(loading)) unresolved(loading, B3Trace.Category.DEADLINE);
                        }, 30000);
                        reply.success(true); break;
                    case "terminal":
                        long terminal = id(call.argument("cycle"));
                        boolean loaded = Boolean.TRUE.equals(call.argument("loaded"));
                        boolean accepted = gate.terminal(terminal, loaded);
                        if (accepted) {
                            DemandBridge.finish(terminal);
                            if (!loaded) complete(terminal);
                        }
                        reply.success(accepted); break;
                    case "show":
                        reply.success(gate.show(id(call.argument("cycle")))); break;
                    case "hidden":
                        long hidden = id(call.argument("cycle"));
                        boolean completed = gate.hidden(hidden);
                        if (completed) { DemandBridge.finish(hidden); complete(hidden); }
                        reply.success(completed); break;
                    case "clearUnused":
                        long cleared = id(call.argument("cycle"));
                        if (!gate.current(cleared)) throw new IllegalStateException("STALE_CYCLE");
                        DemandBridge.finish(cleared);
                        B3Trace.recordFor(cleared, B3Trace.Event.CONTRIBUTION_CLEARED, B3Trace.Result.CLOSED, B3Trace.Category.NONE);
                        reply.success(null); break;
                    case "invalidate":
                        unresolved(gate.generation(), B3Trace.Category.DETACHED);
                        reply.success(null); break;
                    case "event":
                        B3Trace.host(id(call.argument("cycle")), call.argument("event"), call.argument("result"),
                                call.argument("category"), call.argument("network"), call.argument("state"));
                        reply.success(null); break;
                    case "export":
                        reply.success(B3Trace.report(id(call.argument("cycle")))); break;
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
        if (cycleDeadline != null) main.removeCallbacks(cycleDeadline);
        B3Trace.recordFor(id, B3Trace.Event.CYCLE_COMPLETE, B3Trace.Result.SUCCESS, B3Trace.Category.NONE);
    }
    private void finish(PrepCall p, B3Trace.Result fallback) {
        if (pending != p || p.ended || !gate.preparing(p.id) || destroyed) return;
        p.ended = true;
        if (p.timeout != null) main.removeCallbacks(p.timeout);
        if (p.request != null) p.request.stop();
        ResearchPreparation.clearOverride();
        boolean bid = DemandBridge.preparationFinished(p.id);
        B3Trace.Result outcome = bid ? B3Trace.Result.BID : fallback;
        B3Trace.recordFor(p.id, B3Trace.Event.PREPARATION_RESULT, outcome, B3Trace.Category.PREPARATION);
        if (bid && (scenario == B3Trace.Scenario.CANCEL || scenario == B3Trace.Scenario.WITHDRAW)) {
            outcome = scenario == B3Trace.Scenario.CANCEL ? B3Trace.Result.CANCELLED : B3Trace.Result.WITHDRAWN;
            DemandBridge.revoke(p.id, outcome);
        }
        if (scenario == B3Trace.Scenario.TIER_CHECK) {
            boolean passed = BuildConfig.DEBUG && bid && DemandBridge.tierProbe(p.id, otherTier);
            DemandBridge.finish(p.id); gate.probeComplete(p.id); complete(p.id);
            B3Trace.recordFor(p.id, B3Trace.Event.SDK_TIER_RESULT,
                    passed ? B3Trace.Result.SUCCESS : B3Trace.Result.FAILED, B3Trace.Category.TARGETING);
            pending = null; p.reply.success(passed ? "SDK_PASS" : "SDK_INCONCLUSIVE"); return;
        }
        pending = null;
        final String result = outcome.name();
        if (bid && scenario == B3Trace.Scenario.EXPIRE) {
            main.postDelayed(() -> {
                if (!destroyed && gate.preparing(p.id)) p.reply.success(result);
            }, eligibilityMs + 100);
        } else p.reply.success(result);
    }
    private void unresolved(long id, B3Trace.Category reason) {
        if (id <= 0 || !gate.current(id) || gate.state() == CycleGate.State.COMPLETE) return;
        gate.unresolved(id); DemandBridge.finish(id);
        PrepCall p = pending; pending = null;
        if (p != null) {
            p.ended = true;
            if (p.timeout != null) main.removeCallbacks(p.timeout);
            if (p.request != null) p.request.stop();
            p.reply.error("RESTART_REQUIRED", "Cycle unresolved; do not load again.", null);
        }
        ResearchPreparation.clearOverride();
        B3Trace.recordFor(id, B3Trace.Event.HOST_ERROR, B3Trace.Result.TIMEOUT, reason);
    }
    @Override protected void onDestroy() {
        unresolved(gate.generation(), B3Trace.Category.DETACHED);
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        ResearchPreparation.clearOverride();
        if (channel != null) channel.setMethodCallHandler(null);
        scanner.shutdown();
        super.onDestroy();
    }
}
