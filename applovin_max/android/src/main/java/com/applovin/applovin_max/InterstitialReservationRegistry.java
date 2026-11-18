package com.applovin.applovin_max;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Plugin-owned lifecycle/retirement metadata only. All SDK cleanup stays outside registry locks. */
final class InterstitialReservationRegistry
{
    interface Clock { long now(); }
    enum State { IDLE, LOADING, LOADED, SHOWING, TERMINAL, UNKNOWN, RETIRED }
    enum Cleanup { READY, DEFERRED, RETIRED, FAILED }
    enum Event { LOADED, LOAD_FAILED, DISPLAYED, DISPLAY_FAILED, HIDDEN, CLICKED, REVENUE }

    static final long RESERVATION_TIMEOUT_MS = 5_000;

    /** Process identity metadata only; never holds SDK objects or listeners. */
    static final class IdentityDomain
    {
        final AtomicLong nextEpoch = new AtomicLong();
        private long nextGeneration;
        private final Map<String, Long> generations = new HashMap<>();
        private final Set<String> coordinatedIds = new HashSet<>();
        private final Set<String> safeReplacements = new HashSet<>();

        synchronized boolean consumeSafeReplacement(String id) { return safeReplacements.remove(id); }

        synchronized void cleaned(String id, long generation)
        {
            if (generation(id) != generation) return; // Never release a newer generation's guard.
            coordinatedIds.remove(id);
            safeReplacements.add(id);
        }

        synchronized boolean wasCoordinated(String id) { return coordinatedIds.contains(id); }
        synchronized void coordinate(String id) { coordinatedIds.add(id); }

        synchronized long generation(String id)
        {
            Long value = generations.get(id);
            return value == null ? 0 : value;
        }

        synchronized long created(String id)
        {
            long generation = ++nextGeneration;
            generations.put(id, generation);
            return generation;
        }
    }

    private static final IdentityDomain PROCESS_IDENTITIES = new IdentityDomain();

    static final class Rejected extends IllegalStateException
    {
        final String code;
        Rejected(String code) { super(code); this.code = code; }
    }

    static final class Attempt
    {
        final String id = UUID.randomUUID().toString();
        final long generation;
        final long epoch;
        final long createdAtMs;
        final long deadlineMs;

        Attempt(long generation, long epoch, long now)
        {
            this.generation = generation;
            this.epoch = epoch;
            createdAtMs = now;
            deadlineMs = now + RESERVATION_TIMEOUT_MS;
        }
    }

    /** Captured by the plugin's listener at object creation, never reconstructed from ad unit ID. */
    static final class Binding
    {
        final String adUnitId;
        Object object; // Cleared after confirmed local cleanup; retain only a scalar retirement receipt.
        final long generation;
        final long epoch;
        private State state;
        private Attempt attempt;
        private boolean coordinated;
        private boolean invalidated;
        private boolean showDispatched;
        private boolean loadDispatched;
        private boolean retirementRequested;
        private boolean cleaning;
        private boolean cleaned;
        private boolean cleanupFailed;
        private boolean loadedReported;
        private boolean loadFailureReported;
        private boolean displayedReported;
        private boolean displayFailureReported;
        private boolean hiddenReported;
        private boolean revenueReported;

        Binding(String id, Object object, long generation, long epoch, State state)
        {
            adUnitId = id;
            this.object = object;
            this.generation = generation;
            this.epoch = epoch;
            this.state = state;
        }
    }

    private final Map<String, Binding> entries = new HashMap<>();
    private final Clock clock;
    private final IdentityDomain identities;
    private long epoch;
    private boolean attached;

    InterstitialReservationRegistry(Clock clock) { this(clock, PROCESS_IDENTITIES); }

    InterstitialReservationRegistry(Clock clock, IdentityDomain identities)
    {
        this.clock = clock;
        this.identities = identities;
    }

    synchronized void attach()
    {
        if (attached) return;
        epoch = identities.nextEpoch.incrementAndGet();
        attached = true;
        for (Binding entry : entries.values()) invalidate(entry);
    }

    synchronized boolean isAttached() { return attached; }

