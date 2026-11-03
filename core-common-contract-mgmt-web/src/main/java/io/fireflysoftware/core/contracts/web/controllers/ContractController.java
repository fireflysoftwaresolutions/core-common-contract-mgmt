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


package io.fireflysoftware.core.contracts.web.controllers;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.contracts.core.services.ContractService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractDTO;
import io.fireflysoftware.core.contracts.web.read.AuthenticatedContractReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contracts", description = "API for managing contracts")
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "contractReadBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final AuthenticatedContractReadService authenticatedReads;

    @Operation(summary = "Filter contracts", description = "Returns a paginated list of contracts based on filter criteria")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contracts",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = PaginationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter criteria provided", 
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", 
                content = @Content)
    })
    @PostMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<PaginationResponse<ContractDTO>>> filterContracts(
            @Valid @RequestBody FilterRequest<ContractDTO> filterRequest) {
        return ResponseEntity.ok(contractService.filterContracts(filterRequest));
    }

    @Operation(summary = "Create a new contract", description = "Creates a new contract with the provided information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Contract successfully created",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ContractDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid contract data provided", 
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", 
                content = @Content)
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<ContractDTO>> createContract(
            @Valid @RequestBody ContractDTO contractDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(contractDTO));
    }

    @Operation(summary = "Get contract by ID", description = "Returns a contract based on its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contract",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ContractDTO.class))),
        @ApiResponse(responseCode = "404", description = "Contract not found", 
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", 
                content = @Content)
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "contractReadBearer")
    @GetMapping(value = "/{contractId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<ContractDTO>> getContractById(
            @Parameter(description = "ID of the contract to retrieve", required = true)
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(authenticatedReads.contract(contractId));
    }

    @Operation(summary = "Update contract", description = "Updates an existing contract with the provided information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contract successfully updated",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ContractDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid contract data provided", 
                content = @Content),
        @ApiResponse(responseCode = "404", description = "Contract not found", 
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", 
                content = @Content)
    })
    @PutMapping(value = "/{contractId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Mono<ContractDTO>> updateContract(
            @Parameter(description = "ID of the contract to update", required = true)
            @PathVariable UUID contractId,
            @Valid @RequestBody ContractDTO contractDTO) {
        return ResponseEntity.ok(contractService.updateContract(contractId, contractDTO));
    }

    @Operation(summary = "Delete contract", description = "Deletes a contract based on its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Contract successfully deleted",
                content = @Content),
        @ApiResponse(responseCode = "404", description = "Contract not found", 
                content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", 
                content = @Content)
    })
    @DeleteMapping("/{contractId}")
    public Mono<ResponseEntity<Void>> deleteContract(
            @Parameter(description = "ID of the contract to delete", required = true)
            @PathVariable UUID contractId) {
        return contractService.deleteContract(contractId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
