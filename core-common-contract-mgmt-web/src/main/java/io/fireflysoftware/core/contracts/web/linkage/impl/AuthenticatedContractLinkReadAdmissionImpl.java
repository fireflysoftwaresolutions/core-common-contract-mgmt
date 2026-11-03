/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage.impl;

import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import io.fireflysoftware.core.contracts.web.linkage.ContractLinkSettings;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Separate current read purpose. It cannot supply audit append authority or caller-controlled party/custody grants. */
public final class AuthenticatedContractLinkReadAdmissionImpl implements ContractLinkReadAdmission {
    public static final String ACTION="contracts:loan:initial-primary-link:read";
    private final ContractLinkSettings settings;private final PolicyEvaluationPort policies;private final Clock clock;private final LongSupplier nanos;private final Object executionKey=new Object();
    public AuthenticatedContractLinkReadAdmissionImpl(ContractLinkSettings settings,PolicyEvaluationPort policies,Clock clock,LongSupplier nanos){this.settings=Objects.requireNonNull(settings);this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);}
    @Override public Mono<Optional<ContractLinkReceipt>> invoke(UUID application,UUID action,Function<ContractLinkReadIdentity,Mono<Optional<ContractLinkReceipt>>> reader){return Mono.deferContextual(context->{
        if(context.hasKey(executionKey)||!valid(application)||!valid(action))return Mono.error(denied());Instant observed=clock.instant();long before=nanos.getAsLong();
        return authentication().timeout(Duration.ofSeconds(5)).flatMap(authentication->{
            var principal=principal(authentication);var identity=new ContractLinkReadIdentity(action,application,UUID.fromString((String)authentication.getToken().getClaims().get("party_id")));
            Instant until=Collections.min(List.of(observed.plusSeconds(5),authentication.getToken().getExpiresAt(),settings.notAfter()));var execution=new ExecutionImpl(identity,authentication,principal,observed,before,until);
            return Mono.using(()->execution,held->Mono.defer(()->reader.apply(identity)).switchIfEmpty(Mono.error(unavailable())).contextWrite(current->current.put(executionKey,held)),held->held.active.set(false),true)
                .map(value->{if(value.isPresent()){var receipt=value.orElseThrow();if(receipt.purpose()!=ContractLinkPurpose.INITIAL_PRIMARY_LOAN_CONTRACT||!execution.identity.actionId().equals(receipt.actionId())||!execution.identity.applicationId().equals(receipt.applicationId())||!execution.identity.partyId().equals(receipt.partyId())||!settings.resourceTenantId().equals(receipt.resourceTenantId())||!settings.roleInContractId().equals(receipt.roleInContractId()))throw new ContractLinkReadFailure(action);}try{window(execution);}catch(RuntimeException expired){throw new ContractLinkReadFailure(action);}return value;});
        });
    });}
    @Override public Mono<Permit> requireCurrent(ContractLinkReadIdentity identity){return Mono.deferContextual(context->{
        var execution=current(context);if(!execution.identity.equals(identity)||execution.permit.get()!=null)return Mono.error(denied());
        return evaluate(execution,"authorize").map(evaluation->{String revision=evaluation.provenance().orElseThrow().policyRevision();
            var permit=new Permit(identity,settings.resourceTenantId(),settings.roleInContractId(),new AuditActor(execution.principal.issuer(),execution.principal.subject(),settings.allowedClientId(),execution.principal.tenantId(),null,null),settings.bindingId(),settings.bindingVersion(),settings.bindingDigest(),revision,execution.observed,execution.observedNanos,execution.until);
            if(!execution.permit.compareAndSet(null,permit))throw denied();return permit;
        });
    });}
    @Override public Mono<Void> revalidate(Permit original){return Mono.deferContextual(context->{
        var execution=current(context);if(execution.permit.get()!=original)return Mono.error(denied());
        return evaluate(execution,"current").flatMap(evaluation->original.policyRevision().equals(evaluation.provenance().orElseThrow().policyRevision())?Mono.empty():Mono.error(denied()));
    });}
    private Mono<PolicyEvaluation> evaluate(ExecutionImpl execution,String stage){return same(execution).flatMap(principal->{
        Map<String,Object> facts=new LinkedHashMap<>();facts.put("profile","managed-initial-primary-loan-contract-observe-1");facts.put("stage",stage);facts.put("resourceTenantId",settings.resourceTenantId());facts.put("bindingId",settings.bindingId());facts.put("bindingVersion",settings.bindingVersion());facts.put("bindingDigest",settings.bindingDigest());facts.put("applicationId",execution.identity.applicationId());facts.put("partyId",execution.identity.partyId());facts.put("actionId",execution.identity.actionId());facts.put("roleInContractId",settings.roleInContractId());facts.put("purpose",ContractLinkPurpose.INITIAL_PRIMARY_LOAN_CONTRACT.name());
        return policies.evaluate(principal,ACTION,"urn:firefly:loan-contract-link:binding:"+settings.bindingId()+":application:"+execution.identity.applicationId()+":party:"+execution.identity.partyId()+":action:"+execution.identity.actionId(),facts).switchIfEmpty(Mono.error(unavailable())).flatMap(evaluation->{
            if(!evaluation.decision().granted())return Mono.error(denied());if(!evaluation.decision().obligations().isEmpty()||evaluation.provenance().isEmpty())return Mono.error(unavailable());return same(execution).thenReturn(evaluation);
        });
    }).timeout(remaining(execution));}
    private Mono<SecurityPrincipal> same(ExecutionImpl execution){return authentication().filter(current->current==execution.authentication).switchIfEmpty(Mono.error(denied())).map(current->{var principal=principal(current);if(!principal.equals(execution.principal))throw denied();remaining(execution);return principal;});}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(current->current instanceof JwtAuthenticationToken&&current.isAuthenticated()).cast(JwtAuthenticationToken.class).switchIfEmpty(Mono.error(denied()));}
    private SecurityPrincipal principal(JwtAuthenticationToken authentication){
        Jwt jwt=authentication.getToken();Instant now=clock.instant();settings.requireCurrent(now);Object rawParty=jwt.getClaims().get("party_id"),rawScope=jwt.getClaims().get("scope");
        if(jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||jwt.getSubject()==null||jwt.getSubject().isBlank()||!List.of(settings.audience()).equals(jwt.getAudience())
            ||!settings.allowedClientId().equals(jwt.getClaims().get("client_id"))||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!(rawParty instanceof String signedParty)||!(rawScope instanceof String scopes)||!Set.copyOf(Arrays.asList(scopes.split(" +"))).contains(ACTION))throw denied();
        UUID party;try{party=UUID.fromString((String)rawParty);}catch(IllegalArgumentException malformed){throw denied();}if(!valid(party)||!party.toString().equals(rawParty))throw denied();
        if(!(authentication.getPrincipal() instanceof SecurityPrincipal projected)||!settings.issuer().equals(projected.issuer())||!jwt.getSubject().equals(projected.subject())||!settings.platformTenant().equals(projected.tenantId())||!projected.claims().equals(jwt.getClaims())||!projected.hasScope(ACTION))throw denied();return projected;
    }
    private ExecutionImpl current(reactor.util.context.ContextView context){Object raw=context.getOrDefault(executionKey,null);if(!(raw instanceof ExecutionImpl execution)||!execution.active.get())throw denied();remaining(execution);return execution;}
    private Duration remaining(ExecutionImpl execution){if(!execution.active.get())throw denied();return window(execution);}
    private Duration window(ExecutionImpl execution){long elapsed=nanos.getAsLong()-execution.observedNanos;var wall=Duration.between(clock.instant(),execution.until);var monotonic=Duration.between(execution.observed,execution.until).minusNanos(elapsed);if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private static final class ExecutionImpl {
        final ContractLinkReadIdentity identity;final JwtAuthenticationToken authentication;final SecurityPrincipal principal;final Instant observed,until;final long observedNanos;final AtomicBoolean active=new AtomicBoolean(true);final AtomicReference<Permit> permit=new AtomicReference<>();
        ExecutionImpl(ContractLinkReadIdentity identity,JwtAuthenticationToken authentication,SecurityPrincipal principal,Instant observed,long observedNanos,Instant until){this.identity=identity;this.authentication=authentication;this.principal=principal;this.observed=observed;this.observedNanos=observedNanos;this.until=until;}
    }
    private static boolean valid(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original contract party and observation authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current scoped initial contract observation unavailable");}
}
