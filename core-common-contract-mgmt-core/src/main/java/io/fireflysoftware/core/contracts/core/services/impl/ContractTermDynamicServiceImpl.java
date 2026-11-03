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
import io.fireflysoftware.core.contracts.core.mappers.ContractTermDynamicMapper;
import io.fireflysoftware.core.contracts.core.services.ContractTermDynamicService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractTermDynamicDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractTermDynamic;
import io.fireflysoftware.core.contracts.models.repositories.ContractTermDynamicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class ContractTermDynamicServiceImpl implements ContractTermDynamicService {

    @Autowired
    private ContractTermDynamicRepository repository;

    @Autowired
    private ContractTermDynamicMapper mapper;

    @Override
    public Mono<PaginationResponse<ContractTermDynamicDTO>> filterContractTermDynamics(FilterRequest<ContractTermDynamicDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        ContractTermDynamic.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractTermDynamicDTO> createContractTermDynamic(ContractTermDynamicDTO contractTermDynamicDTO) {
        return Mono.just(contractTermDynamicDTO)
                .map(mapper::toEntity)
                .flatMap(this::rejectOverlap)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractTermDynamicDTO> updateContractTermDynamic(UUID termId, ContractTermDynamicDTO contractTermDynamicDTO) {
        return repository.findById(termId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract term dynamic not found with ID: " + termId)))
                .flatMap(existingTerm -> ContractScopeImpl.requireSame(
                                contractTermDynamicDTO.getContractId(), existingTerm.getContractId(), existingTerm,
                                "Contract term", termId)
                        .flatMap(owned -> {
                            ContractTermDynamic updatedTerm = mapper.toEntity(contractTermDynamicDTO);
                            updatedTerm.setTermId(termId);
                            updatedTerm.setContractId(owned.getContractId());
                            return rejectOverlap(updatedTerm);
                        }))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    /**
     * Two active values of the same term cannot cover the same interval.
     * An amendment expires the current row and then adds the next one.
     */
    private Mono<ContractTermDynamic> rejectOverlap(ContractTermDynamic candidate) {
        if (Boolean.FALSE.equals(candidate.getIsActive())
                || candidate.getContractId() == null
                || candidate.getTermTemplateId() == null) {
            return Mono.just(candidate);
        }
        LocalDateTime now = LocalDateTime.now();
        return repository.findByContractIdAndTermTemplateIdAndIsActive(
                        candidate.getContractId(), candidate.getTermTemplateId(), true)
                .filter(existing -> candidate.getTermId() == null || !candidate.getTermId().equals(existing.getTermId()))
                .filter(existing -> overlaps(candidate, existing, now))
                .hasElements()
                .flatMap(overlap -> {
                    if (overlap) {
                        return Mono.error(new RuntimeException(
                                "An active term for template " + candidate.getTermTemplateId()
                                        + " already covers this period on contract " + candidate.getContractId()
                                        + "; expire the current term before adding another"));
                    }
                    return Mono.just(candidate);
                });
    }

    private static boolean overlaps(ContractTermDynamic left, ContractTermDynamic right, LocalDateTime now) {
        LocalDateTime leftStart = left.getEffectiveDate() != null ? left.getEffectiveDate() : now;
        LocalDateTime rightStart = right.getEffectiveDate() != null ? right.getEffectiveDate() : now;
        LocalDateTime leftEnd = left.getExpirationDate() != null ? left.getExpirationDate() : LocalDateTime.MAX;
        LocalDateTime rightEnd = right.getExpirationDate() != null ? right.getExpirationDate() : LocalDateTime.MAX;
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    @Override
    public Mono<Void> deleteContractTermDynamic(UUID termId) {
        return repository.findById(termId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract term dynamic not found with ID: " + termId)))
                .flatMap(term -> repository.deleteById(termId));
    }

    @Override
    public Mono<ContractTermDynamicDTO> getContractTermDynamicById(UUID termId) {
        return repository.findById(termId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract term dynamic not found with ID: " + termId)))
                .map(mapper::toDTO);
    }
}
