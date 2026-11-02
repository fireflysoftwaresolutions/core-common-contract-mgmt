/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage.impl;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkPurpose;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import io.fireflysoftware.core.contracts.models.entities.linkage.*;
import io.fireflysoftware.core.contracts.models.repositories.linkage.*;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import reactor.core.publisher.Mono;
/** Write-free exact graph verification shared by replay and current observation. */
final class ContractLinkGraphVerifierImpl {
    private final ContractLinkOwnedContractRepository contracts;private final ContractLinkPartyRepository parties;
    private final ContractLinkLineRepository lines;private final ContractLinkHistoryRepository histories;
    ContractLinkGraphVerifierImpl(R2dbcEntityTemplate template){var factory=new R2dbcRepositoryFactory(template);
        contracts=factory.getRepository(ContractLinkOwnedContractRepository.class);parties=factory.getRepository(ContractLinkPartyRepository.class);
        lines=factory.getRepository(ContractLinkLineRepository.class);histories=factory.getRepository(ContractLinkHistoryRepository.class);}
    Mono<ContractLinkAction> verify(ContractLinkAction row,AuditActor actor,UUID tenant,UUID role,UUID binding,long version,String bindingDigest){return Mono.defer(()->{
        if(!ContractLinkPurpose.INITIAL_PRIMARY_LOAN_CONTRACT.name().equals(row.purpose())||!tenant.equals(row.resourceTenantId())||!role.equals(row.roleInContractId())||!actor.issuer().equals(row.actorIssuer())||!actor.subject().equals(row.actorSubject())||!actor.clientId().equals(row.actorClientId())||!actor.platformTenantId().equals(row.actorPlatformTenantId())
            ||!binding.equals(row.bindingId())||version!=row.bindingVersion()||!bindingDigest.equals(row.bindingDigest())||!valid(row.contractId())||!valid(row.contractPartyId())||!valid(row.contractLineId())||!valid(row.statusHistoryId())||!valid(row.auditContextNonce())||!valid(row.auditEventId())||row.auditSequence()<1||!digest(row.auditRecordHash())||!digest(row.effectDigest())||row.linkedAt()==null||row.policyRevision()==null)return Mono.error(unavailable());
        var historical=new ContractLinkIntent(row.actionId(),row.applicationId(),row.resourceTenantId(),row.partyId(),row.roleInContractId(),row.requestedContractId(),row.productId(),row.currencyCode());
        if(!historical.digest().equals(row.intentDigest()))return Mono.error(unavailable());
        ContractStatusEnum status;try{status=ContractStatusEnum.valueOf(row.contractStatusAtLink());}catch(RuntimeException invalid){return Mono.error(unavailable());}
        var initial=ContractLinkInitialEvidence.retained(row);var contract=initial.contractRow();var party=initial.partyRow();var line=initial.lineRow();var history=initial.historyRow();
        if(row.contractCreated()!=historical.createsContract()||row.contractPartyCreated()!=row.contractCreated()||!row.contractId().equals(historical.createsContract()?row.contractId():historical.existingContractId())
            ||!row.effectDigest().equals(ContractLinkEffect.digest(historical,actor,row.bindingId(),row.bindingVersion(),row.bindingDigest(),row.policyRevision(),row.contractId(),row.contractPartyId(),row.contractLineId(),row.statusHistoryId(),row.contractCreated(),row.contractPartyCreated(),status,row.linkedAt(),initial.digest())))return Mono.error(unavailable());
        if(!row.contractId().equals(contract.contractId())||contract.contractStatus()!=status||!row.productId().equals(contract.productId())||!row.currencyCode().equals(contract.currencyCode())
            ||!row.contractPartyId().equals(party.contractPartyId())||!row.contractId().equals(party.contractId())||!row.partyId().equals(party.partyId())||!row.roleInContractId().equals(party.roleInContractId())||!party.active()||party.dateLeft()!=null
            ||!row.contractLineId().equals(line.contractLineId())||!row.contractId().equals(line.contractId())||!row.productId().equals(line.productId())||!line.primary()
            ||!row.statusHistoryId().equals(history.contractStatusHistoryId())||!row.contractId().equals(history.contractId())||history.statusCode()!=historyCode(status)||history.startAt()==null||history.endAt()!=null)return Mono.error(unavailable());
        if(row.contractCreated()){
            var expected=LocalDateTime.ofInstant(row.linkedAt(),ZoneOffset.UTC);
            if(status!=ContractStatusEnum.DRAFT||contract.contractNumber()!=null||contract.startDate()!=null||contract.endDate()!=null||contract.productCatalogId()!=null||contract.predecessorContractId()!=null||contract.linkKind()!=null||!expected.equals(contract.createdAt())||!expected.equals(contract.updatedAt())
                ||line.lineRole()!=null||line.quantity()!=null||line.effectiveFrom()!=null||line.effectiveTo()!=null||!expected.equals(line.createdAt())||!expected.equals(line.updatedAt())
                ||!expected.equals(history.startAt())||!expected.equals(history.createdAt())||!expected.equals(history.updatedAt())||!expected.equals(party.dateJoined())||!expected.equals(party.createdAt())||!expected.equals(party.updatedAt()))return Mono.error(unavailable());
        }
        return contracts.findByContractId(row.contractId()).switchIfEmpty(Mono.error(unavailable())).flatMap(current->parties.findAllByContractIdAndPartyIdAndRoleInContractIdAndActiveTrue(row.contractId(),row.partyId(),row.roleInContractId()).take(2).collectList().map(owned->{
            if(!row.contractId().equals(current.contractId())||current.contractStatus()==null||owned.size()!=1)throw unavailable();var actual=owned.getFirst();
            if(!row.contractId().equals(actual.contractId())||!row.partyId().equals(actual.partyId())||!row.roleInContractId().equals(actual.roleInContractId())||!actual.active()||actual.dateLeft()!=null)throw unavailable();return row;
        }));
    });}
    Mono<CurrentImpl> current(UUID id,ContractLinkIntent intent){return contracts.findByContractId(id).switchIfEmpty(Mono.error(unavailable())).flatMap(contract->{
        if(!id.equals(contract.contractId())||!intent.productId().equals(contract.productId())||!intent.currencyCode().equals(contract.currencyCode())||!Set.of(ContractStatusEnum.DRAFT,ContractStatusEnum.ACTIVE).contains(contract.contractStatus()))return Mono.error(unavailable());
        return lines.findAllByContractIdAndPrimaryTrue(id).take(2).collectList().flatMap(primary->{
            if(primary.size()!=1)return Mono.error(unavailable());var line=primary.getFirst();if(!id.equals(line.contractId())||!intent.productId().equals(line.productId())||!line.primary())return Mono.error(unavailable());
            return histories.findAllByContractIdAndEndAtIsNull(id).take(2).collectList().map(open->{if(open.size()!=1)throw unavailable();var history=open.getFirst();
                if(!id.equals(history.contractId())||history.statusCode()!=historyCode(contract.contractStatus())||history.endAt()!=null||history.startAt()==null)throw unavailable();return new CurrentImpl(contract,line,history);});
        });
    });}
    private static StatusCodeEnum historyCode(ContractStatusEnum status){return switch(status){case DRAFT->StatusCodeEnum.CREATED;case ACTIVE->StatusCodeEnum.ACTIVATED;default->throw unavailable();};}
    record CurrentImpl(ContractLinkContractMetadata contract,ContractLinkLineMetadata line,ContractLinkHistoryMetadata history){}
    private static boolean valid(UUID id){return id!=null&&!id.equals(new UUID(0,0));}private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Complete retained initial primary contract graph required");}
}
