/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.interfaces.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
/** Immutable initial commit evidence: all identities/terms are at link time. Current ownership is rechecked separately; this is not a current commercial status assertion. */
public record ContractLinkReceipt(@NotNull UUID actionId,@NotNull ContractLinkPurpose purpose,@NotNull UUID applicationId,@NotNull UUID resourceTenantId,@NotNull UUID partyId,@NotNull UUID roleInContractId,
        @NotNull UUID contractId,@NotNull UUID contractPartyId,@NotNull UUID productId,@NotNull @Pattern(regexp="[A-Z]{3}") String currencyCode,boolean contractCreated,
        @NotNull ContractStatusEnum contractStatusAtLink,@NotNull @Pattern(regexp="[a-f0-9]{64}") String intentDigest,
        @NotNull @Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant linkedAt,boolean replayed) {}
