/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;
/** Observation alone grants no audit append or contract mutation authority. */
public interface ContractLinkReadAdmission {
    record Permit(ContractLinkReadIdentity identity,UUID resourceTenantId,UUID roleInContractId,AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,Instant observedAt,long observedNanos,Instant notAfter){
        public Permit{
            Objects.requireNonNull(identity);Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);
            for(UUID id:new UUID[]{resourceTenantId,roleInContractId,bindingId})if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact current contract observation binding required");
            if(actor.staffRealm()!=null||actor.staffId()!=null||bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                ||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Finite current HUMAN observation authority required");
        }
        @Override public String toString(){return "ContractLinkReadPermit[redacted]";}
    }
    Mono<Optional<io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkReceipt>> invoke(UUID application,UUID action,java.util.function.Function<ContractLinkReadIdentity,Mono<Optional<io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkReceipt>>> reader);
    Mono<Permit> requireCurrent(ContractLinkReadIdentity identity);
    Mono<Void> revalidate(Permit original);
}
