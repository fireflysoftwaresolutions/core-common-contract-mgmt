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
import io.fireflysoftware.core.contracts.core.mappers.ContractMapper;
import io.fireflysoftware.core.contracts.core.services.ContractService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractDTO;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractLinkKind;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.models.entities.Contract;
import io.fireflysoftware.core.contracts.models.entities.ContractStatusHistory;
import io.fireflysoftware.core.contracts.models.repositories.ContractRepository;
import io.fireflysoftware.core.contracts.models.repositories.ContractStatusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class ContractServiceImpl implements ContractService {

    @Autowired
    private ContractRepository repository;

    @Autowired
    private ContractMapper mapper;

    @Autowired
    private ContractStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ContractLineSyncImpl lineSync;

    @Override
    public Mono<PaginationResponse<ContractDTO>> filterContracts(FilterRequest<ContractDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Contract.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ContractDTO> createContract(ContractDTO contractDTO) {
        return Mono.just(contractDTO)
                .map(mapper::toEntity)
                .flatMap(contract -> {
                    if (contract.getContractStatus() == null) {
                        contract.setContractStatus(ContractStatusEnum.DRAFT);
                    }
                    return requireCurrencyAndLink(contract.getContractId(), contract)
                            .then(Mono.defer(() -> repository.save(contract)
                                    .flatMap(saved -> alignProduct(saved).flatMap(this::recordStatus))));
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractDTO> updateContract(UUID contractId, ContractDTO contractDTO) {
        return repository.findById(contractId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract not found with ID: " + contractId)))
                .flatMap(existingContract -> {
                    Contract updatedContract = mapper.toEntity(contractDTO);
                    updatedContract.setContractId(contractId);
                    ContractStatusEnum current = existingContract.getContractStatus() != null
                            ? existingContract.getContractStatus()
                            : ContractStatusEnum.DRAFT;
                    if (updatedContract.getContractStatus() == null) {
                        updatedContract.setContractStatus(current);
                    }
                    if (updatedContract.getContractStatus() != current
                            && !ContractLifecycleImpl.canTransition(current, updatedContract.getContractStatus())) {
                        return Mono.error(new RuntimeException(
                                "Contract " + contractId + " cannot move from " + current
                                        + " to " + updatedContract.getContractStatus()));
                    }
                    boolean statusChanged = updatedContract.getContractStatus() != current;
                    return requireCurrencyAndLink(contractId, updatedContract)
                            .then(Mono.defer(() -> repository.save(updatedContract)
                                    .flatMap(this::alignProduct)
                                    .flatMap(saved -> statusChanged ? recordStatus(saved) : Mono.just(saved))));
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContract(UUID contractId) {
        return repository.findById(contractId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract not found with ID: " + contractId)))
                .flatMap(contract -> {
                    ContractStatusEnum status = contract.getContractStatus() != null
                            ? contract.getContractStatus()
                            : ContractStatusEnum.DRAFT;
                    if (status != ContractStatusEnum.DRAFT) {
                        return Mono.error(new RuntimeException(
                                "Contract " + contractId + " is " + status
                                        + " and cannot be deleted; terminate or cancel it so the commercial history is kept"));
                    }
                    return repository.deleteById(contractId);
                });
    }

    private Mono<Contract> alignProduct(Contract saved) {
        if (saved.getProductId() == null) {
            return Mono.just(saved);
        }
        return lineSync.alignPrimaryLine(saved);
    }

    private Mono<Void> requireCurrencyAndLink(UUID selfId, Contract contract) {
        if (contract.getCurrencyCode() != null && !contract.getCurrencyCode().matches("^[A-Z]{3}$")) {
            return Mono.error(new RuntimeException("Currency must be a 3-letter ISO code"));
        }
        UUID predecessor = contract.getPredecessorContractId();
        ContractLinkKind kind = contract.getLinkKind();
        if (predecessor == null && kind == null) {
            return Mono.empty();
        }
        if (predecessor == null || kind == null) {
            return Mono.error(new RuntimeException(
                    "A predecessor contract and a link kind of AMENDMENT or RENEWAL are both required"));
        }
        if (predecessor.equals(selfId) || predecessor.equals(contract.getContractId())) {
            return Mono.error(new RuntimeException("A contract cannot link to itself"));
        }
        return repository.findById(predecessor)
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Predecessor contract not found with ID: " + predecessor)))
                .then();
    }

    private Mono<Contract> recordStatus(Contract contract) {
        LocalDateTime now = LocalDateTime.now();
        ContractStatusHistory next = ContractStatusHistory.builder()
                .contractId(contract.getContractId())
                .statusCode(ContractLifecycleImpl.historyCode(contract.getContractStatus()))
                .statusStartDate(now)
                .build();
        return statusHistoryRepository.findByContractIdAndStatusEndDateIsNull(contract.getContractId())
                .flatMap(open -> {
                    open.setStatusEndDate(now);
                    return statusHistoryRepository.save(open);
                })
                .then(statusHistoryRepository.save(next))
                .thenReturn(contract);
    }

    @Override
    public Mono<ContractDTO> getContractById(UUID contractId) {
        return repository.findById(contractId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract not found with ID: " + contractId)))
                .map(mapper::toDTO);
    }
}
