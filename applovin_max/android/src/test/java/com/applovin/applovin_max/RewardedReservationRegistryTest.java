package com.applovin.applovin_max;

/** Standalone focused runner: no Android runtime, SDK initialization or network. */
public final class RewardedReservationRegistryTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Rewarded ownership check " + checks);
    }
    private static final class Fixture {
        long now;
        final String id = "rewarded-unit";
        final Object object = new Object();
        final RewardedReservationRegistry.IdentityDomain identities = new RewardedReservationRegistry.IdentityDomain();
        final RewardedReservationRegistry registry = new RewardedReservationRegistry(() -> now, identities);
        final RewardedReservationRegistry.Binding binding;
        final RewardedReservationRegistry.Attempt attempt;
        Fixture() {
            registry.attach(); registry.created(id, object);
            binding = registry.binding(id, object);
            attempt = registry.reserve(id, object, false);
        }
        void show() {
            registry.beforeLoad(id, object, attempt.id);
            check(registry.acceptCallback(binding, RewardedReservationRegistry.Event.LOADED));
            registry.beforeShow(id, object, attempt.id, true);
        }
        void hiddenAndRevenue() {
            check(registry.acceptCallback(binding, RewardedReservationRegistry.Event.HIDDEN));
            check(registry.acceptCallback(binding, RewardedReservationRegistry.Event.REVENUE));
        }
        boolean reward() { return registry.acceptCallback(binding, RewardedReservationRegistry.Event.REWARD); }
    }
    private static void readinessDuringPreparationDoesNotConsumeReservation() {
        Fixture f = new Fixture();
        check(!f.registry.canReportReady(f.id, f.object, false));
        check(f.registry.isCurrent(f.id, f.object, f.attempt.id, f.binding.generation, f.binding.epoch));
        f.registry.validateLoad(f.id, f.object, f.attempt.id);
    }
    private static void lateRewardSurvivesHidden() {
        Fixture f = new Fixture(); f.show(); f.hiddenAndRevenue();
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
        check(f.reward()); check(!f.reward());
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
        f.registry.cleanupFinished(f.binding, true);
    }
    private static void rewardBeforeHiddenStillNeedsRevenue() {
        Fixture f = new Fixture(); f.show(); check(f.reward());
        check(f.registry.acceptCallback(f.binding, RewardedReservationRegistry.Event.HIDDEN));
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
        check(f.registry.acceptCallback(f.binding, RewardedReservationRegistry.Event.REVENUE));
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
    }
    private static void invalidationNeverAbandonsLateReward() {
        Fixture f = new Fixture(); f.show(); f.hiddenAndRevenue();
        f.registry.requestInvalidation(f.id, f.attempt.id);
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
        check(f.reward());
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
    }
    private static void explicitRetirementDuringShowPreservesSink() {
        Fixture f = new Fixture(); f.show();
        f.registry.requestRetirement(f.id, f.attempt.id);
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
        f.hiddenAndRevenue();
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
        check(f.reward());
    }
    private static void explicitDisposalAfterHiddenMayAbandonSink() {
        Fixture f = new Fixture(); f.show(); f.hiddenAndRevenue();
        f.registry.requestRetirement(f.id, f.attempt.id);
        check(!f.reward());
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
        f.registry.cleanupFinished(f.binding, true);
        check(!f.reward());
    }
    private static void detachDoesNotReplayRewardIntoNewEngine() {
        Fixture f = new Fixture(); f.show(); f.hiddenAndRevenue();
        f.registry.detach();
        check(f.reward());
        check(!f.registry.canForwardToEngine(f.binding));
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
    }
    private static void displayFailureCannotEarnReward() {
        Fixture f = new Fixture(); f.show();
        check(f.registry.acceptCallback(f.binding, RewardedReservationRegistry.Event.DISPLAY_FAILED));
        check(!f.reward());
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
    }
    private static void loadAndDisplayDoNotEarnRewards() {
        Fixture f = new Fixture();
        check(!f.reward());
        f.registry.beforeLoad(f.id, f.object, f.attempt.id);
        check(f.registry.acceptCallback(f.binding, RewardedReservationRegistry.Event.LOADED));
        check(!f.reward());
        f.registry.beforeShow(f.id, f.object, f.attempt.id, true);
        check(f.registry.acceptCallback(f.binding, RewardedReservationRegistry.Event.DISPLAYED));
        f.hiddenAndRevenue();
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.DEFERRED);
    }
    private static void cleanupFailureRetainsProcessGuard() {
        Fixture f = new Fixture();
        f.registry.requestRetirement(f.id, f.attempt.id);
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
        f.registry.cleanupFinished(f.binding, false);
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.FAILED);
        RewardedReservationRegistry other = new RewardedReservationRegistry(() -> 0, f.identities);
        other.attach();
        check(other.isCoordinated(f.id)); // Checked before another SDK getInstance/listener write.
    }
    private static void expiredReservationStillAllowsCleanup() {
        Fixture f = new Fixture(); f.now = 5_000;
        check(!f.registry.isCurrent(f.id, f.object, f.attempt.id, f.binding.generation, f.binding.epoch));
        f.registry.requestRetirement(f.id, f.attempt.id);
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
    }
    private static void oldRewardCannotAffectReplacement() {
        Fixture f = new Fixture(); f.show(); f.hiddenAndRevenue(); check(f.reward());
        check(f.registry.beginCleanup(f.binding) == RewardedReservationRegistry.Cleanup.READY);
        f.registry.cleanupFinished(f.binding, true);
        Object replacement = new Object();
        f.registry.created(f.id, replacement);
        RewardedReservationRegistry.Attempt next = f.registry.reserve(f.id, replacement, false);
        check(!f.reward());
        check(!f.registry.isCurrent(f.id, replacement, f.attempt.id, f.binding.generation, f.binding.epoch));
        f.registry.beforeLoad(f.id, replacement, next.id);
        RewardedReservationRegistry.Binding binding = f.registry.binding(f.id, replacement);
        check(f.registry.acceptCallback(binding, RewardedReservationRegistry.Event.LOADED));
        f.registry.beforeShow(f.id, replacement, next.id, true);
        check(f.registry.acceptCallback(binding, RewardedReservationRegistry.Event.REWARD));
    }
    public static void main(String[] args) {
        readinessDuringPreparationDoesNotConsumeReservation();
        lateRewardSurvivesHidden(); rewardBeforeHiddenStillNeedsRevenue();
        invalidationNeverAbandonsLateReward(); explicitRetirementDuringShowPreservesSink();
        explicitDisposalAfterHiddenMayAbandonSink(); detachDoesNotReplayRewardIntoNewEngine();
        displayFailureCannotEarnReward(); loadAndDisplayDoNotEarnRewards();
        cleanupFailureRetainsProcessGuard(); expiredReservationStillAllowsCleanup();
        oldRewardCannotAffectReplacement();
        System.out.println("Rewarded ownership checks passed: " + checks);
    }
}
