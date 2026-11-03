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
import io.fireflysoftware.core.contracts.core.mappers.ContractPartyMapper;
import io.fireflysoftware.core.contracts.core.services.ContractPartyService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractPartyDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractParty;
import io.fireflysoftware.core.contracts.models.repositories.ContractPartyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class ContractPartyServiceImpl implements ContractPartyService {

    @Autowired
    private ContractPartyRepository repository;

    @Autowired
    private ContractPartyMapper mapper;

    @Override
    public Mono<PaginationResponse<ContractPartyDTO>> filterContractParties(FilterRequest<ContractPartyDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        ContractParty.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractPartyDTO> createContractParty(ContractPartyDTO contractPartyDTO) {
        return Mono.just(contractPartyDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractPartyDTO> updateContractParty(UUID contractPartyId, ContractPartyDTO contractPartyDTO) {
        return repository.findById(contractPartyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract party not found with ID: " + contractPartyId)))
                .flatMap(existingParty -> ContractScopeImpl.requireSame(
                                contractPartyDTO.getContractId(), existingParty.getContractId(), existingParty,
                                "Contract party", contractPartyId)
                        .flatMap(owned -> {
                            ContractParty updatedParty = mapper.toEntity(contractPartyDTO);
                            updatedParty.setContractPartyId(contractPartyId);
                            updatedParty.setContractId(owned.getContractId());
                            return repository.save(updatedParty);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContractParty(UUID contractPartyId) {
        return repository.findById(contractPartyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract party not found with ID: " + contractPartyId)))
                .flatMap(party -> repository.deleteById(contractPartyId));
    }

    @Override
    public Mono<ContractPartyDTO> getContractPartyById(UUID contractPartyId) {
        return repository.findById(contractPartyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract party not found with ID: " + contractPartyId)))
                .map(mapper::toDTO);
    }
}
