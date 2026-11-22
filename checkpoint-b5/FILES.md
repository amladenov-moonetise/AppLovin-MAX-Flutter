# B5 exact added-file inventory

All paths are repository-relative. Only checkpoint-b5 is added. No completed fixture is edited.

- checkpoint-b5/.gitignore
- checkpoint-b5/FILES.md
- checkpoint-b5/LICENSE-Prebid-Apache-2.0.txt
- checkpoint-b5/README.md
- checkpoint-b5/REPORT_FORMAT.md
- checkpoint-b5/SOURCE_PROVENANCE.md
- checkpoint-b5/THIRD_PARTY_NOTICES.md
- checkpoint-b5/android/app/build.gradle
- checkpoint-b5/android/app/src/debug/java/com/moonetise/research/checkpointb5/ResearchPreparation.java
- checkpoint-b5/android/app/src/main/AndroidManifest.xml
- checkpoint-b5/android/app/src/main/java/com/moonetise/research/checkpointb5/InstalledApkCheck.java
- checkpoint-b5/android/app/src/main/java/com/moonetise/research/checkpointb5/MainActivity.java
- checkpoint-b5/android/app/src/release/java/com/moonetise/research/checkpointb5/ResearchPreparation.java
- checkpoint-b5/android/build.gradle
- checkpoint-b5/android/gradle.properties
- checkpoint-b5/android/replacement/build.gradle
- checkpoint-b5/android/replacement/consumer-rules.pro
- checkpoint-b5/android/replacement/src/main/AndroidManifest.xml
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/PrebidMaxMediationAdapter.java
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ListenersCreator.java
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/ParametersChecker.java
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxBannerManager.java
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxInterstitialManager.java
- checkpoint-b5/android/replacement/src/main/java/com/applovin/mediation/adapters/prebid/managers/MaxRewardedManager.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/B5Trace.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/BannerDemandBridge.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/BannerScope.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/CycleGate.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/DemandBridge.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/PendingSlot.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/ResponseParameters.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/RewardObservation.java
- checkpoint-b5/android/replacement/src/main/java/com/moonetise/research/b5/RewardedDemandBridge.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/BannerScopeTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/CycleGateTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/FormatScopeTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/FullscreenB4RegressionTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/InterstitialSourceRegressionTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/PendingSlotTest.java
- checkpoint-b5/android/replacement/src/test/java/com/moonetise/research/b5/RewardObservationTest.java
- checkpoint-b5/android/settings.gradle
- checkpoint-b5/lib/main.dart
- checkpoint-b5/pubspec.lock
- checkpoint-b5/pubspec.yaml

The adapter gains only the banner path and banner-manager destruction. BannerDemandBridge/
BannerScope own optional banner eligibility; MaxBannerManager retains original SDK rendering.
MainActivity, debug/release ResearchPreparation, trace and Flutter UI are research scaffolding.
Existing fullscreen managers/providers and tests are retained after package/trace renaming.
Initial and final committed inventory: 44 files; no documentation-file count increase.
Seven JVM classes have 26 passing tests in inspected operator-generated XML reports.
Codex did not rerun builds/tests. README.md records final operator-reviewed B5 findings,
unchanged automatic INCONCLUSIVE results, exact run IDs and accepted limitations.
SOURCE_PROVENANCE.md identifies pinned originals; source/dependency files were not changed for closure.
