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

import io.fireflysoftware.core.contracts.core.services.*;
import io.fireflysoftware.core.contracts.interfaces.dtos.*;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.web.read.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

public final class AuthenticatedContractReadServiceImpl implements AuthenticatedContractReadService {
    private final ContractService contracts;
    private final ContractPartyService parties;
    private final ObjectProvider<PolicyEvaluationPort> policies;
    private final ContractReadSettings settings;
    private final Clock clock;
    private final LongSupplier nanos;
    public AuthenticatedContractReadServiceImpl(ContractService contracts,ContractPartyService parties,ObjectProvider<PolicyEvaluationPort> policies,
            ContractReadSettings settings,Clock clock,LongSupplier nanos){this.contracts=Objects.requireNonNull(contracts);this.parties=Objects.requireNonNull(parties);
        this.policies=Objects.requireNonNull(policies);this.settings=Objects.requireNonNull(settings);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);}
    @Override public Mono<ContractDTO> contract(UUID id){return read(id,"contracts:read",session->Mono.defer(()->contracts.getContractById(id))
        .switchIfEmpty(Mono.error(hidden())).map(value->contractSnapshot(id,value)).flatMap(value->Mono.defer(()->parties.filterContractParties(ownerFilter(id,session.party))).switchIfEmpty(Mono.error(hidden()))
            .map(page->partySnapshot(id,session.party,page)).thenReturn(value)),value->Map.of("productId",value.getProductId(),"currencyCode",value.getCurrencyCode(),"contractStatus",value.getContractStatus().name()));}
    @Override public Mono<PaginationResponse<ContractPartyDTO>> parties(UUID id,FilterRequest<ContractPartyDTO> supplied){return read(id,"contracts:parties:read",session->{
        requireOwnerRequest(id,session.party,supplied);
        return Mono.defer(()->contracts.getContractById(id)).switchIfEmpty(Mono.error(hidden())).map(value->contractSnapshot(id,value))
            .flatMap(contract->Mono.defer(()->parties.filterContractParties(ownerFilter(id,session.party)))).switchIfEmpty(Mono.error(hidden())).map(page->partySnapshot(id,session.party,page));
    },value->Map.of("activeParties",value.getTotalElements()));}
    private <T> Mono<T> read(UUID id,String action,Function<Session,Mono<T>> work,Function<T,Map<String,Object>> facts){return Mono.defer(()->{
        if(id==null)return Mono.error(invalid());settings.requireCurrent(clock.instant());
        return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication())
            .filter(auth->auth instanceof JwtAuthenticationToken&&auth.isAuthenticated()).switchIfEmpty(Mono.error(denied()))
            .flatMap(auth->{var jwt=((JwtAuthenticationToken)auth).getToken();var session=session(id,action,jwt);
                return authorize(session,"request",Map.of()).then(Mono.defer(()->work.apply(session)))
                    .flatMap(value->authorize(session,"response",facts.apply(value)).thenReturn(value)).timeout(remaining(session));});
    }).onErrorMap(error->error instanceof ResponseStatusException?error:unavailable());}
    private Session session(UUID id,String action,Jwt jwt){Instant now=clock.instant();String party=jwt.getClaimAsString("party_id");
        if(jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||jwt.getSubject()==null||jwt.getSubject().isBlank()
            ||!List.of(settings.audience()).equals(jwt.getAudience())||!settings.allowedClientId().equals(jwt.getClaimAsString("client_id"))
            ||!"HUMAN".equals(jwt.getClaimAsString("principal_kind"))||!settings.platformTenant().equals(jwt.getClaimAsString("tenant_id"))
            ||jwt.getExpiresAt()==null||!now.isBefore(jwt.getExpiresAt())||jwt.getNotBefore()==null||now.isBefore(jwt.getNotBefore())
            ||party==null||!party.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw denied();
        String raw=jwt.getClaimAsString("scope");var scopes=raw==null?Set.<String>of():Set.copyOf(Arrays.asList(raw.split(" +")));
        if(!scopes.contains(action))throw denied();
        var principal=SecurityPrincipal.builder().issuer(settings.issuer()).subject(jwt.getSubject()).tenantId(settings.platformTenant()).scopes(scopes).claims(jwt.getClaims()).build();
        Instant until=now.plusSeconds(5);if(jwt.getExpiresAt().isBefore(until))until=jwt.getExpiresAt();if(settings.notAfter().isBefore(until))until=settings.notAfter();
        return new Session(id,action,UUID.fromString(party),jwt,principal,now,nanos.getAsLong(),until);
    }
    private Mono<Void> authorize(Session session,String stage,Map<String,Object> observed){return Mono.defer(()->{
        remaining(session);settings.requireCurrent(clock.instant());session(session.id,session.action,session.jwt);
        var pdp=policies.getIfUnique();if(pdp==null)return Mono.error(unavailable());
        Map<String,Object> facts=new LinkedHashMap<>();facts.put("profile","single-dataset-contract-read-1");facts.put("stage",stage);facts.put("resourceTenantId",settings.resourceTenantId());
        facts.put("configurationBindingId",settings.configurationBindingId());facts.put("configurationBindingVersion",settings.configurationBindingVersion());facts.put("configurationBindingDigest",settings.configurationBindingDigest());
        facts.put("contractId",session.id);facts.put("partyId",session.party);facts.put("policyholderRoleId",settings.policyholderRoleId());facts.putAll(observed);
        return pdp.evaluate(session.principal,session.action,"urn:firefly:contract-dataset:"+settings.configurationBindingId()+":contract:"+session.id+":party:"+session.party,Map.copyOf(facts))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(value->{remaining(session);
                if(!value.decision().granted())return Mono.error(denied());
                if(!value.decision().obligations().isEmpty()||value.provenance().isEmpty())return Mono.error(unavailable());return Mono.<Void>empty();});
    });}
    private FilterRequest<ContractPartyDTO> ownerFilter(UUID id,UUID party){var request=new FilterRequest<ContractPartyDTO>();request.setFilters(ContractPartyDTO.builder().contractId(id).partyId(party).roleInContractId(settings.policyholderRoleId()).isActive(true).build());
        request.setPagination(new PaginationRequest(0,2,"contractPartyId","ASC"));return request;}
    private void requireOwnerRequest(UUID id,UUID party,FilterRequest<ContractPartyDTO> request){if(request==null||request.getFilters()==null||request.getPagination()==null||request.getRangeFilters()!=null||request.getOptions()!=null)throw invalid();
        var value=request.getFilters();var page=request.getPagination();
        if(!id.equals(value.getContractId())||!party.equals(value.getPartyId())||!settings.policyholderRoleId().equals(value.getRoleInContractId())||!Boolean.TRUE.equals(value.getIsActive())
            ||value.getContractPartyId()!=null||value.getDateJoined()!=null||value.getDateLeft()!=null||value.getCreatedAt()!=null||value.getUpdatedAt()!=null
            ||page.getPageNumber()!=0||page.getPageSize()!=2||!"contractPartyId".equals(page.getSortBy())||!"ASC".equals(page.getSortDirection()))throw invalid();}
    private ContractDTO contractSnapshot(UUID id,ContractDTO value){if(value==null)throw hidden();
        var copy=ContractDTO.builder().contractId(value.getContractId()).contractNumber(value.getContractNumber()).contractStatus(value.getContractStatus()).startDate(value.getStartDate()).endDate(value.getEndDate())
            .productCatalogId(value.getProductCatalogId()).productId(value.getProductId()).currencyCode(value.getCurrencyCode()).predecessorContractId(value.getPredecessorContractId()).linkKind(value.getLinkKind()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
        if(!id.equals(copy.getContractId())||copy.getContractStatus()!=ContractStatusEnum.ACTIVE||copy.getProductId()==null||copy.getCurrencyCode()==null||!copy.getCurrencyCode().matches("[A-Z]{3}"))throw hidden();return copy;}
    private PaginationResponse<ContractPartyDTO> partySnapshot(UUID id,UUID party,PaginationResponse<ContractPartyDTO> page){if(page==null||page.getContent()==null||page.getContent().size()!=1||page.getTotalElements()!=1||page.getTotalPages()!=1||page.getCurrentPage()!=0)throw hidden();
        var v=page.getContent().getFirst();if(v==null||v.getContractPartyId()==null||!id.equals(v.getContractId())||!party.equals(v.getPartyId())||!settings.policyholderRoleId().equals(v.getRoleInContractId())||!Boolean.TRUE.equals(v.getIsActive()))throw hidden();
        var copy=ContractPartyDTO.builder().contractPartyId(v.getContractPartyId()).contractId(v.getContractId()).partyId(v.getPartyId()).roleInContractId(v.getRoleInContractId()).isActive(v.getIsActive()).dateJoined(v.getDateJoined()).dateLeft(v.getDateLeft()).createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt()).build();
        return PaginationResponse.<ContractPartyDTO>builder().content(List.of(copy)).totalElements(1).totalPages(1).currentPage(0).build();}
    private Duration remaining(Session session){long elapsed=nanos.getAsLong()-session.nanos;var wall=Duration.between(clock.instant(),session.until);var monotonic=Duration.between(session.observed,session.until).minusNanos(elapsed);
        if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private record Session(UUID id,String action,UUID party,Jwt jwt,SecurityPrincipal principal,Instant observed,long nanos,Instant until){}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Contract read is not permitted");}
    private static ResponseStatusException hidden(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"No visible active contract party");}
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported contract-party filter");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Contract read is unavailable");}
}
