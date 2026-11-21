import 'dart:async';
import 'package:applovin_max/applovin_max.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MaterialApp(home: InvocationScreen()));

class InvocationScreen extends StatefulWidget {
  const InvocationScreen({Key? key}) : super(key: key);
  @override
  State<InvocationScreen> createState() => _InvocationScreenState();
}

class _InvocationScreenState extends State<InvocationScreen> {
  static const _diagnostics = MethodChannel('moonetise/adapter_b1_research');
  static const _adapter =
      'com.applovin.mediation.adapters.PrebidMaxMediationAdapter';
  final _key = TextEditingController();
  final _adUnit = TextEditingController();
  bool _authorized = false;
  bool _used = false;
  bool _closed = false;
  bool _terminalLoad = false;
  bool _diagnosticsFailed = false;
  String _format = 'INTERSTITIAL';
  String _unit = '';
  String _status = 'Not run. No MAX initialization or loading occurs on launch.';
  String _report = '';
  Timer? _window;

  Future<void> _event(String event, String result,
      {String category = 'NONE',
      String network = 'UNKNOWN',
      String state = 'UNKNOWN'}) async {
    try {
      await _diagnostics.invokeMethod<void>('event', <String, String>{
        'event': event, 'result': result, 'category': category,
        'network': network, 'state': state,
      });
    } catch (_) {
      _diagnosticsFailed = true;
      // Never stringify platform exceptions, MAX errors, configuration or credentials.
      if (mounted) setState(() => _status = 'Diagnostic channel failed: INCONCLUSIVE.');
    }
  }

  String _network(String name) {
    if (name == 'TestPBS' || name == 'Custom Network (SDK) - TestPBS') return 'TESTPBS';
    if (name == 'AppLovin' || name == 'AppLovin Exchange' || name == 'ALX') return 'APPLOVIN';
    return 'OTHER';
  }

  String _category(MaxError error) {
    if (error.code == ErrorCode.invalidAdUnitId) return 'CREDENTIAL_OR_CONFIGURATION';
    if (error.code == ErrorCode.networkError || error.code == ErrorCode.networkTimeout) {
      return 'CONNECTIVITY';
    }
    if (error.code == ErrorCode.noFill) return 'NO_FILL';
    return 'SDK_OR_NETWORK_ERROR';
  }

  Future<void> _waterfall(MaxAdWaterfallInfo? waterfall) async {
    if (waterfall == null) return;
    // Bound diagnostic volume. Never read/export credentials, request data or messages.
    for (final response in waterfall.networkResponses.take(24)) {
      final network = _network(response.mediatedNetwork.name);
      final match = response.mediatedNetwork.adapterClassName == _adapter;
      String state = 'UNKNOWN';
      if (response.adLoadState == AdLoadState.adLoadNotAttempted) state = 'AD_LOAD_NOT_ATTEMPTED';
      if (response.adLoadState == AdLoadState.adLoaded) state = 'AD_LOADED';
      if (response.adLoadState == AdLoadState.adFailedToLoad) state = 'AD_FAILED_TO_LOAD';
      await _event('HOST_WATERFALL', match ? 'CLASS_MATCH' : 'CLASS_MISMATCH',
          network: network, state: state,
          category: response.error == null ? 'NONE' : _category(response.error!));
    }
  }

  void _loaded(MaxAd ad) {
    if (_closed || _terminalLoad || ad.adUnitId != _unit) return;
    _terminalLoad = true;
    _event('HOST_LOAD_RESULT', 'LOADED');
    _event('HOST_SELECTED_NETWORK', 'LOADED', network: _network(ad.networkName));
    _waterfall(ad.waterfall);
    if (mounted) setState(() => _status =
        'MAX loaded an ad. It will NOT be shown. Keep observing until the 60-second window closes.');
  }

  void _failed(String unit, MaxError error) {
    if (_closed || _terminalLoad || unit != _unit) return;
    _terminalLoad = true;
    _event('HOST_LOAD_RESULT', 'FAILED', category: _category(error));
    _waterfall(error.waterfall);
    if (mounted) setState(() => _status =
        'MAX reported load failure. Keep observing until the 60-second window closes.');
  }

  void _unexpected(String category) {
    if (!_closed) _event('HOST_UNEXPECTED_CALLBACK', 'FAILED', category: category);
  }

  void _installListeners() {
    AppLovinMAX.setInterstitialListener(InterstitialListener(
      onAdLoadedCallback: _loaded,
      onAdLoadFailedCallback: _failed,
      onAdDisplayedCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdDisplayFailedCallback: (_, __) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdClickedCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdHiddenCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdRevenuePaidCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
    ));
    AppLovinMAX.setRewardedAdListener(RewardedAdListener(
      onAdLoadedCallback: _loaded,
      onAdLoadFailedCallback: _failed,
      onAdDisplayedCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdDisplayFailedCallback: (_, __) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdClickedCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdHiddenCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdRevenuePaidCallback: (_) => _unexpected('UNEXPECTED_DISPLAY'),
      onAdReceivedRewardCallback: (_, __) => _unexpected('UNEXPECTED_REWARD'),
    ));
  }

