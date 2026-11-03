/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage;
import java.time.Instant;
import java.util.UUID;
/** Explicit current owner dataset and contract-role binding; no request-derived or sample role. */
public record ContractLinkSettings(String issuer,String audience,String allowedClientId,String platformTenant,UUID resourceTenantId,
        UUID roleInContractId,UUID bindingId,long bindingVersion,String bindingDigest,Instant notAfter){
    public ContractLinkSettings{
        for(String value:new String[]{issuer,audience,allowedClientId,platformTenant})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit contract link deployment binding required");
        for(UUID id:new UUID[]{resourceTenantId,roleInContractId,bindingId})if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact current dataset and server-bound role required");
        if(bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||notAfter==null)throw new IllegalArgumentException("Versioned current contract link binding required");
    }
    public void requireCurrent(Instant now){if(!now.isBefore(notAfter))throw new IllegalStateException("Current contract link binding required");}
    @Override public String toString(){return "ContractLinkSettings[redacted]";}
}
