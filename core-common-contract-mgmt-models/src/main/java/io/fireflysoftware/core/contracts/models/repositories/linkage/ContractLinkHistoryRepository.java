/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.repositories.linkage;
import io.fireflysoftware.core.contracts.models.entities.linkage.ContractLinkHistoryMetadata;
import java.util.UUID;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
@NoRepositoryBean
public interface ContractLinkHistoryRepository extends ReactiveCrudRepository<ContractLinkHistoryMetadata,UUID>{
    @Lock(LockMode.PESSIMISTIC_WRITE) Flux<ContractLinkHistoryMetadata> findAllByContractIdAndEndAtIsNull(UUID contractId);
}
