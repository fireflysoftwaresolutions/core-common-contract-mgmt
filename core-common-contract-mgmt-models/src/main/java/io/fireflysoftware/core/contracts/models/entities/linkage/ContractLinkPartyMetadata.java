/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.entities.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(value="contract_party",schema="public")
public record ContractLinkPartyMetadata(@Id @Column("contract_party_id") UUID contractPartyId,
        @Column("contract_id") UUID contractId,
        @Column("party_id") UUID partyId,
        @Column("role_in_contract_id") UUID roleInContractId,
        @Column("date_joined") LocalDateTime dateJoined,
        @Column("date_left") LocalDateTime dateLeft,
        @Column("is_active") boolean active,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt) {
    @Override public String toString(){return "ContractLinkPartyMetadata[redacted]";}
}
