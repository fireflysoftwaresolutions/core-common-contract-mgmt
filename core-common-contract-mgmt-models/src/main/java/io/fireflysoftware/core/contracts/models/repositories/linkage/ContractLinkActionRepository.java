/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.repositories.linkage;
import io.fireflysoftware.core.contracts.models.entities.linkage.ContractLinkAction;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
@NoRepositoryBean
public interface ContractLinkActionRepository extends ReactiveCrudRepository<ContractLinkAction,UUID>{
    Flux<ContractLinkAction> findAllByResourceTenantIdAndActionId(UUID resourceTenantId,UUID actionId);
    Flux<ContractLinkAction> findAllByResourceTenantIdAndApplicationIdAndPartyIdAndActionId(UUID resourceTenantId,UUID applicationId,UUID partyId,UUID actionId);
    Flux<ContractLinkAction> findAllByResourceTenantIdAndApplicationIdAndPurpose(UUID resourceTenantId,UUID applicationId,String purpose);
    @Query("SELECT TRUE AS locked FROM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('contract-link-initial-primary-application:' || CAST(:tenant AS text) || ':' || CAST(:application AS text),0))")
    Mono<Boolean> lockInitialPrimaryApplication(UUID tenant,UUID application);
    @Query("SELECT TRUE AS locked FROM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('contract-link-action:' || CAST(:tenant AS text) || ':' || CAST(:action AS text),0))")
    Mono<Boolean> lockAction(UUID tenant,UUID action);
}