  Future<void> _run() async {
    if (_used || !_authorized || _key.text.trim().isEmpty || _adUnit.text.trim().isEmpty) return;
    final sdkKey = _key.text.trim();
    _unit = _adUnit.text.trim();
    _key.clear();
    FocusScope.of(context).unfocus();
    setState(() { _used = true; _status = 'Checking the installed APK before MAX initialization...'; });
    try {
      // Native begin rejects multiple definitions and a second run in this process.
      await _diagnostics.invokeMethod<String>('begin').timeout(const Duration(seconds: 15));
      if (!mounted) return;
      _window = Timer(const Duration(seconds: 60), () async {
        _closed = true;
        try {
          await _diagnostics.invokeMethod<void>('close');
        } catch (_) {
          _diagnosticsFailed = true;
        }
        if (mounted) setState(() => _status = _diagnosticsFailed
            ? 'Observation closed: diagnostic failure, INCONCLUSIVE.'
            : 'Observation closed. Copy the redacted report and apply the B1 acceptance criteria.');
      });
      _installListeners();
      AppLovinMAX.setVerboseLogging(false);
      setState(() => _status = 'Initializing official MAX. No Prebid initialization is performed.');
      final configuration = await AppLovinMAX.initialize(sdkKey)
          .timeout(const Duration(seconds: 20));
      final initialized = await AppLovinMAX.isInitialized()
          .timeout(const Duration(seconds: 3));
      if (_closed || !mounted) return;
      if (configuration == null || initialized != true) {
        await _event('HOST_MAX_INITIALIZED', 'FAILED', category: 'SDK_OR_NETWORK_ERROR');
        setState(() => _status = 'MAX initialization did not succeed: INCONCLUSIVE, not a class-loading diagnosis.');
        return;
      }
      await _event('HOST_MAX_INITIALIZED', 'SUCCESS');
      if (_closed || !mounted || _diagnosticsFailed) return;
      await _event('HOST_LOAD_REQUESTED', 'REQUESTED');
      if (_closed || !mounted || _diagnosticsFailed) return;
      setState(() => _status = 'One publisher-controlled MAX load requested. No show or retry will be called by this screen.');
      if (_format == 'INTERSTITIAL') {
        AppLovinMAX.loadInterstitial(_unit);
      } else {
        AppLovinMAX.loadRewardedAd(_unit);
      }
    } on TimeoutException {
      await _event('HOST_ERROR', 'TIMEOUT', category: 'DEADLINE');
      if (mounted) setState(() => _status =
          'Initialization/channel timeout: INCONCLUSIVE. No retry. Force-stop before another run.');
    } on PlatformException catch (error) {
      // Only inspect fixed channel error codes, never messages or details.
      await _event('HOST_ERROR', 'FAILED', category: 'HOST_EXCEPTION');
      if (mounted) setState(() => _status = error.code == 'APK_CLASS_CHECK_FAILED'
          ? 'FAIL: installed APK does not contain exactly one adapter. MAX was not initialized.'
          : 'Preflight/channel could not proceed: INCONCLUSIVE. Use a fresh process; verify APK and diagnostics.');
    } catch (_) {
      await _event('HOST_ERROR', 'FAILED', category: 'HOST_EXCEPTION');
      if (mounted) setState(() => _status =
          'Run could not proceed: check APK preflight, credentials, configuration and connectivity separately. No retry.');
    }
  }

  Future<void> _copyReport() async {
    try {
      final report = await _diagnostics.invokeMethod<String>('export') ?? '';
      await Clipboard.setData(ClipboardData(text: report));
      if (mounted) setState(() => _report = report.isEmpty ? 'No run recorded.' : report);
    } catch (_) {
      if (mounted) setState(() => _status = 'Report export failed: INCONCLUSIVE.');
    }
  }

  @override
  void dispose() {
    _window?.cancel();
    AppLovinMAX.setInterstitialListener(null);
    AppLovinMAX.setRewardedAdListener(null);
    _key.dispose();
    _adUnit.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Moonetise Adapter B1 — research stub')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('One run per fresh process. No PBS, rendering, show, rewards, or automatic publisher retries.'),
      TextField(controller: _key, enabled: !_used, obscureText: true,
        autocorrect: false, enableSuggestions: false,
        decoration: const InputDecoration(labelText: 'Development MAX SDK key (memory only)')),
      TextField(controller: _adUnit, enabled: !_used, autocorrect: false,
        enableSuggestions: false,
        decoration: const InputDecoration(labelText: 'Existing approved MAX ad-unit ID (not a placement ID)')),
      DropdownButton<String>(value: _format,
        items: const [
          DropdownMenuItem(value: 'INTERSTITIAL', child: Text('Interstitial')),
          DropdownMenuItem(value: 'REWARDED', child: Text('Rewarded — load only')),
        ],
        onChanged: _used ? null : (value) => setState(() => _format = value!)),
      CheckboxListTile(value: _authorized,
        title: const Text('Publisher privacy prerequisites and this development account/ad unit are already authorized. This checkbox does not set consent.'),
        onChanged: _used ? null : (value) => setState(() => _authorized = value ?? false)),
      ElevatedButton(onPressed: _used || !_authorized ? null : _run,
        child: const Text('Run Adapter Invocation Test')),
      const SizedBox(height: 12),
      Text(_status),
      TextButton(onPressed: _copyReport, child: const Text('Copy redacted diagnostics')),
      if (_report.isNotEmpty) SelectableText(_report),
    ])),
  );
}
