/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage.impl;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkRequest;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender;
import org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

public final class ContractLinkAuditRootImpl {
    public record Replay<T>(T value,long sequence,String recordHash){
        public Replay{Objects.requireNonNull(value);if(sequence<1||recordHash==null||!recordHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact retained audit record required");}
    }
    public record Plan<T>(UUID contractId,UUID eventId,Instant occurredAt,String policyRevision,String effectDigest,Optional<Replay<T>> replay,BiFunction<UUID,AuditRecord,Mono<T>> writes){
        public Plan{Objects.requireNonNull(contractId);Objects.requireNonNull(eventId);Objects.requireNonNull(occurredAt);Objects.requireNonNull(replay);
            if(contractId.equals(new UUID(0,0))||eventId.equals(new UUID(0,0))||policyRevision==null||policyRevision.isBlank()||effectDigest==null||!effectDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact contract link evidence required");
            if(replay.isEmpty())Objects.requireNonNull(writes);else if(writes!=null)throw new IllegalArgumentException("Replay cannot carry business writes");}
    }
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate template;
    private final BoundedRootTransaction root;
    private final Function<UUID,TransactionalAuditAppender> append;
    private final CompletedAuditAppendService completedAppend;
    private final ContractLinkAdmission authority;
    private final Clock clock;
    private final LongSupplier nanos;
    public ContractLinkAuditRootImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,
            R2dbcTransactionManager manager,CleanupPolicy cleanup,ContractLinkAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.template=template;this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        this.root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));
        this.append=nonce->new SpringDataTransactionalAuditAppender(this.resources,client,template,manager,authority,clock,nanos,()->nonce);
        this.completedAppend=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);
    }
    public R2dbcEntityTemplate template(){return template;}
    public Clock clock(){return clock;}
    public <T> Mono<T> execute(ContractLinkRequest intent,Function<ContractLinkAdmission.Permit,Mono<Plan<T>>> prepare){return Mono.defer(()->{
        var deliberate=new java.util.concurrent.atomic.AtomicReference<org.fireflyframework.web.error.exceptions.BusinessException>();
        var appendAttempted=new java.util.concurrent.atomic.AtomicBoolean();
        long observedNanos=nanos.getAsLong();Instant observedAt=clock.instant();
        var initial=R2dbcOperationBudget.observed(observedAt,observedAt.plusSeconds(5),observedNanos,Duration.ofSeconds(5),clock,nanos);
        return authority.requireCurrent(intent).switchIfEmpty(Mono.error(unavailable())).timeout(initial.remaining())
            .onErrorMap(error->preDispatchFailure(intent.actionId(),error)).flatMap(permit->{
            if(!permit.intent().equals(ContractLinkIntent.of(intent,permit.resourceTenantId(),permit.partyId(),permit.roleInContractId())))return Mono.error(new ContractLinkFailure(intent.actionId(),ContractLinkFailure.Outcome.REFUSED));
            final R2dbcOperationBudget budget;
            try{budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos).intersect(initial);}catch(RuntimeException expired){return Mono.error(new ContractLinkFailure(intent.actionId(),ContractLinkFailure.Outcome.REFUSED));}
            var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope)
                .then(authority.revalidate(permit)).then(Mono.defer(()->prepare.apply(permit))).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(plan->{budget.remaining();scope.remaining();
                    var event=new AuditEvent(plan.eventId(),new AuditStream(permit.resourceTenantId(),"core-contract-management","contract",plan.contractId()),
                        permit.actor(),"contracts.loan.initial-primary.link",AuditEvent.Outcome.COMPLETED,permit.actionId(),permit.actionId(),null,
                        plan.occurredAt(),plan.effectDigest(),plan.policyRevision());
                    if(plan.occurredAt().isAfter(clock.instant())||plan.replay().isEmpty()&&!permit.policyRevision().equals(plan.policyRevision()))return Mono.error(unavailable());
                    UUID appendNonce=UUID.randomUUID();
                    return authority.revalidate(permit).then(Mono.defer(()->{appendAttempted.set(true);return authority.withinExpectedAppend(permit,event,()->append.apply(appendNonce).append(scope,event));}))
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(provisional->{
                            if(provisional.replay()!=plan.replay().isPresent() || provisional.record()==null)return Mono.error(unavailable());
                            return completedAppend.requireCompleted(scope,appendNonce,provisional).switchIfEmpty(Mono.error(unavailable())).flatMap(record->{
                                if(!event.equals(record.event()) || !record.equals(AuditRecordHasher.record(
                                    new AuditCheckpoint(event.stream(),record.sequence()-1,record.previousHash()),event,record.recordedAt())))return Mono.error(unavailable());
                                if(plan.replay().isPresent()){
                                    var expected=plan.replay().orElseThrow();
                                    if(record.sequence()!=expected.sequence()||!record.recordHash().equals(expected.recordHash()))return Mono.error(unavailable());
                                    return authority.revalidate(permit).thenReturn(expected.value());
                                }
                                return resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit))
                                    .then(Mono.defer(()->plan.writes().apply(appendNonce,record))).switchIfEmpty(Mono.error(unavailable()))
                                    .flatMap(value->authority.revalidate(permit).thenReturn(value));
                            });
                        });
                }).timeout(scope.remaining()).onErrorResume(error->{
                    if(!appendAttempted.get() && error instanceof org.fireflyframework.web.error.exceptions.BusinessException business
                        && (business.getStatus()==org.springframework.http.HttpStatus.BAD_REQUEST || business.getStatus()==org.springframework.http.HttpStatus.CONFLICT)) deliberate.compareAndSet(null,business);
                    return resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error));}))
                .map(completed->{var facts=completed.transactionFacts();
                    if(!facts.nativeAttempted() || !facts.commitAcknowledged() || !facts.boundaryCompleted() || facts.rollbackAcknowledged() || completed.cleanupFact()!=CleanupFact.COMPLETE)
                        throw new ContractLinkFailure(intent.actionId(),facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?ContractLinkFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:ContractLinkFailure.Outcome.OUTCOME_UNKNOWN);
                    return completed.value();
                }).onErrorMap(RootTransactionException.class,error->{var facts=error.transactionFacts();
                    if(deliberate.get()!=null && facts.rollbackAcknowledged() && !facts.commitAcknowledged() && error.cleanupFact()==CleanupFact.COMPLETE)return deliberate.get();
                    return new ContractLinkFailure(intent.actionId(),facts.commitAcknowledged()&&!facts.rollbackAcknowledged()?ContractLinkFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:
                        facts.rollbackAcknowledged()&&!facts.commitAcknowledged()&&error.cleanupFact()==CleanupFact.COMPLETE?ContractLinkFailure.Outcome.REFUSED:ContractLinkFailure.Outcome.OUTCOME_UNKNOWN);
                }).flatMap(value->Mono.defer(()->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;})).timeout(budget.remaining()))
                    .onErrorMap(error->new ContractLinkFailure(intent.actionId(),ContractLinkFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE)));
        });});}
    private static Throwable preDispatchFailure(UUID action,Throwable error){
        if(error instanceof org.springframework.web.server.ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;
        return new ContractLinkFailure(action,ContractLinkFailure.Outcome.REFUSED);
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact paired contract link audit and completed owned root required");}
}
