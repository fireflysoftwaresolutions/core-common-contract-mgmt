/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contract.sdk;

import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.invoker.ApiClient;
import io.fireflysoftware.core.contract.sdk.model.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import static org.assertj.core.api.Assertions.*;

class ProtectedContractGeneratedSdkTest {
    static final UUID CONTRACT=UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final UUID PARTY=UUID.fromString("10000000-0000-4000-8000-000000000002");
    static final UUID ROLE=UUID.fromString("10000000-0000-4000-8000-000000000003");
    static final UUID MEMBERSHIP=UUID.fromString("10000000-0000-4000-8000-000000000004");
    static final UUID PRODUCT=UUID.fromString("10000000-0000-4000-8000-000000000005");
    static final String MARKER="unit-not-a-wire-token";
    static final String CONTRACT_JSON="""
        {"contractId":"%s","productId":"%s","currencyCode":"USD","contractStatus":"ACTIVE",
         "startDate":"2026-01-01T00:00:00","endDate":"2027-01-01T00:00:00"}
        """.formatted(CONTRACT,PRODUCT);
    static final String PAGE_JSON="""
        {"content":[{"contractPartyId":"%s","contractId":"%s","partyId":"%s","roleInContractId":"%s",
         "isActive":true,"dateJoined":"2025-01-01T00:00:00"}],"totalElements":1,"totalPages":1,"currentPage":0}
        """.formatted(MEMBERSHIP,CONTRACT,PARTY,ROLE);

    record Request(String method,String path,String authorization,String body,String idempotencyKey) { }
    static final class TransportFixtureImpl implements AutoCloseable {
        final HttpServer server;
        final AtomicBoolean refused=new AtomicBoolean();
        final List<Request> requests=new CopyOnWriteArrayList<>();
        final ContractsApi contracts;
        final ContractPartiesApi parties;
        TransportFixtureImpl() throws IOException {
            var client=new ApiClient();client.setBearerToken(MARKER);
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            try {
            server.createContext("/",exchange->{
                try(exchange) {
                    var body=exchange.getRequestBody().readNBytes(65537);
                    var authorization=exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                    var path=exchange.getRequestURI().getPath();
                    requests.add(new Request(exchange.getRequestMethod(),path,authorization,new String(body,StandardCharsets.UTF_8),
                        exchange.getRequestHeaders().getFirst("X-Idempotency-Key")));
                    int status=body.length>65536?413:!("Bearer "+MARKER).equals(authorization)?401:refused.get()?403:200;
                    var response=(status!=200?"{}":path.endsWith("/parties/filter")?PAGE_JSON:CONTRACT_JSON).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,"application/json");
                    exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);
                }
            });
            client.setBasePath("http://127.0.0.1:"+server.getAddress().getPort());
            contracts=new ContractsApi(client);parties=new ContractPartiesApi(client);
            server.start();
            } catch(RuntimeException|Error failure) {
                server.stop(0);throw failure;
            }
        }
        public void close() { server.stop(0); }
    }

    @Test void generatedBearerGetReturnsTypedContractAndDates() throws Exception {
        try(var fixture=new TransportFixtureImpl()) {
            var response=fixture.contracts.getContractByIdWithHttpInfo(CONTRACT,null).block(Duration.ofSeconds(2));
            assertThat(response).isNotNull();assertThat(response.getStatusCode().value()).isEqualTo(200);
            ContractDTO value=response.getBody();assertThat(value).isNotNull();
            assertThat(value.getContractId()).isEqualTo(CONTRACT);assertThat(value.getProductId()).isEqualTo(PRODUCT);
            assertThat(value.getCurrencyCode()).isEqualTo("USD");assertThat(value.getContractStatus()).isEqualTo(ContractDTO.ContractStatusEnum.ACTIVE);
            assertThat(value.getStartDate()).isEqualTo(LocalDateTime.of(2026,1,1,0,0));assertThat(value.getEndDate()).isEqualTo(LocalDateTime.of(2027,1,1,0,0));
            assertThat(fixture.requests).hasSize(1);assertThat(fixture.requests.getFirst().method()).isEqualTo("GET");
            assertThat(fixture.requests.getFirst().authorization()).isEqualTo("Bearer "+MARKER);
        }
    }

    @Test void generatedBearerPartyPageHasActualTypedRowsAndPreservesRefusal() throws Exception {
        try(var fixture=new TransportFixtureImpl()) {
            var filter=new FilterRequestContractPartyDTO()
                .filters(new ContractPartyDTO().contractId(CONTRACT).partyId(PARTY).roleInContractId(ROLE).isActive(true))
                .pagination(new PaginationRequest().pageNumber(0).pageSize(2).sortBy("contractPartyId").sortDirection("ASC"));
            var response=fixture.parties.filterContractPartiesWithHttpInfo(CONTRACT,filter).block(Duration.ofSeconds(2));
            assertThat(response).isNotNull();assertThat(response.getStatusCode().value()).isEqualTo(200);
            PaginationResponseContractPartyDTO page=response.getBody();assertThat(page).isNotNull();assertThat(page.getContent()).hasSize(1);
            ContractPartyDTO party=page.getContent().getFirst();assertThat(party.getClass()).isEqualTo(ContractPartyDTO.class);
            assertThat(party.getContractPartyId()).isEqualTo(MEMBERSHIP);assertThat(party.getContractId()).isEqualTo(CONTRACT);
            assertThat(party.getPartyId()).isEqualTo(PARTY);assertThat(party.getRoleInContractId()).isEqualTo(ROLE);assertThat(party.getIsActive()).isTrue();
            assertThat(party.getDateJoined()).isEqualTo(LocalDateTime.of(2025,1,1,0,0));assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getTotalPages()).isEqualTo(1);assertThat(page.getCurrentPage()).isZero();
            fixture.refused.set(true);
            assertThatThrownBy(()->fixture.parties.filterContractPartiesWithHttpInfo(CONTRACT,filter).block(Duration.ofSeconds(2)))
                .isInstanceOfSatisfying(WebClientResponseException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(403));
            assertThat(fixture.requests).hasSize(2);
            assertThat(fixture.requests).allSatisfy(request->{assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/api/v1/contracts/"+CONTRACT+"/parties/filter");
                assertThat(request.authorization()).isEqualTo("Bearer "+MARKER);assertThat(request.idempotencyKey()).isNull();});
            var sent=ApiClient.createDefaultMapper(null).readTree(fixture.requests.getFirst().body());
            assertThat(sent.path("filters").path("contractId").asText()).isEqualTo(CONTRACT.toString());
            assertThat(sent.path("filters").path("partyId").asText()).isEqualTo(PARTY.toString());
            assertThat(sent.path("filters").path("roleInContractId").asText()).isEqualTo(ROLE.toString());
            assertThat(sent.path("filters").path("isActive").asBoolean()).isTrue();
            assertThat(sent.path("pagination").path("pageNumber").asInt()).isZero();assertThat(sent.path("pagination").path("pageSize").asInt()).isEqualTo(2);
        }
    }
}
