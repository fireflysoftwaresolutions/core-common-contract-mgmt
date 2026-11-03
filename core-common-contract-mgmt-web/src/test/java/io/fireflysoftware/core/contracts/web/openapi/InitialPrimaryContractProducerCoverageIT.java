/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.openapi;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InitialPrimaryContractProducerCoverageIT {
    static final String PATH="/api/v1/initial-primary-loan-contract-links/applications/{applicationId}/actions/{actionId}";
    static final Set<String> VERBS=Set.of("get","post","put","patch","delete","head","options","trace");
    @Test void genuineWholeControllerCapturePreservesEveryOriginalContractAndPublishesTheBoundedInitialLink()throws Exception {
        var yaml=new ObjectMapper(new YAMLFactory());var original=yaml.readTree(read("contract.link.producer.original"));var candidate=yaml.readTree(read("contract.link.producer.candidate"));
        var expected=new HashSet<>(operations(original));assertThat(expected.add(List.of(PATH,"post","linkInitialPrimaryLoanContract"))).isTrue();assertThat(expected.add(List.of(PATH,"get","observeInitialPrimaryLoanContract"))).isTrue();assertThat(operations(candidate)).containsExactlyInAnyOrderElementsOf(expected);
        original.path("paths").properties().forEach(row->assertThat(candidate.path("paths").path(row.getKey())).isEqualTo(row.getValue()));
        original.path("components").path("schemas").properties().forEach(row->assertThat(candidate.path("components").path("schemas").path(row.getKey())).isEqualTo(row.getValue()));
        original.path("components").path("securitySchemes").properties().forEach(row->assertThat(candidate.path("components").path("securitySchemes").path(row.getKey())).isEqualTo(row.getValue()));
        var write=candidate.path("paths").path(PATH).path("post");var read=candidate.path("paths").path(PATH).path("get");
        for(String status:List.of("201","200"))assertThat(schema(write,status)).isEqualTo("#/components/schemas/ContractLinkReceipt");
        for(String status:List.of("409","503"))assertThat(schema(write,status)).isEqualTo("#/components/schemas/ContractLinkError");
        assertThat(schema(read,"200")).isEqualTo("#/components/schemas/ContractLinkReceipt");assertThat(schema(read,"503")).isEqualTo("#/components/schemas/ContractLinkError");assertThat(read.path("responses").path("204").path("content").isMissingNode()).isTrue();
        for(String status:List.of("400","401","403"))assertThat(schema(write,status)).isEmpty();
        for(String status:List.of("401","403"))assertThat(schema(read,status)).isEmpty();
        for(JsonNode operation:List.of(write,read)){assertThat(operation.path("security").findValues("contractReadBearer")).hasSize(1);assertThat(operation.path("responses").has("401")).isTrue();assertThat(operation.path("responses").has("403")).isTrue();}
        for(JsonNode parameter:write.path("parameters"))assertThat(parameter.path("name").asText()).isNotEqualToIgnoringCase("X-Idempotency-Key");
        var schemas=candidate.path("components").path("schemas");var request=schemas.path("ContractLinkRequest").path("properties");for(String forbidden:List.of("partyId","roleInContractId","resourceTenantId","contractCreated","contractLinked","contractStatus"))assertThat(request.has(forbidden)).isFalse();
        assertThat(schemas.path("ContractLinkReceipt").path("properties").path("linkedAt").path("$ref").asText()).isEqualTo("#/components/schemas/FireflyInstant");assertThat(schemas.path("FireflyInstant").path("type").asText()).isEqualTo("string");assertThat(schemas.path("FireflyInstant").path("format").asText()).isEqualTo("date-time");
        var required=new HashSet<String>();schemas.path("ContractLinkReceipt").path("required").forEach(value->required.add(value.asText()));assertThat(required).contains("actionId","purpose","applicationId","resourceTenantId","partyId","roleInContractId","contractId","contractPartyId","productId","currencyCode","contractStatusAtLink","intentDigest","linkedAt");
        Files.writeString(Path.of(System.getProperty("contract.link.producer.coverage")),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("originalOperations",operations(original),"candidateOperations",operations(candidate),"originalPathsSchemasAndSecurityPreserved",true,"initialPurpose","INITIAL_PRIMARY_LOAN_CONTRACT","actualInstantSchema",true))+"\n");
    }
    static String schema(JsonNode operation,String status){return operation.path("responses").path(status).path("content").path("application/json").path("schema").path("$ref").asText();}
    static Set<List<String>> operations(JsonNode document){assertThat(document.path("openapi").asText()).startsWith("3.");assertThat(document.path("paths").isObject()).isTrue();var tuples=new HashSet<List<String>>();document.path("paths").properties().forEach(path->path.getValue().properties().forEach(operation->{if(VERBS.contains(operation.getKey())){String id=operation.getValue().path("operationId").asText();assertThat(id).isNotBlank();assertThat(tuples.add(List.of(path.getKey(),operation.getKey(),id))).isTrue();}}));return tuples;}
    static byte[] read(String property)throws Exception{var path=Path.of(Objects.requireNonNull(System.getProperty(property)));assertThat(path).isAbsolute();assertThat(Files.isRegularFile(path)&&!Files.isSymbolicLink(path)).isTrue();assertThat(Files.size(path)).isBetween(1L,524288L);return Files.readAllBytes(path);}
}
