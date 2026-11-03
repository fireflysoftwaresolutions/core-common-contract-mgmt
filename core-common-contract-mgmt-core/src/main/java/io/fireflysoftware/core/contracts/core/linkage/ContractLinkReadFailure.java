/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ContractLinkReadFailure extends BusinessException {
    private final UUID actionId;
    public ContractLinkReadFailure(UUID action){super(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_LINK_OBSERVATION_UNAVAILABLE","Original contract link observation unavailable",Map.of("actionId",Objects.requireNonNull(action).toString(),"outcome","OBSERVATION_UNAVAILABLE","recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.actionId=action;}
    public UUID actionId(){return actionId;}
}
