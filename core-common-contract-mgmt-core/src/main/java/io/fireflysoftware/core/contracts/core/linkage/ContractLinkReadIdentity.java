/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import java.util.UUID;
public record ContractLinkReadIdentity(UUID actionId,UUID applicationId,UUID partyId){
    public ContractLinkReadIdentity{for(UUID id:new UUID[]{actionId,applicationId,partyId})if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact original contract link read identity required");}
}
