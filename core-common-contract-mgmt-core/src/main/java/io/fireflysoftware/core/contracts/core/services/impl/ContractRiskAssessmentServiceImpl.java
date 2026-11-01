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
import io.fireflysoftware.core.contracts.core.mappers.ContractRiskAssessmentMapper;
import io.fireflysoftware.core.contracts.core.services.ContractRiskAssessmentService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractRiskAssessmentDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractRiskAssessment;
import io.fireflysoftware.core.contracts.models.repositories.ContractRiskAssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class ContractRiskAssessmentServiceImpl implements ContractRiskAssessmentService {

    @Autowired
    private ContractRiskAssessmentRepository repository;

    @Autowired
    private ContractRiskAssessmentMapper mapper;

    @Override
    public Mono<PaginationResponse<ContractRiskAssessmentDTO>> filterContractRiskAssessments(FilterRequest<ContractRiskAssessmentDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        ContractRiskAssessment.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractRiskAssessmentDTO> createContractRiskAssessment(ContractRiskAssessmentDTO contractRiskAssessmentDTO) {
        return Mono.just(contractRiskAssessmentDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractRiskAssessmentDTO> updateContractRiskAssessment(UUID contractRiskAssessmentId, ContractRiskAssessmentDTO contractRiskAssessmentDTO) {
        return repository.findById(contractRiskAssessmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract risk assessment not found with ID: " + contractRiskAssessmentId)))
                .flatMap(existingAssessment -> ContractScopeImpl.requireSame(
                                contractRiskAssessmentDTO.getContractId(), existingAssessment.getContractId(), existingAssessment,
                                "Contract risk assessment", contractRiskAssessmentId)
                        .flatMap(owned -> {
                            ContractRiskAssessment updatedAssessment = mapper.toEntity(contractRiskAssessmentDTO);
                            updatedAssessment.setContractRiskAssessmentId(contractRiskAssessmentId);
                            updatedAssessment.setContractId(owned.getContractId());
                            return repository.save(updatedAssessment);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContractRiskAssessment(UUID contractRiskAssessmentId) {
        return repository.findById(contractRiskAssessmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract risk assessment not found with ID: " + contractRiskAssessmentId)))
                .flatMap(assessment -> repository.deleteById(contractRiskAssessmentId));
    }

    @Override
    public Mono<ContractRiskAssessmentDTO> getContractRiskAssessmentById(UUID contractRiskAssessmentId) {
        return repository.findById(contractRiskAssessmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract risk assessment not found with ID: " + contractRiskAssessmentId)))
                .map(mapper::toDTO);
    }
}
