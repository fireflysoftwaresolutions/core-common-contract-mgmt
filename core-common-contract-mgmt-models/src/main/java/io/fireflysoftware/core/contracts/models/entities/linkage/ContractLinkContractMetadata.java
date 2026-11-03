/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.entities.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(value="contract",schema="public")
public record ContractLinkContractMetadata(@Id @Column("contract_id") UUID contractId,
        @Column("contract_number") String contractNumber,
        @Column("contract_status") ContractStatusEnum contractStatus,
        @Column("start_date") LocalDateTime startDate,
        @Column("end_date") LocalDateTime endDate,
        @Column("product_catalog_id") UUID productCatalogId,
        @Column("product_id") UUID productId,
        @Column("currency_code") String currencyCode,
        @Column("predecessor_contract_id") UUID predecessorContractId,
        @Column("link_kind") ContractLinkKind linkKind,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt) {
    @Override public String toString(){return "ContractLinkContractMetadata[redacted]";}
}
