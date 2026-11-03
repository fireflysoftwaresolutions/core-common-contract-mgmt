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

import io.fireflysoftware.core.contracts.core.mappers.ContractLineMapper;
import io.fireflysoftware.core.contracts.core.services.ContractLineService;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractLineDTO;
import io.fireflysoftware.core.contracts.models.entities.ContractLine;
import io.fireflysoftware.core.contracts.models.repositories.ContractLineRepository;
import io.fireflysoftware.core.contracts.models.repositories.ContractRepository;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ContractLineServiceImpl implements ContractLineService {

    @Autowired
    private ContractLineRepository lines;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private ContractLineMapper mapper;

    @Autowired
    private ContractLineSyncImpl sync;

    @Override
    public Mono<PaginationResponse<ContractLineDTO>> filterContractLines(FilterRequest<ContractLineDTO> filterRequest) {
        return FilterUtils.createFilter(ContractLine.class, mapper::toDTO).filter(filterRequest);
    }

    @Override
    public Mono<ContractLineDTO> createContractLine(ContractLineDTO dto) {
        if (dto.getContractId() == null) {
            return Mono.error(new RuntimeException("Contract ID is required"));
        }
        String dates = dateError(dto);
        if (dates != null) {
            return Mono.error(new RuntimeException(dates));
        }
        return contracts.findById(dto.getContractId())
                .switchIfEmpty(Mono.error(new RuntimeException("Contract not found with ID: " + dto.getContractId())))
                .flatMap(contract -> lines.findByContractId(contract.getContractId()).collectList()
                        .flatMap(existing -> {
                            boolean primary = Boolean.TRUE.equals(dto.getIsPrimary()) || existing.isEmpty();
                            dto.setIsPrimary(primary);
                            return clearPrimary(existing, primary)
                                    .then(Mono.fromSupplier(() -> mapper.toEntity(dto)))
                                    .flatMap(lines::save)
                                    .flatMap(saved -> primary
                                            ? sync.mirrorHeader(saved.getContractId()).thenReturn(saved)
                                            : Mono.just(saved));
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ContractLineDTO> updateContractLine(UUID contractLineId, ContractLineDTO dto) {
        String dates = dateError(dto);
        if (dates != null) {
            return Mono.error(new RuntimeException(dates));
        }
        return lines.findById(contractLineId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract line not found with ID: " + contractLineId)))
                .flatMap(existing -> ContractScopeImpl.requireSame(
                                dto.getContractId(), existing.getContractId(), existing, "Contract line", contractLineId)
                        .flatMap(owned -> lines.findByContractId(owned.getContractId()).collectList()
                                .flatMap(existingLines -> {
                                    boolean onlyLine = existingLines.size() <= 1;
                                    boolean primary = onlyLine || (dto.getIsPrimary() == null
                                            ? Boolean.TRUE.equals(owned.getIsPrimary())
                                            : Boolean.TRUE.equals(dto.getIsPrimary()));
                                    return clearPrimary(existingLines, primary)
                                            .then(Mono.fromSupplier(() -> {
                                                ContractLine updated = mapper.toEntity(dto);
                                                updated.setContractLineId(contractLineId);
                                                updated.setContractId(owned.getContractId());
                                                updated.setIsPrimary(primary);
                                                return updated;
                                            }))
                                            .flatMap(lines::save)
                                            .flatMap(saved -> sync.mirrorHeader(saved.getContractId()).thenReturn(saved));
                                })))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteContractLine(UUID contractLineId) {
        return lines.findById(contractLineId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract line not found with ID: " + contractLineId)))
                .flatMap(line -> lines.delete(line)
                        .then(sync.mirrorHeader(line.getContractId()))
                        .then());
    }

    @Override
    public Mono<ContractLineDTO> getContractLineById(UUID contractLineId) {
        return lines.findById(contractLineId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract line not found with ID: " + contractLineId)))
                .map(mapper::toDTO);
    }

    private Mono<Void> clearPrimary(List<ContractLine> existing, boolean primary) {
        if (!primary) {
            return Mono.empty();
        }
        return Flux.fromIterable(existing)
                .filter(line -> Boolean.TRUE.equals(line.getIsPrimary()))
                .flatMap(line -> {
                    line.setIsPrimary(false);
                    return lines.save(line);
                })
                .then();
    }

    private static String dateError(ContractLineDTO dto) {
        if (dto.getEffectiveFrom() != null && dto.getEffectiveTo() != null
                && dto.getEffectiveTo().isBefore(dto.getEffectiveFrom())) {
            return "Effective to must not be before effective from";
        }
        return null;
    }
}
