/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.interfaces.linkage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;
/** The signed party, role and owner namespace come from admission, never the request. */
public record ContractLinkRequest(@NotNull UUID actionId,@NotNull UUID applicationId,UUID existingContractId,
        @NotNull UUID productId,@NotNull @Pattern(regexp="[A-Z]{3}") String currencyCode) {}
