/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.interfaces.linkage;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ContractLinkError(@NotNull UUID actionId,@NotNull String code,@NotNull Outcome outcome,@NotNull String recovery){
    public enum Outcome {REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE}
}
