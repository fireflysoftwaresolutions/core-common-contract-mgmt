/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ContractLinkFailure extends BusinessException {
    public enum Outcome{REFUSED,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE}
    private final UUID actionId;private final Outcome outcome;
    public ContractLinkFailure(UUID actionId,Outcome outcome){super(HttpStatus.SERVICE_UNAVAILABLE,"CONTRACT_LINK_"+Objects.requireNonNull(outcome).name(),"Original contract link response unavailable",Map.of("actionId",Objects.requireNonNull(actionId).toString(),"outcome",outcome.name(),"recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.actionId=actionId;this.outcome=outcome;}
    public UUID actionId(){return actionId;}public Outcome outcome(){return outcome;}
}
