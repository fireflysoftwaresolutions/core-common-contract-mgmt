/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkRequest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;
/** Trusted current caller/role/dataset/policy bridge. A request or constructed permit alone grants nothing. */
public interface ContractLinkAdmission extends AuditAppendAuthority {
    record Permit(ContractLinkIntent intent,AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,
            String policyRevision,Instant observedAt,long observedNanos,Instant notAfter){
        public Permit{
            Objects.requireNonNull(intent);Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);
            if(bindingId==null||bindingId.equals(new UUID(0,0))||bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")
                ||actor.staffRealm()!=null||actor.staffId()!=null||actor.platformTenantId().length()>512
                ||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                ||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)
                throw new IllegalArgumentException("Exact finite current contract link authority required");
        }
        public UUID actionId(){return intent.actionId();}
        public UUID applicationId(){return intent.applicationId();}
        public UUID partyId(){return intent.partyId();}
        public UUID roleInContractId(){return intent.roleInContractId();}
        public UUID resourceTenantId(){return intent.resourceTenantId();}
        public String intentDigest(){return intent.digest();}
        @Override public String toString(){return "ContractLinkPermit[redacted]";}
    }
    Mono<io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkReceipt> invoke(ContractLinkRequest request,ContractLinkStore owner);
    Mono<Permit> requireCurrent(ContractLinkRequest request);
    Mono<Void> revalidate(Permit permit);
    <T> Mono<T> withinExpectedAppend(Permit permit,AuditEvent event,Supplier<Mono<T>> append);
}
