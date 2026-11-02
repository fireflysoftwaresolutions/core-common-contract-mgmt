/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.entities.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(value="contract_status_history",schema="public")
public record ContractLinkHistoryMetadata(@Id @Column("contract_status_history_id") UUID contractStatusHistoryId,
        @Column("contract_id") UUID contractId,
        @Column("status_code") StatusCodeEnum statusCode,
        @Column("status_start_date") LocalDateTime startAt,
        @Column("status_end_date") LocalDateTime endAt,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt) {
    @Override public String toString(){return "ContractLinkHistoryMetadata[redacted]";}
}
