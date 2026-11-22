import 'dart:async';
import 'dart:convert';
import 'package:applovin_max/applovin_max.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MaterialApp(home: B4Screen()));

class B4Screen extends StatefulWidget {
  const B4Screen({super.key});
  @override
  State<B4Screen> createState() => _B4ScreenState();
}

class _B4ScreenState extends State<B4Screen> with WidgetsBindingObserver {
  static const channel = MethodChannel('moonetise/adapter_b4_research');
  static const unit = '691ff46cc460ce8e';
  static const adapter = 'com.applovin.mediation.adapters.PrebidMaxMediationAdapter';
  final keyInput = TextEditingController();
  final expiryInput = TextEditingController(text: '5000');
  bool authorized = false;
  bool opening = false, session = false, prebidStarted = false, blocked = false;
  bool busy = false, terminal = false, ready = false, shown = false, hidden = false;
  bool visibleReviewed = false, diagnosticsFailed = false;
  int rewardCallbacks = 0;
  int cycle = 0, reportCycle = 0;
  String selected = 'NO_DEMAND', activeScenario = '', winner = 'UNKNOWN';
  String phase = 'IDLE', status = 'No SDK initialization on launch.', report = '';
  Timer? deadline, loadDeadline;
  bool get canRun => session && !blocked && !busy && cycle < 1
      && (cycle == 0 || phase == 'COMPLETE')
      && (activeScenario != 'SYNTHETIC' || !hidden || visibleReviewed);

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); }
  Future<void> event(String name, String result, {int? id,
      String category = 'NONE', String network = 'UNKNOWN', String state = 'UNKNOWN'}) async {
    try {
      await channel.invokeMethod<void>('event', {
        'cycle': id ?? cycle, 'event': name, 'result': result,
        'category': category, 'network': network, 'state': state,
      });
    } catch (_) { diagnosticsFailed = true; }
  }
  String network(String name) {
    if (name == 'TestPBS' || name == 'Custom Network (SDK) - TestPBS') return 'TESTPBS';
    if (name == 'AppLovin' || name == 'AppLovin Exchange' || name == 'ALX') return 'APPLOVIN';
    return 'OTHER';
  }
  String category(MaxError error) {
    if (error.code == ErrorCode.noFill) return 'NO_FILL';
    if (error.code == ErrorCode.invalidAdUnitId) return 'CREDENTIAL_OR_CONFIGURATION';
    if (error.code == ErrorCode.networkError || error.code == ErrorCode.networkTimeout) return 'CONNECTIVITY';
    return 'SDK_OR_NETWORK_ERROR';
  }
  Future<void> waterfall(int id, MaxAdWaterfallInfo? value) async {
    if (value == null) return;
    for (final response in value.networkResponses.take(24)) {
      String state = 'UNKNOWN';
      if (response.adLoadState == AdLoadState.adLoaded) state = 'AD_LOADED';
      if (response.adLoadState == AdLoadState.adFailedToLoad) state = 'AD_FAILED_TO_LOAD';
      if (response.adLoadState == AdLoadState.adLoadNotAttempted) state = 'AD_LOAD_NOT_ATTEMPTED';
      await event('HOST_WATERFALL',
        response.mediatedNetwork.adapterClassName == adapter ? 'CLASS_MATCH' : 'CLASS_MISMATCH',
        id: id, network: network(response.mediatedNetwork.name), state: state,
        category: response.error == null ? 'NONE' : category(response.error!));
    }
  }
  Future<void> initializePrebid() async {
    if (prebidStarted) return;
    prebidStarted = true;
    try { await channel.invokeMethod<bool>('initializePrebid').timeout(const Duration(seconds: 10)); }
    catch (_) { await event('HOST_ERROR', 'FAILED', id: 0, category: 'PREPARATION'); }
  }
  Future<void> open() async {
    if (opening || session || !authorized || keyInput.text.trim().isEmpty) return;
    final sdkKey = keyInput.text.trim(); keyInput.clear();
    FocusScope.of(context).unfocus();
    setState(() { opening = true; status = 'Checking installed APK and public adapter constructor...'; });
    try {
      await channel.invokeMapMethod<String, dynamic>('open', {'authorized': authorized})
          .timeout(const Duration(seconds: 15));
      // Install publisher listeners exactly once; they never create another MAX ad.
      AppLovinMAX.setRewardedAdListener(RewardedAdListener(
        onAdLoadedCallback: (ad) { loaded(ad); },
        onAdLoadFailedCallback: (id, error) { failed(id, error); },
        onAdDisplayedCallback: (ad) {
          if (ad.adUnitId == unit && shown) {
            event('HOST_DISPLAYED', 'SUCCESS');
            if (mounted) setState(() => status = 'Genuine DISPLAYED callback. Reward remains separate.');
          }
        },
        onAdHiddenCallback: (ad) { if (ad.adUnitId == unit) dismissed(); },
        onAdDisplayFailedCallback: (_, __) { stop('MAX display failed; restart required.'); },
        onAdReceivedRewardCallback: (ad, reward) {
          if (ad.adUnitId == unit && shown) receivedReward();
        },
        onAdClickedCallback: (_) {},
        onAdRevenuePaidCallback: (_) {}, // Publisher owns attribution; no amounts exported.
      ));
      if (selected == 'SYNTHETIC') await initializePrebid();
      if (blocked || !mounted) return;
      AppLovinMAX.setVerboseLogging(false);
      final config = await AppLovinMAX.initialize(sdkKey).timeout(const Duration(seconds: 20));
      final initialized = await AppLovinMAX.isInitialized().timeout(const Duration(seconds: 3));
      if (config == null || initialized != true) throw StateError('MAX_NOT_READY');
      await event('HOST_MAX_INITIALIZED', 'SUCCESS', id: 0);
      if (mounted) setState(() { session = true; status = 'Session initialized. Run one cycle at a time.'; });
    } on PlatformException catch (error) {
      blocked = true;
      if (mounted) setState(() => status = error.code == 'APK_CLASS_CHECK_FAILED'
          ? 'FAIL: installed APK must contain exactly one compatible original-name adapter.'
          : 'Session/channel failure: INCONCLUSIVE. Restart; no automatic retry.');
    } catch (_) {
      blocked = true;
      if (mounted) setState(() => status = 'Session/preflight/init failed: INCONCLUSIVE. Restart; no automatic retry.');
    } finally { if (mounted) setState(() => opening = false); }
  }

  Future<void> runCycle() async {
    final expiry = int.tryParse(expiryInput.text);
    if (!canRun || !authorized || expiry == null || expiry < 1000 || expiry > 30000) return;
    final mode = kReleaseMode ? 'NO_DEMAND' : selected;
    setState(() { busy = true; status = 'Starting next explicit cycle...'; });
    try {
      // A ready/cached MAX ad would not demonstrate a new mediation request.
      if (await AppLovinMAX.isRewardedAdReady(unit) == true) {
        await stop('MAX already has a ready ad: cannot establish a fresh cycle. INCONCLUSIVE; restart.'); return;
      }
      final id = await channel.invokeMethod<int>('begin', {
        'authorized': authorized, 'scenario': mode, 'eligibilityMs': expiry,
      });
      if (id == null) throw StateError('NO_CYCLE');
      cycle = id; reportCycle = id; activeScenario = mode;
      terminal = false; ready = false; shown = false; hidden = false; visibleReviewed = false;
      winner = 'UNKNOWN'; phase = 'PREPARING';
      deadline?.cancel();
      // Loading has its own bounded deadline. Do not expire a loaded or shown creative.
      if (mounted) setState(() => status = 'Cycle $cycle: $mode preparation...');
      String preparation = 'FAILED';
      try {
        preparation = await channel.invokeMethod<String>('prepare', {'cycle': id})
            .timeout(const Duration(seconds: 5)) ?? 'FAILED';
      } catch (_) {
        // Optional contribution failure must not suppress the independently authorized MAX load.
        await event('HOST_ERROR', 'FAILED', id: id, category: 'PREPARATION');
        try { await channel.invokeMethod<void>('clearUnused', {'cycle': id}); }
        catch (_) { diagnosticsFailed = true; }
      }
      if (blocked || !mounted || id != cycle) return;
      final accepted = await channel.invokeMethod<bool>('requestLoad', {'cycle': id});
      if (accepted != true) throw StateError('LOAD_NOT_ALLOWED');
      phase = 'LOADING';
      await event('HOST_LOAD_REQUESTED', 'REQUESTED', id: id);
      if (blocked || !mounted) return;
      setState(() => status = 'Cycle $id preparation: $preparation. One official MAX load requested.');
      loadDeadline = Timer(const Duration(seconds: 31), () {
        if (cycle == id && !terminal) stop('MAX completion unknown; restart required.');
      });
      AppLovinMAX.loadRewardedAd(unit);
    } catch (_) {
      await stop('Cycle operation rejected/failed: INCONCLUSIVE; restart required.');
    }
  }
  Future<void> loaded(MaxAd ad) async {
    if (blocked || terminal || cycle == 0 || ad.adUnitId != unit) return;
    final id = cycle;
    if (phase != 'LOADING') { await stop('Unexpected MAX callback before authorized load. Restart.'); return; }
    terminal = true; loadDeadline?.cancel();
    try {
      final accepted = await channel.invokeMethod<bool>('terminal', {'cycle': id, 'loaded': true});
      if (accepted != true) { await stop('Late/unresolved MAX load callback. Restart.'); return; }
      await event('HOST_LOAD_RESULT', 'LOADED', id: id);
      winner = network(ad.networkName);
      await event('HOST_SELECTED_NETWORK', 'LOADED', id: id, network: winner);
      await waterfall(id, ad.waterfall);
      if (mounted && !blocked) setState(() {
        phase = 'LOADED'; ready = true;
        status = 'Genuine LOADED: $winner. Explicit operator Show is available.';
      });
    } catch (_) { await stop('Terminal reporting failed; restart required.'); }
  }
  Future<void> failed(String unitId, MaxError error) async {
    if (blocked || terminal || cycle == 0 || unitId != unit) return;
    final id = cycle;
    if (phase != 'LOADING') { await stop('Unexpected MAX failure callback. Restart.'); return; }
    terminal = true; loadDeadline?.cancel();
    try {
      final accepted = await channel.invokeMethod<bool>('terminal', {'cycle': id, 'loaded': false});
      if (accepted != true) { await stop('Late MAX failure; restart required.'); return; }
      await event('HOST_LOAD_RESULT', 'FAILED', id: id, category: category(error));
      await waterfall(id, error.waterfall);
      deadline?.cancel();
      if (mounted) setState(() { phase = 'COMPLETE'; busy = false; status = 'Cycle $id MAX failed. Export its report; fresh process required.'; });
    } catch (_) { await stop('Terminal reporting failed; restart required.'); }
  }
  Future<void> show() async {
    if (!ready || shown || blocked) return;
    try {
      if (await AppLovinMAX.isRewardedAdReady(unit) != true) { await stop('Loaded ad no longer ready. Restart.'); return; }
      if (await channel.invokeMethod<bool>('show', {'cycle': cycle}) != true) {
        await stop('Show state rejected. Restart.'); return;
      }
      shown = true; ready = false; phase = 'SHOWING';
      if (mounted) setState(() => status = 'Explicit publisher Show requested.');
      await event('HOST_SHOW_REQUESTED', 'REQUESTED');
      AppLovinMAX.showRewardedAd(unit);
    } catch (_) { await stop('Show failed/unresolved. Restart.'); }
  }
  Future<void> receivedReward() async {
    // Invoked ONLY by official Flutter MAX onAdReceivedRewardCallback; no entitlements.
    rewardCallbacks++;
    try {
      if (await channel.invokeMethod<bool>('publisherReward', {'cycle': cycle}) != true) diagnosticsFailed = true;
    } catch (_) { diagnosticsFailed = true; }
    if (mounted) setState(() => status = 'GENUINE PUBLISHER MAX REWARD CALLBACK ($rewardCallbacks). No game entitlement granted.');
  }
  Future<void> dismissed() async {
    if (!shown || hidden || phase != 'SHOWING') return;
    final id = cycle;
    try {
      if (await channel.invokeMethod<bool>('hidden', {'cycle': id}) != true) {
        await stop('Hidden callback rejected; restart.'); return;
      }
      await event('HOST_HIDDEN', 'SUCCESS', id: id);
      deadline?.cancel(); loadDeadline?.cancel();
      if (mounted) setState(() {
        hidden = true; ready = false; busy = false; phase = 'COMPLETE';
        status = 'Cycle $id hidden. Confirm synthetic visibility, export its report, fresh process required.';
      });
    } catch (_) { await stop('Hidden reporting failed; restart.'); }
  }
  Future<void> clearUnused() async {
    try {
      await channel.invokeMethod<void>('clearUnused', {'cycle': cycle});
      if (mounted) setState(() => status = 'Unused preparation cleared. Loaded creative remains owned by MAX; Show stays available.');
    } catch (_) { diagnosticsFailed = true; }
  }
  Future<void> visible(bool yes) async {
    await event('HOST_OPERATOR_VISIBLE', yes ? 'YES' : 'NO');
    if (mounted) setState(() { visibleReviewed = true; if (!yes) blocked = true; });
  }
  Future<void> stop(String message) async {
    blocked = true; ready = false; busy = false;
    deadline?.cancel(); loadDeadline?.cancel();
    try { await channel.invokeMethod<void>('invalidate'); } catch (_) { diagnosticsFailed = true; }
    if (cycle > 0) await event('HOST_ERROR', 'TIMEOUT', category: 'DEADLINE');
    if (mounted) setState(() => status = message);
  }
  Future<void> exportReport() async {
    if (reportCycle == 0) return;
    try {
      final value = await channel.invokeMapMethod<String, dynamic>('export', {'cycle': reportCycle});
      if (value == null) return;
      final header = Map<String, dynamic>.from(value);
      final lines = header.remove('jsonl') as String? ?? '';
      header['publisherRewardCallbacks'] = rewardCallbacks;
      header['rewardEvidenceSource'] = 'OFFICIAL_FLUTTER_MAX_LISTENER';
      header['entitlementsGranted'] = false;
      header['hostDiagnosticsHealthy'] = !diagnosticsFailed;
      if (diagnosticsFailed) {
        header['nativeClassification'] = header['classification'];
        header['classification'] = 'INCONCLUSIVE';
      }
      final output = '${jsonEncode(header)}\n$lines';
      await Clipboard.setData(ClipboardData(text: output));
      if (mounted) setState(() => report = output);
    } catch (_) { if (mounted) setState(() => status = 'Export failed: INCONCLUSIVE.'); }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (session && busy && !shown && state == AppLifecycleState.paused) {
      stop('Backgrounded with pending/loaded ad; restart required.');
    }
  }
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    deadline?.cancel(); loadDeadline?.cancel();
    if (session) channel.invokeMethod<void>('invalidate');
    AppLovinMAX.setRewardedAdListener(null);
    keyInput.dispose(); expiryInput.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Moonetise B4 — Rewarded E2E')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('RESEARCH ONLY. One scenario per fresh process. Installing replaces the Asterise sample. No automatic show or game entitlements.'),
      TextField(controller: keyInput, enabled: !opening && !session && !blocked, obscureText: true,
        autocorrect: false, enableSuggestions: false,
        decoration: const InputDecoration(labelText: 'Development MAX SDK key — memory only')),
      DropdownButton<String>(value: selected, items: [
        const DropdownMenuItem(value: 'NO_DEMAND', child: Text('A — NO PREBID DEMAND')),
        if (!kReleaseMode) const DropdownMenuItem(value: 'SYNTHETIC', child: Text('B — SYNTHETIC REWARDED DEMAND')),
      ], onChanged: session || opening ? null : (value) => setState(() => selected = value!)),
      CheckboxListTile(value: authorized,
        title: const Text('Publisher privacy/CMP and independent MAX authorization are satisfied. For B, I authorize the fixed synthetic PBS test. This does not set consent.'),
        onChanged: session || opening ? null : (value) => setState(() => authorized = value ?? false)),
      TextField(controller: expiryInput, enabled: !busy && cycle == 0, keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'Eligibility ms: 1000–30000; default 5000')),
      ElevatedButton(onPressed: !session && !opening && !blocked && authorized ? open : null,
        child: const Text('INITIALIZE')),
      ElevatedButton(onPressed: canRun ? runCycle : null, child: const Text('RUN REWARDED TEST')),
      ElevatedButton(onPressed: ready && !shown && !blocked ? show : null, child: const Text('SHOW REWARDED')),
      Text(status),
      Text('Genuine publisher reward callbacks: $rewardCallbacks. No game currency granted.'),
      if (activeScenario == 'SYNTHETIC' && hidden && !visibleReviewed && winner == 'TESTPBS') ...[
        TextButton(onPressed: () => visible(true), child: const Text('Expected synthetic rewarded creative visibly confirmed')),
        TextButton(onPressed: () => visible(false), child: const Text('Expected creative NOT visible')),
      ],
      TextButton(onPressed: cycle > 0 ? exportReport : null, child: const Text('COPY REDACTED REPORT')),
      if (report.isNotEmpty) SelectableText(report),
    ])),
  );
}