    synchronized void hostUnavailable()
    {
        for (Binding entry : entries.values()) invalidate(entry);
    }

    synchronized void detach()
    {
        attached = false;
        hostUnavailable();
        // No destruction: a dispatched presentation can still produce legitimate callbacks.
    }

    synchronized long created(String id, Object object)
    {
        Binding previous = entries.get(id);
        if (previous != null && previous.object == object) return previous.generation;
        if (previous != null) invalidate(previous);
        final long generation;
        final boolean firstObject;
        synchronized (identities)
        {
            firstObject = identities.generation(id) == 0 || identities.consumeSafeReplacement(id);
            generation = identities.created(id);
        }
        Binding entry = new Binding(id, object, generation, epoch,
                attached && firstObject ? State.IDLE : State.UNKNOWN);
        entry.coordinated = identities.wasCoordinated(id);
        entries.put(id, entry);
        return generation;
    }

    synchronized Binding binding(String id, Object object)
    {
        Binding entry = entries.get(id);
        if (entry == null || entry.object != object) throw new Rejected("RESERVATION_UNSAFE");
        return entry;
    }

    synchronized Attempt reserve(String id, Object object, boolean nativeReady)
    {
        Binding entry = binding(id, object);
        requireIdentity(entry);
        entry.coordinated |= identities.wasCoordinated(id);
        if (nativeReady && (!entry.coordinated || entry.state == State.IDLE))
        {
            if (entry.coordinated) invalidate(entry);
            else entry.state = State.LOADED;
            throw new Rejected("INTERSTITIAL_BUSY");
        }
        if (entry.coordinated)
        {
            requireAttempt(entry, entry.attempt == null ? null : entry.attempt.id);
            throw new Rejected("ALREADY_RESERVED");
        }
        if (entry.state != State.IDLE)
            throw new Rejected(entry.state == State.UNKNOWN || entry.state == State.TERMINAL
                    ? "RESERVATION_UNSAFE" : "INTERSTITIAL_BUSY");
        identities.coordinate(id);
        entry.coordinated = true; // Sticky across expiry, failure, hidden, generations and engines.
        entry.attempt = new Attempt(entry.generation, epoch, clock.now());
        return entry.attempt;
    }

    synchronized boolean isCurrent(String id, Object object, String attemptId,
                                   long generation, long expectedEpoch)
    {
        Binding entry = entries.get(id);
        if (entry == null || entry.object != object || entry.generation != generation
                || entry.epoch != expectedEpoch) return false;
        try { requireAttempt(entry, attemptId); return true; }
        catch (Rejected ignored) { return false; }
    }

    synchronized boolean isCoordinated(String id)
    {
        Binding entry = entries.get(id);
        return identities.wasCoordinated(id) || (entry != null && entry.coordinated && !entry.cleaned);
    }

    synchronized void validateLoad(String id, Object object, String attemptId)
    {
        Binding entry = binding(id, object);
        entry.coordinated |= identities.wasCoordinated(id);
        if (entry.coordinated)
        {
            requireAttempt(entry, attemptId);
            if (entry.state != State.IDLE) throw new Rejected("INTERSTITIAL_BUSY");
        }
        else if (attemptId != null) throw new Rejected("STALE_ATTEMPT");
    }

    synchronized void beforeLoad(String id, Object object, String attemptId)
    {
        validateLoad(id, object, attemptId); // Recheck after native readiness queries/reentrancy.
        Binding entry = binding(id, object);
        if (!entry.coordinated)
        {
            // Ordinary dispatch retains upstream behavior, even if its state is uncertain.
            entry.invalidated = false;
            entry.showDispatched = false;
        }
        entry.state = entry.state == State.IDLE || entry.state == State.TERMINAL
                ? State.LOADING : State.UNKNOWN;
        entry.loadDispatched = true;
        // Consume ONLY the pre-load deadline. This is not a bid-freshness authorization.
    }

