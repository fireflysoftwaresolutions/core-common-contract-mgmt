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


package io.fireflysoftware.core.contracts.core.services.impl;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.contracts.core.mappers.ContractDocumentMapper;
import io.fireflysoftware.core.contracts.core.services.ContractDocumentService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractDocumentDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractDocument;
import io.fireflysoftware.core.contracts.models.repositories.ContractDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class ContractDocumentServiceImpl implements ContractDocumentService {

    @Autowired
    private ContractDocumentRepository repository;

    @Autowired
    private ContractDocumentMapper mapper;

    @Override
    public Mono<PaginationResponse<ContractDocumentDTO>> filterContractDocuments(FilterRequest<ContractDocumentDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        ContractDocument.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractDocumentDTO> createContractDocument(ContractDocumentDTO contractDocumentDTO) {
        return Mono.just(contractDocumentDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractDocumentDTO> updateContractDocument(UUID contractDocumentId, ContractDocumentDTO contractDocumentDTO) {
        return repository.findById(contractDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract document not found with ID: " + contractDocumentId)))
                .flatMap(existingDocument -> ContractScopeImpl.requireSame(
                                contractDocumentDTO.getContractId(), existingDocument.getContractId(), existingDocument,
                                "Contract document", contractDocumentId)
                        .flatMap(owned -> {
                            ContractDocument updatedDocument = mapper.toEntity(contractDocumentDTO);
                            updatedDocument.setContractDocumentId(contractDocumentId);
                            updatedDocument.setContractId(owned.getContractId());
                            return repository.save(updatedDocument);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContractDocument(UUID contractDocumentId) {
        return repository.findById(contractDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract document not found with ID: " + contractDocumentId)))
                .flatMap(document -> repository.deleteById(contractDocumentId));
    }

    @Override
    public Mono<ContractDocumentDTO> getContractDocumentById(UUID contractDocumentId) {
        return repository.findById(contractDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract document not found with ID: " + contractDocumentId)))
                .map(mapper::toDTO);
    }
}
