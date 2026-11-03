/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.contracts.web.read.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.contracts.core.services.*;
import io.fireflysoftware.core.contracts.interfaces.dtos.*;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.web.read.ContractReadSettings;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedContractReadServiceImplTest {
    static final Instant NOW=Instant.parse("2026-10-31T12:00:00Z");
    static final UUID CONTRACT=UUID.fromString("10000000-0000-4000-8000-000000000001"), PARTY=UUID.fromString("10000000-0000-4000-8000-000000000002"),
        ROLE=UUID.fromString("10000000-0000-4000-8000-000000000003"), TENANT=UUID.fromString("10000000-0000-4000-8000-000000000004"),
        BINDING=UUID.fromString("10000000-0000-4000-8000-000000000005"), PRODUCT=UUID.fromString("10000000-0000-4000-8000-000000000006");
    final ContractService contracts=mock(ContractService.class);
    final ContractPartyService parties=mock(ContractPartyService.class);
    final AtomicBoolean permit=new AtomicBoolean(true);
    final PolicyEvaluationPort pdp=(principal,action,resource,facts)->Mono.just(new PolicyEvaluation(permit.get()?Decision.permit():Decision.deny("withdrawn"),Optional.of(new PolicyEvaluationProvenance("owned-read-policy","owned-read-decision"))));
    final ContractReadSettings settings=new ContractReadSettings(true,"https://issuer.invalid","contracts-core","https://issuer.invalid/jwks","insurance-core","opaque-tenant",TENANT,BINDING,1,"a".repeat(64),ROLE,NOW.plusSeconds(3600),false);
    @SuppressWarnings("unchecked")
    AuthenticatedContractReadServiceImpl service(){
        ObjectProvider<PolicyEvaluationPort> provider=mock(ObjectProvider.class);when(provider.getIfUnique()).thenReturn(pdp);
        return new AuthenticatedContractReadServiceImpl(contracts,parties,provider,settings,Clock.fixed(NOW,ZoneOffset.UTC),System::nanoTime);
    }
    JwtAuthenticationToken human(String kind){return new JwtAuthenticationToken(Jwt.withTokenValue("unit-not-a-wire-token").header("alg","RS256").issuer(settings.issuer()).subject("opaque-human")
        .audience(List.of(settings.audience())).issuedAt(NOW.minusSeconds(2)).notBefore(NOW.minusSeconds(1)).expiresAt(NOW.plusSeconds(60))
        .claim("client_id",settings.allowedClientId()).claim("principal_kind",kind).claim("tenant_id",settings.platformTenant()).claim("party_id",PARTY.toString())
        .claim("scope","contracts:read contracts:parties:read").build(),List.of());}
    ContractDTO contract(){return ContractDTO.builder().contractId(CONTRACT).contractStatus(ContractStatusEnum.ACTIVE).productId(PRODUCT).currencyCode("USD").build();}
    PaginationResponse<ContractPartyDTO> owner(){return PaginationResponse.<ContractPartyDTO>builder().content(List.of(ContractPartyDTO.builder().contractPartyId(UUID.randomUUID()).contractId(CONTRACT).partyId(PARTY).roleInContractId(ROLE).isActive(true).build())).totalElements(1).totalPages(1).currentPage(0).build();}
    @Test void anonymousAndMachineRefuseBeforeEitherOwner(){
        var unvalidated=new JwtAuthenticationToken(human("HUMAN").getToken());
        assertThat(unvalidated.isAuthenticated()).isFalse();
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(unvalidated)))
            .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403)).verify();
        StepVerifier.create(service().contract(CONTRACT)).expectError(ResponseStatusException.class).verify();
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("STAFDA")))).expectError(ResponseStatusException.class).verify();
        verifyNoInteractions(contracts,parties);
    }
    @Test void actualOwnerContractAndActivePartyAreRequiredAndReturnedAsCopies(){
        assertThat(human("HUMAN").isAuthenticated()).isTrue();
        var input=contract();when(contracts.getContractById(CONTRACT)).thenReturn(Mono.just(input));when(parties.filterContractParties(any())).thenReturn(Mono.just(owner()));
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("HUMAN"))))
            .assertNext(value->{assertThat(value).isNotSameAs(input);assertThat(value.getContractId()).isEqualTo(CONTRACT);assertThat(value.getProductId()).isEqualTo(PRODUCT);assertThat(value.getCurrencyCode()).isEqualTo("USD");}).verifyComplete();
        verify(contracts).getContractById(CONTRACT);verify(parties).filterContractParties(any());
    }
    @Test void currentPolicyWithdrawalSuppressesOwnerResult(){
        when(contracts.getContractById(CONTRACT)).thenReturn(Mono.just(contract()));when(parties.filterContractParties(any())).thenReturn(Mono.defer(()->{permit.set(false);return Mono.just(owner());}));
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("HUMAN"))))
            .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403)).verify();
        verify(contracts).getContractById(CONTRACT);verify(parties).filterContractParties(any());
    }
    @Test void malformedCurrencyAndWrongOwnerCannotBecomeContractEvidence(){
        var bad=contract();bad.setCurrencyCode("usd");when(contracts.getContractById(CONTRACT)).thenReturn(Mono.just(bad));
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("HUMAN"))))
            .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(404)).verify();
        verify(contracts).getContractById(CONTRACT);verifyNoInteractions(parties);
    }
    @Test void callerCannotRequestAnotherPartyOrChangeExactBoundedFilter(){
        var request=new FilterRequest<ContractPartyDTO>();request.setFilters(ContractPartyDTO.builder().contractId(CONTRACT).partyId(UUID.randomUUID()).roleInContractId(ROLE).isActive(true).build());request.setPagination(new PaginationRequest(0,2,"contractPartyId","ASC"));
        StepVerifier.create(service().parties(CONTRACT,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("HUMAN"))))
            .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(400)).verify();verifyNoInteractions(contracts,parties);
    }
    @Test void emptyPartyPublisherDoesNotBecomeVisibleContract(){
        when(contracts.getContractById(CONTRACT)).thenReturn(Mono.just(contract()));when(parties.filterContractParties(any())).thenReturn(Mono.empty());
        StepVerifier.create(service().contract(CONTRACT).contextWrite(ReactiveSecurityContextHolder.withAuthentication(human("HUMAN"))))
            .expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(404)).verify();
        verify(contracts).getContractById(CONTRACT);verify(parties).filterContractParties(any());
    }

}
