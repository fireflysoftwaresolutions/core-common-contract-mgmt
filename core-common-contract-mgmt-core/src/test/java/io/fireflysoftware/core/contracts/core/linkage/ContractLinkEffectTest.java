/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContractLinkEffectTest {
    final UUID action=UUID.randomUUID(),application=UUID.randomUUID(),tenant=UUID.randomUUID(),party=UUID.randomUUID(),role=UUID.randomUUID(),product=UUID.randomUUID(),binding=UUID.randomUUID();
    final UUID contract=UUID.randomUUID(),contractParty=UUID.randomUUID(),line=UUID.randomUUID(),history=UUID.randomUUID();
    final Instant now=Instant.parse("2026-11-02T12:00:00.123456Z");
    final ContractLinkIntent intent=new ContractLinkIntent(action,application,tenant,party,role,null,product,"EUR");
    final AuditActor actor=new AuditActor("https://issuer.invalid","opaque:human/é","domain-lending","opaque-platform",null,null);
    String effect(UUID c,UUID p,UUID l,UUID h,boolean created,boolean linked,ContractStatusEnum status,Instant at){return effect(c,p,l,h,created,linked,status,at,"c".repeat(64));}
    String effect(UUID c,UUID p,UUID l,UUID h,boolean created,boolean linked,ContractStatusEnum status,Instant at,String initialDigest){return ContractLinkEffect.digest(intent,actor,binding,1,"a".repeat(64),"policy-1",c,p,l,h,created,linked,status,at,initialDigest);}
    @Test void sameCompletePersistedGraphHasStableEffect(){assertThat(effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64))).matches("[a-f0-9]{64}").isEqualTo(effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64)));}
    @Test void eachPersistedIdentityCreationFlagStatusAndInstantBindsTheEffect(){String original=effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64));
        for(String changed:List.of(effect(UUID.randomUUID(),contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now),effect(contract,UUID.randomUUID(),line,history,true,true,ContractStatusEnum.DRAFT,now),effect(contract,contractParty,UUID.randomUUID(),history,true,true,ContractStatusEnum.DRAFT,now),effect(contract,contractParty,line,UUID.randomUUID(),true,true,ContractStatusEnum.DRAFT,now),effect(contract,contractParty,line,history,false,true,ContractStatusEnum.DRAFT,now),effect(contract,contractParty,line,history,true,false,ContractStatusEnum.DRAFT,now),effect(contract,contractParty,line,history,true,true,ContractStatusEnum.ACTIVE,now),effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now.plusNanos(1000))))assertThat(changed).isNotEqualTo(original);
    }
    @Test void completeInitialRowEvidenceDigestBindsTheEffect(){assertThat(effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64))).isNotEqualTo(effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"d".repeat(64)));}
    @Test void historicalActorBindingAndPolicyAreNotReplacedByLaterObservation(){String original=effect(contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64));
        for(String changed:List.of(
            ContractLinkEffect.digest(intent,new AuditActor(actor.issuer(),"another-opaque-human",actor.clientId(),actor.platformTenantId(),null,null),binding,1,"a".repeat(64),"policy-1",contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64)),
            ContractLinkEffect.digest(intent,actor,UUID.randomUUID(),1,"a".repeat(64),"policy-1",contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64)),
            ContractLinkEffect.digest(intent,actor,binding,2,"a".repeat(64),"policy-1",contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64)),
            ContractLinkEffect.digest(intent,actor,binding,1,"b".repeat(64),"policy-1",contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64)),
            ContractLinkEffect.digest(intent,actor,binding,1,"a".repeat(64),"policy-2",contract,contractParty,line,history,true,true,ContractStatusEnum.DRAFT,now,"c".repeat(64))))assertThat(changed).isNotEqualTo(original);
    }
}
