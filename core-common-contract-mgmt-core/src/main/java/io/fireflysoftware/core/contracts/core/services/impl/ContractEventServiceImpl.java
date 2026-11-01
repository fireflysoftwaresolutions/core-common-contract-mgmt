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
import io.fireflysoftware.core.contracts.core.mappers.ContractEventMapper;
import io.fireflysoftware.core.contracts.core.services.ContractEventService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractEventDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractEvent;
import io.fireflysoftware.core.contracts.models.repositories.ContractEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class ContractEventServiceImpl implements ContractEventService {

    @Autowired
    private ContractEventRepository repository;

    @Autowired
    private ContractEventMapper mapper;

    @Override
    public Mono<PaginationResponse<ContractEventDTO>> filterContractEvents(FilterRequest<ContractEventDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        ContractEvent.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractEventDTO> createContractEvent(ContractEventDTO contractEventDTO) {
        return Mono.just(contractEventDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractEventDTO> updateContractEvent(UUID contractEventId, ContractEventDTO contractEventDTO) {
        return repository.findById(contractEventId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract event not found with ID: " + contractEventId)))
                .flatMap(existingEvent -> ContractScopeImpl.requireSame(
                                contractEventDTO.getContractId(), existingEvent.getContractId(), existingEvent,
                                "Contract event", contractEventId)
                        .flatMap(owned -> {
                            ContractEvent updatedEvent = mapper.toEntity(contractEventDTO);
                            updatedEvent.setContractEventId(contractEventId);
                            updatedEvent.setContractId(owned.getContractId());
                            return repository.save(updatedEvent);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContractEvent(UUID contractEventId) {
        return repository.findById(contractEventId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract event not found with ID: " + contractEventId)))
                .flatMap(event -> repository.deleteById(contractEventId));
    }

    @Override
    public Mono<ContractEventDTO> getContractEventById(UUID contractEventId) {
        return repository.findById(contractEventId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract event not found with ID: " + contractEventId)))
                .map(mapper::toDTO);
    }
}
