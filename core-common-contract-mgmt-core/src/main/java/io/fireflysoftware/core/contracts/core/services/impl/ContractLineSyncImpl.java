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

import io.fireflysoftware.core.contracts.models.entities.Contract;
import io.fireflysoftware.core.contracts.models.entities.ContractLine;
import io.fireflysoftware.core.contracts.models.repositories.ContractLineRepository;
import io.fireflysoftware.core.contracts.models.repositories.ContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Keeps contract.product_id equal to the primary line, or the first line when none is primary.
 */
@Component("contractLineSync")
public class ContractLineSyncImpl {

    @Autowired
    private ContractLineRepository lines;

    @Autowired
    private ContractRepository contracts;

    public Mono<Contract> alignPrimaryLine(Contract contract) {
        if (contract.getProductId() == null || contract.getContractId() == null) {
            return Mono.just(contract);
        }
        return lines.findByContractId(contract.getContractId())
                .collectList()
                .flatMap(existing -> {
                    ContractLine primary = primaryOf(existing);
                    if (primary != null) {
                        if (contract.getProductId().equals(primary.getProductId())) {
                            return Mono.just(contract);
                        }
                        primary.setProductId(contract.getProductId());
                        return lines.save(primary).thenReturn(contract);
                    }
                    if (!existing.isEmpty()) {
                        ContractLine first = existing.get(0);
                        first.setIsPrimary(true);
                        first.setProductId(contract.getProductId());
                        return lines.save(first).thenReturn(contract);
                    }
                    ContractLine created = ContractLine.builder()
                            .contractId(contract.getContractId())
                            .productId(contract.getProductId())
                            .isPrimary(true)
                            .build();
                    return lines.save(created).thenReturn(contract);
                });
    }

    public Mono<Contract> mirrorHeader(UUID contractId) {
        return contracts.findById(contractId)
                .switchIfEmpty(Mono.error(new RuntimeException("Contract not found with ID: " + contractId)))
                .flatMap(contract -> lines.findByContractId(contractId)
                        .collectList()
                        .flatMap(existing -> {
                            ContractLine primary = primaryOf(existing);
                            if (primary == null && !existing.isEmpty()) {
                                ContractLine first = existing.get(0);
                                first.setIsPrimary(true);
                                return lines.save(first)
                                        .then(saveHeader(contract, first.getProductId()));
                            }
                            UUID productId = primary == null ? null : primary.getProductId();
                            if (sameProduct(contract.getProductId(), productId)) {
                                return Mono.just(contract);
                            }
                            return saveHeader(contract, productId);
                        }));
    }

    private Mono<Contract> saveHeader(Contract contract, UUID productId) {
        contract.setProductId(productId);
        return contracts.save(contract);
    }

    private static ContractLine primaryOf(List<ContractLine> existing) {
        return existing.stream()
                .filter(line -> Boolean.TRUE.equals(line.getIsPrimary()))
                .findFirst()
                .orElse(null);
    }

    private static boolean sameProduct(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }
}
