/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.entities.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(value="contract_line",schema="public")
public record ContractLinkLineMetadata(@Id @Column("contract_line_id") UUID contractLineId,
        @Column("contract_id") UUID contractId,
        @Column("product_id") UUID productId,
        @Column("line_role") String lineRole,
        @Column("quantity") BigDecimal quantity,
        @Column("effective_from") LocalDateTime effectiveFrom,
        @Column("effective_to") LocalDateTime effectiveTo,
        @Column("is_primary") boolean primary,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt) {
    @Override public String toString(){return "ContractLinkLineMetadata[redacted]";}
}
