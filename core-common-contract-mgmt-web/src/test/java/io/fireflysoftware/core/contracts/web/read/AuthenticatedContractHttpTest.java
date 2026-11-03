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

package io.fireflysoftware.core.contracts.web.read;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.core.contracts.core.services.*;
import io.fireflysoftware.core.contracts.interfaces.dtos.*;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.web.controllers.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(classes=AuthenticatedContractHttpTest.Boundary.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"server.address=127.0.0.1","firefly.web.idempotency.enabled=true","firefly.cache.default-cache-type=CAFFEINE","firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedContractHttpTest {
    static final UUID CONTRACT=UUID.fromString("10000000-0000-4000-8000-000000000001"),PARTY=UUID.fromString("10000000-0000-4000-8000-000000000002"),
        ROLE=UUID.fromString("10000000-0000-4000-8000-000000000003"),TENANT=UUID.fromString("10000000-0000-4000-8000-000000000004"),
        BINDING=UUID.fromString("10000000-0000-4000-8000-000000000005"),PRODUCT=UUID.fromString("10000000-0000-4000-8000-000000000006");
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static {try {KEY=new RSAKeyGenerator(2048).keyID("owned-contract-read").generate();JWKS=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        JWKS.createContext("/jwks",exchange->{byte[] raw=new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,raw.length);try(var out=exchange.getResponseBody()){out.write(raw);}});JWKS.start();}
        catch(Exception error){throw new ExceptionInInitializerError(error);}}
    @DynamicPropertySource static void trust(DynamicPropertyRegistry registry){
        registry.add("contracts.authenticated-read.enabled",()->true);registry.add("contracts.authenticated-read.issuer",()->"https://owned-contract-issuer.invalid");
        registry.add("contracts.authenticated-read.audience",()->"contracts-core");registry.add("contracts.authenticated-read.jwk-set-uri",()->"http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");
        registry.add("contracts.authenticated-read.allowed-client-id",()->"insurance-core");registry.add("contracts.authenticated-read.platform-tenant",()->"opaque-tenant");
        registry.add("contracts.authenticated-read.resource-tenant-id",TENANT::toString);registry.add("contracts.authenticated-read.configuration-binding-id",BINDING::toString);
        registry.add("contracts.authenticated-read.configuration-binding-version",()->1);registry.add("contracts.authenticated-read.configuration-binding-digest",()->"a".repeat(64));
        registry.add("contracts.authenticated-read.policyholder-role-id",ROLE::toString);registry.add("contracts.authenticated-read.not-after",()->Instant.now().plusSeconds(3600).toString());registry.add("contracts.authenticated-read.allow-loopback-http",()->true);
    }
    @AfterAll static void close(){JWKS.stop(0);}
    @LocalServerPort int port;
    @Autowired ContractService contracts;
    @Autowired ContractPartyService parties;
    @Autowired CurrentPolicyImpl policy;
    @BeforeEach void resetOwners(){reset(contracts,parties);policy.allow.set(true);policy.provenance.set(true);
        when(contracts.getContractById(CONTRACT)).thenReturn(Mono.just(ContractDTO.builder().contractId(CONTRACT).contractStatus(ContractStatusEnum.ACTIVE).productId(PRODUCT).currencyCode("USD").build()));
        when(parties.filterContractParties(any())).thenReturn(Mono.just(PaginationResponse.<ContractPartyDTO>builder().content(List.of(ContractPartyDTO.builder().contractPartyId(UUID.randomUUID()).contractId(CONTRACT).partyId(PARTY).roleInContractId(ROLE).isActive(true).build())).totalElements(1).totalPages(1).currentPage(0).build()));}
    WebTestClient client(){return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build();}
    String token(String kind,String audience,String scope)throws Exception{Instant now=Instant.now();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),new JWTClaimsSet.Builder()
        .issuer("https://owned-contract-issuer.invalid").subject("opaque-human").audience(audience).issueTime(Date.from(now.minusSeconds(1))).notBeforeTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(now.plusSeconds(60)))
        .claim("client_id","insurance-core").claim("principal_kind",kind).claim("tenant_id","opaque-tenant").claim("party_id",PARTY.toString()).claim("scope",scope).build());jwt.sign(new RSASSASigner(KEY));return jwt.serialize();}
    String human()throws Exception{return token("HUMAN","contracts-core","contracts:read contracts:parties:read");}
    String path(){return "/api/v1/contracts/"+CONTRACT;}
    @Test void anonymousWrongAudienceAndMachineCannotReadOwner()throws Exception{
        client().get().uri(path()).exchange().expectStatus().isUnauthorized();
        client().get().uri(path()).headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","foreign","contracts:read"))).exchange().expectStatus().isUnauthorized();
        client().get().uri(path()).headers(h->h.setBearerAuth(tokenUnchecked("STAFDA","contracts-core","contracts:read"))).exchange().expectStatus().isForbidden();verifyNoInteractions(contracts,parties);
    }
    String tokenUnchecked(String kind,String audience,String scope){try{return token(kind,audience,scope);}catch(Exception failure){throw new AssertionError(failure);}}
    @Test void signedHumanReadsActualTypedOwnerFieldsWithoutCookie()throws Exception{
        client().get().uri(path()).headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:read"))).exchange().expectStatus().isOk().expectHeader().doesNotExist("Set-Cookie")
            .expectBody().jsonPath("$.contractId").isEqualTo(CONTRACT.toString()).jsonPath("$.productId").isEqualTo(PRODUCT.toString()).jsonPath("$.currencyCode").isEqualTo("USD");verify(contracts).getContractById(CONTRACT);verify(parties).filterContractParties(any());
    }
    @Test void currentPolicyAndProvenanceAreRequiredBeforeOwner()throws Exception{
        policy.allow.set(false);client().get().uri(path()).headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:read"))).exchange().expectStatus().isForbidden();
        policy.allow.set(true);policy.provenance.set(false);client().get().uri(path()).headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:read"))).exchange().expectStatus().isEqualTo(503);verifyNoInteractions(contracts,parties);
    }
    @Test void activePartyFilterUsesOnlyCurrentHumanAndConfiguredRole()throws Exception{
        String body="{\"filters\":{\"contractId\":\""+CONTRACT+"\",\"partyId\":\""+PARTY+"\",\"roleInContractId\":\""+ROLE+"\",\"isActive\":true},\"pagination\":{\"pageNumber\":0,\"pageSize\":2,\"sortBy\":\"contractPartyId\",\"sortDirection\":\"ASC\"}}";
        client().post().uri(path()+"/parties/filter").header("X-Idempotency-Key","owned-current-read").headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:parties:read"))).contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isOk().expectBody().jsonPath("$.totalElements").isEqualTo(1).jsonPath("$.content[0].partyId").isEqualTo(PARTY.toString());
        policy.allow.set(false);
        client().post().uri(path()+"/parties/filter").header("X-Idempotency-Key","owned-current-read").headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:parties:read"))).contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isForbidden();
        verify(contracts,times(1)).getContractById(CONTRACT);verify(parties,times(1)).filterContractParties(any());
    }
    @Test void alternateContractReadsAndWritesHaveNoReadOnlyBypass()throws Exception{
        client().post().uri("/api/v1/contracts/filter").bodyValue("{}").exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/contract-parties?partyId="+PARTY).exchange().expectStatus().isForbidden();
        client().get().uri(path()+"/parties/"+UUID.randomUUID()).headers(h->h.setBearerAuth(tokenUnchecked("HUMAN","contracts-core","contracts:read"))).exchange().expectStatus().isForbidden();
        client().post().uri("/api/v1/contracts").bodyValue("{}").exchange().expectStatus().isForbidden();verifyNoInteractions(contracts,parties);
    }
    @Test void realProducedMetadataContainsSchemeAndBothTypedSuccessSchemas()throws Exception{
        var body=client().get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();var spec=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertThat(spec.path("components").path("securitySchemes").path("contractReadBearer").path("scheme").asText()).isEqualTo("bearer");
        for(String suffix:List.of("", "/parties/filter")){var operation=spec.path("paths").path("/api/v1/contracts/{contractId}"+suffix).path(suffix.isEmpty()?"get":"post");
            assertThat(operation.path("security").findValues("contractReadBearer")).hasSize(1);assertThat(operation.path("responses").path("200").path("content").path("application/json").path("schema").isMissingNode()).isFalse();}
        var pageSchema=spec.path("paths").path("/api/v1/contracts/{contractId}/parties/filter").path("post").path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(pageSchema).startsWith("#/components/schemas/");
        var page=spec.path("components").path("schemas").path(pageSchema.substring("#/components/schemas/".length()));
        assertThat(page.path("properties").path("content").path("items").path("$ref").asText()).isEqualTo("#/components/schemas/ContractPartyDTO");
        verifyNoInteractions(contracts,parties);
    }
    static class CurrentPolicyImpl implements PolicyEvaluationPort {
        final AtomicBoolean allow=new AtomicBoolean(true),provenance=new AtomicBoolean(true);
        public Mono<PolicyEvaluation> evaluate(SecurityPrincipal principal,String action,String resource,Map<String,Object> facts){return Mono.just(new PolicyEvaluation(allow.get()?Decision.permit():Decision.deny("withdrawn"),provenance.get()?Optional.of(new PolicyEvaluationProvenance("owned-contract-policy","owned-contract-decision")):Optional.empty()));}
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration","org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration","org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration","org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration","org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration"})
    @Import({ContractController.class,ContractPartyController.class,GlobalContractPartyController.class,ContractReadSecurityConfiguration.class})
    static class Boundary {
        @Bean ContractService contracts(){return mock(ContractService.class);}
        @Bean ContractPartyService parties(){return mock(ContractPartyService.class);}
        @Bean CurrentPolicyImpl policy(){return new CurrentPolicyImpl();}
    }
}
