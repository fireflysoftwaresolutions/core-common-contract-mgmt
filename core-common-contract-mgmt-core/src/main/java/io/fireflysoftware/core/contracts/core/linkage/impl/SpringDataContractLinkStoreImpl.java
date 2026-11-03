/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage.impl;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import io.fireflysoftware.core.contracts.models.entities.linkage.*;
import io.fireflysoftware.core.contracts.models.repositories.linkage.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.*;
/** Actual contract/party/line/history, action receipt and canonical pair share one bounded owning transaction. */
public final class SpringDataContractLinkStoreImpl implements ContractLinkStore {
    private final ContractLinkAuditRootImpl owner;private final R2dbcEntityTemplate template;
    private final ContractLinkActionRepository actions;private final ContractLinkGraphVerifierImpl verifier;
    private final ContractLinkPartyRepository parties;
    public SpringDataContractLinkStoreImpl(ContractLinkAuditRootImpl owner){
        this.owner=Objects.requireNonNull(owner);template=owner.template();var factory=new R2dbcRepositoryFactory(template);
        actions=factory.getRepository(ContractLinkActionRepository.class);verifier=new ContractLinkGraphVerifierImpl(template);
        parties=factory.getRepository(ContractLinkPartyRepository.class);
    }
    @Override public Mono<ContractLinkReceipt> link(ContractLinkRequest request){
        try{validRequest(request);}catch(RuntimeException invalid){return Mono.error(invalid);}
        return owner.execute(request,permit->actions.lockInitialPrimaryApplication(permit.resourceTenantId(),permit.applicationId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable()))
            .then(actions.lockAction(permit.resourceTenantId(),permit.actionId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable())))
            .flatMap(locked->retained(permit)).flatMap(retained->{
                if(retained.isPresent())return verified(permit,retained.orElseThrow()).map(row->new ContractLinkAuditRootImpl.Plan<ContractLinkReceipt>(row.contractId(),row.auditEventId(),row.linkedAt(),row.policyRevision(),row.effectDigest(),Optional.of(new ContractLinkAuditRootImpl.Replay<>(receipt(row,true),row.auditSequence(),row.auditRecordHash())),null));
                return initialApplicationAvailable(permit).then(Mono.defer(()->{
                    Instant linkedAt=owner.clock().instant().truncatedTo(ChronoUnit.MICROS);
                    return graph(permit.intent(),linkedAt).map(graph->{
                        var initial=ContractLinkInitialEvidence.capture(graph.contract,graph.party,graph.line,graph.history);
                        String effect=ContractLinkEffect.digest(permit.intent(),permit,graph.contract.contractId(),graph.party.contractPartyId(),graph.line.contractLineId(),graph.history.contractStatusHistoryId(),graph.contractCreated,graph.partyCreated,graph.contract.contractStatus(),linkedAt,initial.digest());
                        return new ContractLinkAuditRootImpl.Plan<ContractLinkReceipt>(graph.contract.contractId(),UUID.randomUUID(),linkedAt,permit.policyRevision(),effect,Optional.empty(),(nonce,record)->{
                            var action=action(permit,graph,initial,linkedAt,effect,nonce,record);
                            Mono<Void> writes=graph.contractCreated?insert(graph.contract).then(insert(graph.line)).then(insert(graph.history)):Mono.empty();
                            if(graph.partyCreated)writes=writes.then(insert(graph.party));
                            return writes.then(insert(action)).then(retained(permit)).flatMap(found->found.isEmpty()?Mono.error(unavailable()):verified(permit,found.orElseThrow()).map(actual->{if(!action.equals(actual))throw unavailable();return receipt(actual,false);}));
                        });
                    });
                }));
            }));
    }
    private Mono<Void> initialApplicationAvailable(ContractLinkAdmission.Permit permit){return actions.findAllByResourceTenantIdAndApplicationIdAndPurpose(permit.resourceTenantId(),permit.applicationId(),ContractLinkPurpose.INITIAL_PRIMARY_LOAN_CONTRACT.name()).take(2).collectList().flatMap(rows->{
        if(rows.size()>1)return Mono.error(unavailable());
        if(!rows.isEmpty())return Mono.error(ContractLinkConflict.initialPrimaryApplication(permit.actionId()));
        return Mono.empty();
    });}
    private Mono<GraphImpl> graph(ContractLinkIntent intent,Instant at){
        var display=LocalDateTime.ofInstant(at,ZoneOffset.UTC);
        if(intent.createsContract()){
            UUID contract=UUID.randomUUID();var metadata=new ContractLinkContractMetadata(contract,null,ContractStatusEnum.DRAFT,null,null,null,intent.productId(),intent.currencyCode(),null,null,display,display);
            var party=new ContractLinkPartyMetadata(UUID.randomUUID(),contract,intent.partyId(),intent.roleInContractId(),display,null,true,display,display);
            var line=new ContractLinkLineMetadata(UUID.randomUUID(),contract,intent.productId(),null,null,null,null,true,display,display);
            var history=new ContractLinkHistoryMetadata(UUID.randomUUID(),contract,StatusCodeEnum.CREATED,display,null,display,display);
            return Mono.just(new GraphImpl(metadata,party,line,history,true,true));
        }
        return verifier.current(intent.existingContractId(),intent).flatMap(existing->parties.findAllByContractIdAndPartyIdAndRoleInContractIdAndActiveTrue(intent.existingContractId(),intent.partyId(),intent.roleInContractId()).take(2).collectList().map(found->{
            if(found.size()!=1)throw unavailable();var party=found.getFirst();
            if(!intent.existingContractId().equals(party.contractId())||!intent.partyId().equals(party.partyId())||!intent.roleInContractId().equals(party.roleInContractId())||!party.active()||party.dateLeft()!=null)throw unavailable();
            return new GraphImpl(existing.contract(),party,existing.line(),existing.history(),false,false);
        }));
    }
    private Mono<Optional<ContractLinkAction>> retained(ContractLinkAdmission.Permit permit){return actions.findAllByResourceTenantIdAndActionId(permit.resourceTenantId(),permit.actionId()).take(2).collectList().map(rows->{
        if(rows.size()>1||rows.stream().anyMatch(row->!permit.actionId().equals(row.actionId())||!permit.resourceTenantId().equals(row.resourceTenantId())))throw unavailable();return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());});}
    private Mono<ContractLinkAction> verified(ContractLinkAdmission.Permit permit,ContractLinkAction row){
        if(!permit.actionId().equals(row.actionId()))return Mono.error(unavailable());
        return verifier.verify(row,permit.actor(),permit.resourceTenantId(),permit.roleInContractId(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest()).flatMap(actual->
            permit.intent().digest().equals(actual.intentDigest())?Mono.just(actual):Mono.error(new ContractLinkConflict(permit.actionId())));
    }
    private static ContractLinkAction action(ContractLinkAdmission.Permit permit,GraphImpl graph,ContractLinkInitialEvidence initial,Instant at,String effect,UUID nonce,AuditRecord record){var i=permit.intent();return new ContractLinkAction(i.actionId(),i.resourceTenantId(),i.purpose().name(),i.applicationId(),i.partyId(),i.roleInContractId(),i.existingContractId(),graph.contract.contractId(),graph.party.contractPartyId(),graph.line.contractLineId(),graph.history.contractStatusHistoryId(),graph.partyCreated,i.productId(),i.currencyCode(),graph.contractCreated,graph.contract.contractStatus().name(),i.digest(),effect,initial.contract(),initial.party(),initial.line(),initial.history(),initial.digest(),permit.actor().issuer(),permit.actor().subject(),permit.actor().clientId(),permit.actor().platformTenantId(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest(),permit.policyRevision(),at,nonce,record.event().eventId(),record.sequence(),record.recordHash());}
    static ContractLinkReceipt receipt(ContractLinkAction row,boolean replayed){return new ContractLinkReceipt(row.actionId(),ContractLinkPurpose.valueOf(row.purpose()),row.applicationId(),row.resourceTenantId(),row.partyId(),row.roleInContractId(),row.contractId(),row.contractPartyId(),row.productId(),row.currencyCode(),row.contractCreated(),ContractStatusEnum.valueOf(row.contractStatusAtLink()),row.intentDigest(),row.linkedAt(),replayed);}
    private <T> Mono<Void> insert(T row){return template.insert(row).switchIfEmpty(Mono.error(unavailable())).then();}
    private record GraphImpl(ContractLinkContractMetadata contract,ContractLinkPartyMetadata party,ContractLinkLineMetadata line,ContractLinkHistoryMetadata history,boolean contractCreated,boolean partyCreated){}
    private static void validRequest(ContractLinkRequest request){if(request==null||!valid(request.actionId())||!valid(request.applicationId())||!valid(request.productId())||request.existingContractId()!=null&&!valid(request.existingContractId())||request.currencyCode()==null||!request.currencyCode().matches("[A-Z]{3}"))throw new BusinessException(HttpStatus.BAD_REQUEST,"CONTRACT_LINK_INPUT_INVALID","Exact original contract action/application/product/currency required");}
    private static boolean valid(UUID id){return id!=null&&!id.equals(new UUID(0,0));}private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Complete current immutable contract link graph required");}
}
