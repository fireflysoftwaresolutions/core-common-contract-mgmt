/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import io.fireflysoftware.core.contracts.web.linkage.impl.AuthenticatedContractLinkAdmissionImpl;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Constructed authentication isolates authority identity; no contract receipt, storage success or signature proof is fabricated. */
class ContractLinkAdmissionContextTest {
    final Clock clock=Clock.systemUTC();final UUID tenant=UUID.randomUUID(),party=UUID.randomUUID(),role=UUID.randomUUID(),binding=UUID.randomUUID();
    final ContractLinkRequest request=new ContractLinkRequest(UUID.randomUUID(),UUID.randomUUID(),null,UUID.randomUUID(),"EUR");
    final ContractLinkSettings settings=new ContractLinkSettings("https://issuer.invalid","contract-core","domain-lending","opaque-platform",tenant,role,binding,1,"a".repeat(64),clock.instant().plusSeconds(60));
    final AtomicBoolean live=new AtomicBoolean(true);final AtomicReference<String> revision=new AtomicReference<>("contract-policy-1");
    final IllegalStateException reached=new IllegalStateException("authority-only-test-boundary");
    final AuthenticatedContractLinkAdmissionImpl authority=new AuthenticatedContractLinkAdmissionImpl(settings,(principal,action,resource,facts)->{
        assertThat(action).isEqualTo(AuthenticatedContractLinkAdmissionImpl.ACTION);assertThat(facts.get("purpose")).isEqualTo("INITIAL_PRIMARY_LOAN_CONTRACT");
        assertThat(facts.get("resourceTenantId")).isEqualTo(tenant);assertThat(facts.get("roleInContractId")).isEqualTo(role);
        boolean allowed=live.get()&&party.equals(facts.get("partyId"))&&request.applicationId().equals(facts.get("applicationId"))&&request.productId().equals(facts.get("productId"))&&"EUR".equals(facts.get("currencyCode"));
        return Mono.just(new PolicyEvaluation(allowed?Decision.permit():Decision.deny("refused"),Optional.of(new PolicyEvaluationProvenance(revision.get(),"controlled-authority-decision"))));
    },clock,System::nanoTime);
    @Test void missingAuthenticationCannotReachOwner(){var calls=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->{calls.incrementAndGet();return Mono.never();})).expectError().verify();assertThat(calls).hasValue(0);}
    @Test void wrongClientScopeOrServiceKindCannotReachOwner(){for(var claims:List.<Map<String,Object>>of(Map.of("client_id","another-client"),Map.of("scope","contracts:read"),Map.of("principal_kind","SERVICE"))){
        var calls=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->{calls.incrementAndGet();return Mono.never();}).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,claims)))).expectError().verify();assertThat(calls).hasValue(0);
    }}
    @Test void signedForeignPartyStillNeedsTheApplicationSpecificPolicyGrant(){var writes=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->{writes.incrementAndGet();return Mono.error(reached);})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(UUID.randomUUID(),Map.of())))).expectError().verify();assertThat(writes).hasValue(0);}
    @Test void exactPermitPreservesCompleteOriginalIntentAndOpaqueHuman(){StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->{
        assertThat(permit.intent()).isEqualTo(ContractLinkIntent.of(request,tenant,party,role));assertThat(permit.actor().subject()).isEqualTo("opaque:human/é");assertThat(permit.actor().clientId()).isEqualTo("domain-lending");assertThat(permit.actor().platformTenantId()).isEqualTo("opaque-platform");
        return authority.revalidate(permit).then(Mono.error(reached));
    })).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectErrorMatches(error->error==reached).verify();}
    @Test void callerConstructedRequestAndSavedPermitCannotEscapePrivateExecution(){var saved=new AtomicReference<ContractLinkAdmission.Permit>();
        StepVerifier.create(authority.requireCurrent(request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectError().verify();
        StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->{saved.set(permit);return Mono.error(reached);})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectErrorMatches(error->error==reached).verify();
        assertThat(saved.get()).isNotNull();StepVerifier.create(authority.revalidate(saved.get()).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectError().verify();
    }
    @Test void equalClaimsInAnotherAuthenticationCannotRevalidateTheOriginalPermit(){var original=authentication(party,Map.of());var replacement=new JwtAuthenticationToken(original.getToken(),original.getPrincipal(),original.getAuthorities());
        StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->authority.revalidate(permit).contextWrite(ReactiveSecurityContextHolder.withAuthentication(replacement))).then(Mono.error(reached))).contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).expectErrorMatches(error->error!=reached).verify();
    }
    @Test void currentRevocationOrChangedPolicyRevisionPreventsFurtherAuthority(){for(boolean revoke:List.of(true,false)){
        live.set(true);revision.set("contract-policy-1");StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->{if(revoke)live.set(false);else revision.set("contract-policy-2");return authority.revalidate(permit).then(Mono.error(reached));})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectErrorMatches(error->error!=reached).verify();
    }}
    @Test void unrelatedAuditEventCannotUseTheCapturedAppendPermit(){var appends=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(value).flatMap(permit->{
        var event=new AuditEvent(UUID.randomUUID(),new AuditStream(tenant,"core-contract-management","contract",UUID.randomUUID()),permit.actor(),"contracts.other.link",AuditEvent.Outcome.COMPLETED,request.actionId(),request.actionId(),null,clock.instant(),"b".repeat(64),permit.policyRevision());
        return authority.withinExpectedAppend(permit,event,()->{appends.incrementAndGet();return Mono.error(reached);});
    })).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,Map.of())))).expectErrorMatches(error->error!=reached).verify();assertThat(appends).hasValue(0);}
    JwtAuthenticationToken authentication(UUID signedParty,Map<String,Object> overrides){var now=clock.instant();var builder=Jwt.withTokenValue("constructed-not-native-signature-proof").header("alg","RS256").issuer(settings.issuer()).subject("opaque:human/é").audience(List.of(settings.audience())).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30)).claim("client_id",settings.allowedClientId()).claim("principal_kind","HUMAN").claim("tenant_id",settings.platformTenant()).claim("party_id",signedParty.toString()).claim("scope",AuthenticatedContractLinkAdmissionImpl.ACTION);overrides.forEach(builder::claim);var jwt=builder.build();
        var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));var principal=factory.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims()).toBuilder().tenantId(settings.platformTenant()).build();return new JwtAuthenticationToken(jwt,principal,List.of());
    }
}