    synchronized void beforeShow(String id, Object object, String attemptId, boolean nativeReady)
    {
        Binding entry = binding(id, object);
        entry.coordinated |= identities.wasCoordinated(id);
        if (entry.coordinated)
        {
            requireAttempt(entry, attemptId);
            if (entry.state != State.LOADED || entry.showDispatched)
                throw new Rejected("INTERSTITIAL_NOT_READY");
            if (!nativeReady)
            {
                invalidate(entry);
                throw new Rejected("INTERSTITIAL_NOT_READY");
            }
        }
        else if (attemptId != null) throw new Rejected("STALE_ATTEMPT");
        entry.state = entry.state == State.LOADED ? State.SHOWING : State.UNKNOWN;
        entry.showDispatched = true;
    }

    synchronized boolean canReportReady(String id, Object object, boolean nativeReady)
    {
        Binding entry = binding(id, object);
        entry.coordinated |= identities.wasCoordinated(id);
        if (!entry.coordinated) return nativeReady;
        try
        {
            requireAttempt(entry, entry.attempt == null ? null : entry.attempt.id);
            return nativeReady && entry.state == State.LOADED && !entry.showDispatched;
        }
        catch (Rejected ignored) { return false; }
    }

    synchronized void markUnsafe(Binding entry)
    {
        invalidate(entry); // Captured generation only, including after replacement.
    }

    synchronized void markUnsafe(String id)
    {
        Binding entry = entries.get(id);
        if (entry != null) invalidate(entry);
    }

    /**
     * Changes ONLY the captured object binding. Obsolete non-presentation events cannot touch
     * a replacement's state. Presentation receipts remain valid after policy/host invalidation.
     */
    synchronized boolean acceptCallback(Binding entry, Event event)
    {
        boolean presentationReceipt = entry.showDispatched
                && (event == Event.DISPLAYED || event == Event.DISPLAY_FAILED
                    || event == Event.HIDDEN || event == Event.CLICKED || event == Event.REVENUE);
        if (!presentationReceipt && !(entry.coordinated ? currentObject(entry) : currentOwnerBinding(entry)))
            return false;
        if (entry.coordinated && !presentationReceipt)
        {
            try { requireAttempt(entry, entry.attempt == null ? null : entry.attempt.id); }
            catch (Rejected ignored) { return false; }
        }
        if (!entry.coordinated)
        {
            // No new deduplication/order restrictions for ordinary publisher callbacks.
            if (event == Event.LOADED)
                entry.state = entry.state == State.LOADING ? State.LOADED : State.UNKNOWN;
            else if (event == Event.DISPLAYED)
                entry.state = entry.showDispatched ? State.SHOWING : State.UNKNOWN;
            else if ((event == Event.LOAD_FAILED || event == Event.DISPLAY_FAILED || event == Event.HIDDEN)
                    && entry.state != State.UNKNOWN)
                entry.state = State.TERMINAL;
            return true;
        }
        switch (event)
        {
            case LOADED:
                if (entry.loadedReported || entry.loadFailureReported) return false;
                if (entry.state != State.LOADING) { invalidate(entry); return false; }
                entry.loadedReported = true;
                entry.state = State.LOADED;
                return true;
            case LOAD_FAILED:
                if (entry.loadFailureReported) return false;
                if (entry.state != State.LOADING || entry.loadedReported) { invalidate(entry); return false; }
                entry.loadFailureReported = true;
                entry.state = State.TERMINAL;
                invalidate(entry);
                return true;
            case DISPLAYED:
                if (!entry.showDispatched || entry.displayedReported || entry.hiddenReported
                        || entry.displayFailureReported) return false;
                entry.displayedReported = true;
                entry.state = State.SHOWING;
                return true;
            case DISPLAY_FAILED:
                if (!entry.showDispatched || entry.displayFailureReported || entry.displayedReported
                        || entry.hiddenReported) return false;
                entry.displayFailureReported = true;
                entry.state = State.TERMINAL;
                invalidate(entry);
                return true;
            case HIDDEN:
                if (!entry.showDispatched || entry.hiddenReported || entry.displayFailureReported) return false;
                entry.hiddenReported = true;
                entry.state = State.TERMINAL;
                invalidate(entry);
                return true;
            case REVENUE:
                if (!entry.showDispatched || entry.revenueReported || entry.displayFailureReported) return false;
                entry.revenueReported = true;
                return true; // May legitimately follow HIDDEN.
            case CLICKED:
                return entry.showDispatched && !entry.displayFailureReported;
            default:
                return false;
        }
    }

