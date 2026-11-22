package com.moonetise.research.b4;

import android.os.SystemClock;
import android.util.Log;
import com.applovin.sdk.AppLovinSdk;
import java.time.Instant;
import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed-schema bounded evidence. No request, response ID, SDK key, consent or raw errors. */
public final class B4Trace {
    public static final String TAG = "MoonetiseAdapterB4";
    public static final String ADAPTER = "com.applovin.mediation.adapters.PrebidMaxMediationAdapter";
    public static final long WINDOW_MILLIS = 180000;
    public enum Scenario { SYNTHETIC, NO_DEMAND, PREP_FAILURE, PREP_TIMEOUT, CANCEL, EXPIRE, WITHDRAW, TIER_CHECK }
    public enum Event {
        RUN_STARTED, INSTALLED_APK_CHECK, CYCLE_STARTED, CYCLE_COMPLETE, PREBID_INITIALIZED, INIT_STATUS,
        PREPARATION_STARTED, PREPARATION_RESULT, CAPTURED, STALE_CALLBACK,
        SLOT_ARMED, SLOT_CLAIM, CACHE_POP, PROVIDER_MISS, ADAPTER_DELEGATED, ADAPTER_NO_FILL,
        CONTRIBUTION_CLEARED, OWNED_CACHE_CLEARED, SDK_TIER_RESULT,
        MOONETISE_B4_ADAPTER_CONSTRUCTED, MOONETISE_B4_MAX_INITIALIZE_CALLED,
        MOONETISE_B4_MAX_LOAD_CALLED, MOONETISE_B4_ADAPTER_DESTROYED,
        HOST_MAX_INITIALIZED, HOST_LOAD_REQUESTED, HOST_LOAD_RESULT, HOST_WATERFALL,
        HOST_SELECTED_NETWORK, HOST_OPERATOR_VISIBLE, HOST_DISPLAYED, HOST_HIDDEN,
        HOST_SHOW_REQUESTED, HOST_REWARD, HOST_ERROR
    }
    public enum Result {
        SUCCESS, FAILED, TIMEOUT, BID, NO_BID, REQUESTED, LOADED, MATCH, MISMATCH,
        MISSING, EXPIRED, CONSUMED, YES, NO, CLASS_MATCH, CLASS_MISMATCH, UNKNOWN,
        ADAPTER_READY, DESTROYED, CLOSED, FRESH, CANCELLED, WITHDRAWN, LATE_IGNORED,
        FORCED_FAILURE, FORCED_TIMEOUT, INITIALIZED_SUCCESS, INITIALIZED_UNKNOWN, INITIALIZED_FAILURE
    }
    public enum Category { NONE, PREPARATION, TARGETING, AUTHORIZATION, NO_FILL, CONNECTIVITY,
        CREDENTIAL_OR_CONFIGURATION, SDK_OR_NETWORK_ERROR, HOST_EXCEPTION, DETACHED, DEADLINE, CAPTURE_LIMIT }
    public enum Network { UNKNOWN, TESTPBS, APPLOVIN, OTHER }
    public enum State { UNKNOWN, AD_LOAD_NOT_ATTEMPTED, AD_LOADED, AD_FAILED_TO_LOAD }
    private static final List<String> lines = new ArrayList<>();
    private static final Map<Long, Scenario> scenarios = new LinkedHashMap<>();
    private static String runId;
    private static long current;
    private static boolean release, overflow;
    private B4Trace() {}
    public static synchronized String begin(boolean releaseBuild) {
        if (runId != null) throw new IllegalStateException("SESSION_ALREADY_OPEN");
        release = releaseBuild; runId = UUID.randomUUID().toString();
        emit(0, Event.RUN_STARTED, Result.SUCCESS, Category.NONE, Network.UNKNOWN, State.UNKNOWN);
        return runId;
    }
    public static synchronized void cycle(long id, Scenario scenario) {
        current = id; scenarios.put(id, scenario);
        emit(id, Event.CYCLE_STARTED, Result.SUCCESS, Category.NONE, Network.UNKNOWN, State.UNKNOWN);
    }
    public static synchronized void record(Event e, Result r, Category c) { recordFor(current, e, r, c); }
    public static synchronized void recordFor(long id, Event e, Result r, Category c) {
        if (runId != null) emit(id, e, r, c, Network.UNKNOWN, State.UNKNOWN);
    }
    public static synchronized void host(long id, String event, String result, String category,
            String network, String state) {
        Event e = Event.valueOf(event);
        if (!e.name().startsWith("HOST_")) throw new IllegalArgumentException("HOST_EVENT");
        if (id != 0 && !scenarios.containsKey(id)) throw new IllegalArgumentException("CYCLE");
        emit(id, e, Result.valueOf(result), Category.valueOf(category),
                Network.valueOf(network), State.valueOf(state));
    }
    private static void emit(long id, Event e, Result r, Category c, Network n, State s) {
        if (runId == null) return;
        if (lines.size() >= 640) { overflow = true; return; }
        try {
            JSONObject j = new JSONObject();
            j.put("runId", runId); j.put("cycle", id); j.put("timestamp", Instant.now().toString());
            j.put("elapsedMs", SystemClock.elapsedRealtime()); j.put("event", e.name());
            j.put("scenario", id == 0 ? "SESSION" : scenarios.get(id).name());
            j.put("build", release ? "RELEASE" : "DEBUG");
            j.put("format", "REWARDED");
            j.put("adapterClass", ADAPTER); j.put("maxVersion", AppLovinSdk.VERSION);
            j.put("result", r.name()); j.put("category", c.name());
            j.put("network", n.name()); j.put("loadState", s.name());
            String line = j.toString(); lines.add(line); Log.i(TAG, line);
        } catch (JSONException impossible) { throw new IllegalStateException("SCHEMA"); }
    }
    private static boolean seen(long id, Event e, Result r, Network network, State state) {
        for (String line : lines) try {
            JSONObject j = new JSONObject(line);
            if (j.getLong("cycle") == id && e.name().equals(j.getString("event"))
                    && (r == null || r.name().equals(j.getString("result")))
                    && (network == null || network.name().equals(j.getString("network")))
                    && (state == null || state.name().equals(j.getString("loadState")))) return true;
        } catch (JSONException ignored) { return false; }
        return false;
    }
    private static boolean has(long id, Event e, Result r) { return seen(id, e, r, null, null); }
    private static int count(long id, Event e, Result r) {
        int total = 0;
        for (String line : lines) try {
            JSONObject j = new JSONObject(line);
            if (j.getLong("cycle") == id && e.name().equals(j.getString("event"))
                    && r.name().equals(j.getString("result"))) total++;
        } catch (JSONException ignored) {}
        return total;
    }
    private static void need(List<String> missing, boolean value, String name) { if (!value) missing.add(name); }
    public static synchronized Map<String, Object> report(long id) {
        if (!scenarios.containsKey(id)) throw new IllegalArgumentException("CYCLE");
        Scenario scenario = scenarios.get(id);
        List<String> missing = new ArrayList<>();
        need(missing, !overflow, "CAPTURE_WITHIN_LIMIT");
        boolean explicitFail = has(id, Event.HOST_OPERATOR_VISIBLE, Result.NO)
                || count(id, Event.SLOT_CLAIM, Result.MATCH) > 1
                || count(id, Event.CACHE_POP, Result.CONSUMED) > 1
                || count(id, Event.HOST_REWARD, Result.SUCCESS) > 1
                || count(id, Event.HOST_LOAD_REQUESTED, Result.REQUESTED) > 1
                || has(id, Event.OWNED_CACHE_CLEARED, Result.FAILED);
        if (scenario == Scenario.TIER_CHECK) {
            need(missing, has(id, Event.SDK_TIER_RESULT, Result.SUCCESS), "SDK_TIER_PROBE_SUCCESS");
        } else {
            need(missing, has(0, Event.INSTALLED_APK_CHECK, Result.SUCCESS), "INSTALLED_BASE_SPLIT_UNIQUENESS");
            need(missing, has(0, Event.HOST_MAX_INITIALIZED, Result.SUCCESS), "MAX_INITIALIZED");
            need(missing, (has(0, Event.MOONETISE_B4_ADAPTER_CONSTRUCTED, Result.SUCCESS) || has(id, Event.MOONETISE_B4_ADAPTER_CONSTRUCTED, Result.SUCCESS)), "ADAPTER_CONSTRUCTED");
            need(missing, (has(0, Event.MOONETISE_B4_MAX_INITIALIZE_CALLED, Result.ADAPTER_READY) || has(id, Event.MOONETISE_B4_MAX_INITIALIZE_CALLED, Result.ADAPTER_READY)), "ADAPTER_INITIALIZED");
            need(missing, count(id, Event.HOST_LOAD_REQUESTED, Result.REQUESTED) == 1, "ONE_PUBLISHER_LOAD");
            need(missing, has(id, Event.MOONETISE_B4_MAX_LOAD_CALLED, Result.REQUESTED), "ADAPTER_INVOKED");
            if (scenario == Scenario.SYNTHETIC) {
                need(missing, has(id, Event.CYCLE_COMPLETE, Result.SUCCESS), "CYCLE_COMPLETE");
                need(missing, has(id, Event.HOST_LOAD_RESULT, Result.LOADED), "GENUINE_MAX_LOADED");
                need(missing, count(id, Event.HOST_SHOW_REQUESTED, Result.REQUESTED) == 1, "ONE_EXPLICIT_SHOW");
                need(missing, has(id, Event.PREPARATION_RESULT, Result.BID), "PREPARATION_BID");
                need(missing, count(id, Event.CAPTURED, Result.FRESH) == 1, "ONE_FRESH_CAPTURE");
                need(missing, has(id, Event.SLOT_CLAIM, Result.MATCH), "MATCHING_CLAIM");
                need(missing, has(id, Event.CACHE_POP, Result.CONSUMED), "CACHE_POP");
                need(missing, has(id, Event.ADAPTER_DELEGATED, Result.SUCCESS), "DELEGATED");
                need(missing, seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.TESTPBS, null), "TESTPBS_SELECTED");
                need(missing, seen(id, Event.HOST_WATERFALL, Result.CLASS_MATCH, Network.TESTPBS, State.AD_LOADED), "TESTPBS_LOADED");
                need(missing, has(id, Event.HOST_DISPLAYED, Result.SUCCESS), "DISPLAYED");
                need(missing, has(id, Event.HOST_HIDDEN, Result.SUCCESS), "HIDDEN");
                need(missing, count(id, Event.HOST_REWARD, Result.SUCCESS) == 1, "GENUINE_PUBLISHER_REWARD_CALLBACK");
                need(missing, has(id, Event.HOST_OPERATOR_VISIBLE, Result.YES), "VISIBLE_CONFIRMATION");
            } else {
                boolean terminal = has(id, Event.HOST_LOAD_RESULT, Result.LOADED) || has(id, Event.HOST_LOAD_RESULT, Result.FAILED);
                need(missing, terminal, "GENUINE_MAX_TERMINAL");
                need(missing, !has(id, Event.SLOT_CLAIM, Result.MATCH), "NO_PREBID_CLAIM");
                need(missing, seen(id, Event.HOST_WATERFALL, Result.CLASS_MATCH, Network.TESTPBS, State.AD_FAILED_TO_LOAD), "TESTPBS_NO_FILL");
                need(missing, has(id, Event.PROVIDER_MISS, scenario == Scenario.EXPIRE ? Result.EXPIRED : Result.MISSING), "EXPECTED_PROVIDER_MISS");
                need(missing, has(id, Event.ADAPTER_NO_FILL, Result.MISSING), "ADAPTER_PROMPT_NO_FILL");
                need(missing, has(id, Event.PREPARATION_RESULT, Result.NO_BID), "NO_DEMAND_NO_AUCTION_PATH");
                boolean ordinary = seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.APPLOVIN, null)
                        || seen(id, Event.HOST_SELECTED_NETWORK, Result.LOADED, Network.OTHER, null)
                        || seen(id, Event.HOST_WATERFALL, null, Network.APPLOVIN, State.AD_FAILED_TO_LOAD)
                        || seen(id, Event.HOST_WATERFALL, null, Network.OTHER, State.AD_FAILED_TO_LOAD);
                need(missing, ordinary, "ORDINARY_MEDIATION_EVIDENCE");
                if (scenario == Scenario.CANCEL || scenario == Scenario.WITHDRAW)
                    need(missing, has(id, Event.CONTRIBUTION_CLEARED,
                            scenario == Scenario.CANCEL ? Result.CANCELLED : Result.WITHDRAWN), "OWNED_DEMAND_CLEARED");
                if (scenario == Scenario.CANCEL || scenario == Scenario.WITHDRAW || scenario == Scenario.EXPIRE) {
                    need(missing, has(id, Event.PREPARATION_RESULT, Result.BID), "PREPARED_BID_FOR_CLEANUP_TEST");
                    need(missing, has(id, Event.OWNED_CACHE_CLEARED, Result.SUCCESS), "OWNED_CACHE_REMOVED");
                }
                if (scenario == Scenario.PREP_FAILURE || scenario == Scenario.PREP_TIMEOUT)
                    need(missing, has(id, Event.PREPARATION_RESULT,
                            scenario == Scenario.PREP_FAILURE ? Result.FORCED_FAILURE : Result.FORCED_TIMEOUT), "LOCAL_FAULT_EVIDENCE");
            }
        }
        if (has(id, Event.HOST_ERROR, Result.TIMEOUT)) missing.add("RESTART_REQUIRED_UNRESOLVED");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId); report.put("cycle", id); report.put("scenario", scenario.name());
        report.put("entitlementsGranted", false);
        report.put("rewardEvidenceSource", "OFFICIAL_FLUTTER_MAX_LISTENER");
        report.put("scope", scenario == Scenario.TIER_CHECK ? "SDK_LEVEL_ONLY" : "MAX_RUNTIME");
        report.put("classification", explicitFail ? "FAIL" : missing.isEmpty() ? "PASS" : "INCONCLUSIVE");
        report.put("missingEvidence", missing);
        report.put("liveMultiTier", scenario != Scenario.TIER_CHECK && has(id, Event.PROVIDER_MISS, Result.MISMATCH)
                && has(id, Event.SLOT_CLAIM, Result.MATCH) ? "PASS" : "INCONCLUSIVE");
        List<String> selected = new ArrayList<>();
        for (String line : lines) try {
            long cycle = new JSONObject(line).getLong("cycle");
            if (cycle == 0 || cycle == id) selected.add(line);
        } catch (JSONException ignored) {}
        report.put("jsonl", String.join("\n", selected));
        return report;
    }
}
