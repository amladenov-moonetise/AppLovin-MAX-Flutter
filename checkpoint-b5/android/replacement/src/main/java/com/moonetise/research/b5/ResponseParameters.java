package com.moonetise.research.b5;

import android.os.Bundle;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.adapters.PrebidMaxMediationAdapter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** MAX 13.3.1 public interface; only the copied local-extra map changes. */
public final class ResponseParameters implements MaxAdapterResponseParameters {
    private final MaxAdapterResponseParameters original;
    private final Map<String, Object> extras;
    public ResponseParameters(MaxAdapterResponseParameters original, String id) {
        this.original = original;
        Map<String, Object> copy = new HashMap<>();
        if (original.getLocalExtraParameters() != null) copy.putAll(original.getLocalExtraParameters());
        copy.put(PrebidMaxMediationAdapter.EXTRA_RESPONSE_ID, id);
        extras = Collections.unmodifiableMap(copy);
    }
    public String getAdUnitId() { return original.getAdUnitId(); }
    public Map<String, Object> getLocalExtraParameters() { return extras; }
    public Bundle getServerParameters() { return original.getServerParameters(); }
    public Bundle getCustomParameters() { return original.getCustomParameters(); }
    public Boolean hasUserConsent() { return original.hasUserConsent(); }
    public Boolean isAgeRestrictedUser() { return original.isAgeRestrictedUser(); }
    public Boolean isDoNotSell() { return original.isDoNotSell(); }
    public String getConsentString() { return original.getConsentString(); }
    public boolean isTesting() { return original.isTesting(); }
    public String getThirdPartyAdPlacementId() { return original.getThirdPartyAdPlacementId(); }
    public String getBidResponse() { return original.getBidResponse(); }
    public long getBidExpirationMillis() { return original.getBidExpirationMillis(); }
}
