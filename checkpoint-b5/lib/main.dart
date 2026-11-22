import 'dart:async';
import 'dart:convert';
import 'package:applovin_max/applovin_max.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MaterialApp(home: B5Screen()));

class B5Screen extends StatefulWidget {
  const B5Screen({super.key});
  @override
  State<B5Screen> createState() => _B5ScreenState();
}

class _B5ScreenState extends State<B5Screen> with WidgetsBindingObserver {
  static const channel = MethodChannel('moonetise/adapter_b5_research');
  static const unit = 'cecda62026b8a5bb';
  static const adapter = 'com.applovin.mediation.adapters.PrebidMaxMediationAdapter';
  final keyInput = TextEditingController();
  final expiryInput = TextEditingController(text: '5000');
  bool authorized = false, opening = false, initialized = false, blocked = false;
  bool started = false, hold = false, terminal = false, loadedAd = false;
  bool removing = false, removed = false, visibleReviewed = false, disposalReviewed = false;
  bool diagnosticsFailed = false;
  int cycle = 0, revenueCallbacks = 0;
  String scenario = 'NO_DEMAND', phase = 'IDLE', winner = 'UNKNOWN';
  String status = 'One explicit first-load test per fresh process.', report = '';
  Widget? publisherBanner;
  Timer? loadDeadline;

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); }
  Future<void> event(String name, String result, {int? id, String category = 'NONE',
      String network = 'UNKNOWN', String state = 'UNKNOWN'}) async {
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
  Future<void> waterfall(MaxAdWaterfallInfo? value) async {
    if (value == null) return;
    for (final response in value.networkResponses.take(24)) {
      String state = 'UNKNOWN';
      if (response.adLoadState == AdLoadState.adLoaded) state = 'AD_LOADED';
      if (response.adLoadState == AdLoadState.adFailedToLoad) state = 'AD_FAILED_TO_LOAD';
      if (response.adLoadState == AdLoadState.adLoadNotAttempted) state = 'AD_LOAD_NOT_ATTEMPTED';
      await event('HOST_WATERFALL',
        response.mediatedNetwork.adapterClassName == adapter ? 'CLASS_MATCH' : 'CLASS_MISMATCH',
        network: network(response.mediatedNetwork.name), state: state,
        category: response.error == null ? 'NONE' : category(response.error!));
    }
  }
  Future<void> initialize() async {
    if (opening || initialized || blocked || !authorized || keyInput.text.trim().isEmpty) return;
    final sdkKey = keyInput.text.trim(); keyInput.clear();
    FocusScope.of(context).unfocus();
    setState(() { opening = true; status = 'Installed APK preflight and explicit SDK initialization...'; });
    try {
      await channel.invokeMapMethod<String, dynamic>('open', {'authorized': true})
          .timeout(const Duration(seconds: 15));
      if (scenario == 'SYNTHETIC') {
        try { await channel.invokeMethod<bool>('initializePrebid').timeout(const Duration(seconds: 10)); }
        catch (_) { await event('HOST_ERROR', 'FAILED', id: 0, category: 'PREPARATION'); }
      }
      AppLovinMAX.setVerboseLogging(false);
      final config = await AppLovinMAX.initialize(sdkKey).timeout(const Duration(seconds: 20));
      if (config == null || await AppLovinMAX.isInitialized().timeout(const Duration(seconds: 3)) != true) {
        throw StateError('MAX_NOT_READY');
      }
      await event('HOST_MAX_INITIALIZED', 'SUCCESS', id: 0);
      if (mounted) setState(() { initialized = true; status = 'Ready. No banner view or ad load has been created.'; });
    } on PlatformException catch (error) {
      blocked = true;
      if (mounted) setState(() => status = error.code == 'APK_CLASS_CHECK_FAILED'
          ? 'FAIL: installed base/splits must contain exactly one compatible adapter.'
          : 'Initialization unavailable: INCONCLUSIVE. Restart required.');
    } catch (_) {
      blocked = true;
      if (mounted) setState(() => status = 'Initialization failed: INCONCLUSIVE. Restart required.');
    } finally { if (mounted) setState(() => opening = false); }
  }
  Future<void> runTest() async {
    final expiry = int.tryParse(expiryInput.text);
    if (!initialized || started || blocked || !authorized || expiry == null || expiry < 1000 || expiry > 30000) return;
    setState(() { started = true; phase = 'PREPARING'; status = 'Preparing optional banner demand before mounting MAX view...'; });
    try {
      cycle = await channel.invokeMethod<int>('begin', {
        'authorized': authorized, 'scenario': scenario, 'eligibilityMs': expiry,
      }) ?? 0;
      if (cycle == 0) throw StateError('NO_CYCLE');
      if (mounted) setState(() {}); // Enable cancellation while native preparation is pending.
      String preparation = 'FAILED';
      try {
        preparation = await channel.invokeMethod<String>('prepare', {'cycle': cycle})
            .timeout(const Duration(seconds: 5)) ?? 'FAILED';
      } catch (_) {
        await event('HOST_ERROR', 'FAILED', category: 'PREPARATION');
        try { await channel.invokeMethod<void>('clearUnused', {'cycle': cycle}); }
        catch (_) { diagnosticsFailed = true; }
      }
      if (!mounted || blocked || removed) return;
      setState(() { phase = 'PREPARED'; status = 'Preparation: $preparation. MAX-only loading remains allowed.'; });
      if (!hold) await mountBanner();
    } catch (_) { await stop('Preparation state unresolved; restart required.'); }
  }
  Future<void> cancelPreparation() async {
    if (cycle == 0 || (phase != 'PREPARING' && phase != 'PREPARED')) return;
    try {
      await channel.invokeMethod<bool>('cancelPreparation', {'cycle': cycle});
      if (mounted) setState(() => status = 'Prebid contribution cancelled. Explicit MAX-only view creation remains allowed.');
    } catch (_) { diagnosticsFailed = true; }
  }
  Future<void> mountBanner() async {
    if (phase != 'PREPARED' || publisherBanner != null || blocked || removed) return;
    final available = MediaQuery.of(context).size.width - MediaQuery.of(context).padding.horizontal - 32;
    if (available < 320) {
      await stop('Viewport cannot fit the fixed 320dp banner. Restart in a sufficient window.'); return;
    }
    phase = 'ARMING'; // Prevent reentrant user actions while awaiting native authorization.
    try {
      if (await channel.invokeMethod<bool>('requestLoad', {'cycle': cycle}) != true) throw StateError('NOT_ARMED');
      await event('HOST_LOAD_REQUESTED', 'REQUESTED');
      await event('HOST_VIEW_MOUNT_REQUESTED', 'REQUESTED');
      if (!mounted || blocked || removed) return;
      // Official widget owns the MAX view and issues its one initial load on mount.
      // Keep this SAME widget instance across parent diagnostic rebuilds.
      final view = MaxAdView(
        key: ValueKey('b5-banner-$cycle'),
        adUnitId: unit,
        adFormat: AdFormat.banner,
        width: 320, height: 50,
        isAdaptiveBannerEnabled: false,
        isAutoRefreshEnabled: false,
        extraParameters: const {'force_banner': 'true'},
        listener: AdViewAdListener(
          onAdLoadedCallback: (ad) { onLoaded(ad); },
          onAdLoadFailedCallback: (id, error) { onFailed(id, error); },
          onAdClickedCallback: (ad) { if (ad.adUnitId == unit) event('HOST_CLICKED', 'SUCCESS'); },
          onAdExpandedCallback: (ad) { if (ad.adUnitId == unit) event('HOST_EXPANDED', 'SUCCESS'); },
          onAdCollapsedCallback: (ad) { if (ad.adUnitId == unit) event('HOST_COLLAPSED', 'SUCCESS'); },
          onAdRevenuePaidCallback: (ad) {
            if (ad.adUnitId != unit || removed) return;
            revenueCallbacks++;
            event('HOST_REVENUE', 'SUCCESS'); // No amounts, raw revenue metadata or currency retained.
            if (mounted) setState(() {});
          },
        ),
      );
      setState(() { publisherBanner = view; phase = 'LOADING'; status = 'Official MAX widget mounted: 320x50, force_banner=true, refresh OFF.'; });
      loadDeadline = Timer(const Duration(seconds: 31), () {
        if (!terminal && !removed) stop('MAX banner completion unresolved; dispose and restart.', timedOut: true);
      });
    } catch (_) { await stop('Banner mount/load authorization failed; restart required.'); }
  }
  Future<void> onLoaded(MaxAd ad) async {
    if (ad.adUnitId != unit || cycle == 0 || removed) return;
    if (terminal) { await event('HOST_LOAD_RESULT', 'LOADED'); diagnosticsFailed = true; return; }
    terminal = true; loadDeadline?.cancel();
    try {
      final accepted = await channel.invokeMethod<bool>('terminal', {'cycle': cycle, 'loaded': true});
      await event('HOST_LOAD_RESULT', 'LOADED');
      winner = network(ad.networkName);
      await event('HOST_SELECTED_NETWORK', 'LOADED', network: winner);
      await waterfall(ad.waterfall);
      if (accepted != true) { await stop('Late/unresolved loaded callback; restart required.'); return; }
      if (mounted) setState(() { loadedAd = true; phase = 'LOADED'; status = 'Genuine publisher LOADED: $winner. Inspect the actual banner, then dispose.'; });
    } catch (_) { await stop('Loaded callback bookkeeping unavailable; restart required.'); }
  }
  Future<void> onFailed(String id, MaxError error) async {
    if (id != unit || cycle == 0 || removed) return;
    if (terminal) { await event('HOST_LOAD_RESULT', 'FAILED', category: category(error)); diagnosticsFailed = true; return; }
    terminal = true; loadDeadline?.cancel();
    try {
      await channel.invokeMethod<bool>('terminal', {'cycle': cycle, 'loaded': false});
      await event('HOST_LOAD_RESULT', 'FAILED', category: category(error));
      await waterfall(error.waterfall);
      if (mounted) setState(() { phase = 'FAILED'; status = 'Genuine MAX load failure. Fill is not guaranteed. Dispose and export.'; });
    } catch (_) { diagnosticsFailed = true; }
  }
  Future<void> disposeBanner() async {
    if (publisherBanner == null || removing) return;
    removing = true; loadDeadline?.cancel();
    setState(() { publisherBanner = null; loadedAd = false; status = 'Removing official publisher banner widget...'; });
    await WidgetsBinding.instance.endOfFrame;
    try {
      await channel.invokeMethod<bool>('disposeContribution', {'cycle': cycle});
      // Flutter subtree removal is observed; this is NOT a forged native SDK destroy acknowledgement.
      await event('HOST_VIEW_REMOVED', 'SUCCESS');
    } catch (_) { diagnosticsFailed = true; }
    if (mounted) setState(() { removed = true; removing = false; phase = 'DISPOSED'; status = 'Widget removed. Confirm no observed disposal corruption; export. Fresh process required for next load.'; });
  }
  Future<void> visible(bool yes) async {
    await event('HOST_OPERATOR_VISIBLE', yes ? 'YES' : 'NO');
    if (mounted) setState(() => visibleReviewed = true);
  }
  Future<void> disposalReview(bool yes) async {
    await event('HOST_DISPOSAL_REVIEW', yes ? 'YES' : 'NO');
    if (mounted) setState(() => disposalReviewed = true);
  }
  Future<void> stop(String message, {bool timedOut = false}) async {
    blocked = true; loadDeadline?.cancel();
    try { await channel.invokeMethod<void>('invalidate'); } catch (_) { diagnosticsFailed = true; }
    if (cycle > 0) await event('HOST_ERROR', timedOut ? 'TIMEOUT' : 'FAILED',
        category: timedOut ? 'DEADLINE' : 'HOST_EXCEPTION');
    if (mounted) setState(() => status = message);
  }
  Future<void> exportReport() async {
    if (cycle == 0) return;
    try {
      final value = await channel.invokeMapMethod<String, dynamic>('export', {'cycle': cycle});
      if (value == null) return;
      final header = Map<String, dynamic>.from(value);
      final lines = header.remove('jsonl') as String? ?? '';
      header['hostDiagnosticsHealthy'] = !diagnosticsFailed;
      header['publisherRevenueCallbacks'] = revenueCallbacks;
      if (diagnosticsFailed) {
        header['nativeClassification'] = header['classification'];
        if (header['classification'] != 'FAIL') header['classification'] = 'INCONCLUSIVE';
      }
      final output = jsonEncode(header) + '\n' + lines;
      await Clipboard.setData(ClipboardData(text: output));
      if (mounted) setState(() => report = output);
    } catch (_) { if (mounted) setState(() => status = 'Export unavailable: INCONCLUSIVE.'); }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (initialized && started && !terminal && !removed && state == AppLifecycleState.paused) {
      stop('Backgrounded during preparation/loading; restart required.');
    }
  }
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this); loadDeadline?.cancel();
    if (initialized) channel.invokeMethod<void>('invalidate');
    keyInput.dispose(); expiryInput.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Moonetise B5 — Banner E2E')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('RESEARCH. One scenario/first load per fresh process. Installing replaces the Asterise sample. No automatic refresh or retries.'),
      TextField(controller: keyInput, enabled: !opening && !initialized && !blocked, obscureText: true,
        autocorrect: false, enableSuggestions: false,
        decoration: const InputDecoration(labelText: 'Development MAX SDK key — memory only')),
      DropdownButton<String>(value: scenario, items: [
        const DropdownMenuItem(value: 'NO_DEMAND', child: Text('A — NO PREBID DEMAND')),
        if (!kReleaseMode) const DropdownMenuItem(value: 'SYNTHETIC', child: Text('B — SYNTHETIC 320x50 PREBID BANNER')),
      ], onChanged: initialized || opening ? null : (value) => setState(() => scenario = value!)),
      CheckboxListTile(value: authorized,
        title: const Text('Publisher privacy/CMP and independent MAX authorization are satisfied. For B I authorize the approved synthetic PBS test. This does not set consent.'),
        onChanged: initialized || opening ? null : (value) => setState(() => authorized = value ?? false)),
      TextField(controller: expiryInput, enabled: !started, keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'Bid eligibility ms: 1000–30000; default 5000')),
      CheckboxListTile(value: hold, title: const Text('Pause after preparation for optional cancellation check'),
        onChanged: started ? null : (value) => setState(() => hold = value ?? false)),
      ElevatedButton(onPressed: !opening && !initialized && !blocked && authorized ? initialize : null, child: const Text('INITIALIZE')),
      ElevatedButton(onPressed: initialized && !started && !blocked ? runTest : null, child: const Text('RUN BANNER TEST')),
      TextButton(onPressed: cycle > 0 && (phase == 'PREPARING' || phase == 'PREPARED') ? cancelPreparation : null,
        child: const Text('CANCEL PREBID ONLY — MAX remains allowed')),
      if (hold) ElevatedButton(onPressed: phase == 'PREPARED' && !blocked ? mountBanner : null,
        child: const Text('CREATE OFFICIAL MAX BANNER')),
      const Text('Publisher-owned official MAX banner slot: 320x50'),
      Center(child: SizedBox(width: 320, height: 50, child: publisherBanner)),
      Text(status),
      Text('Genuine revenue callbacks observed: $revenueCallbacks (amounts not retained).'),
      if (loadedAd && !visibleReviewed) ...[
        TextButton(onPressed: () => visible(true), child: Text(winner == 'TESTPBS'
            ? 'Operator: expected synthetic 320x50 creative visible in this MAX slot'
            : 'Operator: ordinary MAX banner visible')),
        TextButton(onPressed: () => visible(false), child: const Text('Operator: expected banner NOT visible / wrong dimensions')),
      ],
      ElevatedButton(onPressed: publisherBanner != null && !removing && (terminal || blocked) ? disposeBanner : null,
        child: const Text('DISPOSE PUBLISHER BANNER')),
      if (removed && !disposalReviewed) ...[
        TextButton(onPressed: () => disposalReview(true), child: const Text('Operator: banner removed; no observed disposal corruption')),
        TextButton(onPressed: () => disposalReview(false), child: const Text('Operator: disposal corruption observed')),
      ],
      TextButton(onPressed: cycle > 0 ? exportReport : null, child: const Text('COPY REDACTED REPORT')),
      if (report.isNotEmpty) SelectableText(report),
    ])),
  );
}