    /** Expiry/invalidated attempts may still request cleanup; never require load/show permission here. */
    synchronized Binding requestRetirement(String id, String attemptId)
    {
        Binding entry = entries.get(id);
        if (entry == null || attemptId == null || entry.attempt == null
                || !entry.attempt.id.equals(attemptId))
            throw new Rejected("STALE_ATTEMPT");
        requireIdentity(entry);
        if (!entry.coordinated) throw new Rejected("STALE_ATTEMPT");
        invalidate(entry); // Before any SDK calls, including synchronous/reentrant cleanup.
        return entry;
    }

    synchronized java.util.List<Binding> pendingCleanup()
    {
        java.util.List<Binding> pending = new java.util.ArrayList<>();
        for (Binding entry : entries.values())
            if (entry.retirementRequested && !entry.cleaned && !entry.cleanupFailed) pending.add(entry);
        return pending;
    }

    synchronized boolean retirementPending(Binding entry)
    {
        return entry.retirementRequested && !entry.cleaned && !entry.cleanupFailed;
    }

    synchronized Cleanup beginCleanup(Binding entry)
    {
        if (entry.cleaned) return Cleanup.RETIRED;
        if (entry.cleanupFailed) return Cleanup.FAILED;
        if (!entry.retirementRequested || entry.cleaning) return Cleanup.DEFERRED;
        // Revenue can legitimately arrive after HIDDEN. No timer may invent its completion.
        if (entry.showDispatched && !entry.displayFailureReported
                && !(entry.hiddenReported && entry.revenueReported)) return Cleanup.DEFERRED;
        entry.cleaning = true;
        return Cleanup.READY;
    }

    synchronized void cleanupFinished(Binding entry, boolean success)
    {
        if (!entry.cleaning) throw new IllegalStateException("Cleanup was not claimed");
        entry.cleaning = false;
        if (!success)
        {
            entry.cleanupFailed = true;
            return; // No SDK retry; uncertain cleanup keeps the ID quarantined.
        }
        entry.cleaned = true;
        entry.state = State.RETIRED;
        entry.object = null;
        identities.cleaned(entry.adUnitId, entry.generation);
    }

    synchronized boolean canForwardToEngine(Binding entry)
    {
        return attached && entry.epoch == epoch;
    }

    private boolean currentOwnerBinding(Binding entry)
    {
        return attached && entry.epoch == epoch && entries.get(entry.adUnitId) == entry;
    }

    private boolean currentObject(Binding entry)
    {
        return currentOwnerBinding(entry) && identities.generation(entry.adUnitId) == entry.generation;
    }

    private void requireIdentity(Binding entry)
    {
        if (!attached) throw new Rejected("ENGINE_DETACHED");
        if (!currentObject(entry)) throw new Rejected("STALE_OBJECT");
    }

    private void requireAttempt(Binding entry, String attemptId)
    {
        requireIdentity(entry);
        if (attemptId == null) throw new Rejected("ATTEMPT_REQUIRED");
        if (entry.attempt == null || !entry.attempt.id.equals(attemptId))
            throw new Rejected("STALE_ATTEMPT");
        if (!entry.loadDispatched && clock.now() >= entry.attempt.deadlineMs)
        {
            invalidate(entry);
            throw new Rejected("ATTEMPT_EXPIRED");
        }
        if (entry.invalidated || entry.cleaned || entry.cleanupFailed)
            throw new Rejected("ATTEMPT_INVALIDATED");
    }

    private void invalidate(Binding entry)
    {
        entry.invalidated = true;
        if (entry.coordinated && !entry.cleaned) entry.retirementRequested = true;
        if (!entry.showDispatched && !entry.cleaned) entry.state = State.UNKNOWN;
        // Preserve authorization denial until successful exact-object cleanup.
    }
}
