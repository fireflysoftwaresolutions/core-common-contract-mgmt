/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ContractLinkConflict extends BusinessException {
    private final UUID actionId;
    public ContractLinkConflict(UUID actionId){this(actionId,"CONTRACT_LINK_ACTION_CONFLICT","Original contract action already has different immutable intent");}
    private ContractLinkConflict(UUID actionId,String code,String message){super(HttpStatus.CONFLICT,code,message,Map.of("actionId",Objects.requireNonNull(actionId).toString(),"purpose","INITIAL_PRIMARY_LOAN_CONTRACT","outcome","CONFLICT","recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.actionId=actionId;}
    public static ContractLinkConflict initialPrimaryApplication(UUID actionId){return new ContractLinkConflict(actionId,"CONTRACT_LINK_INITIAL_PRIMARY_ALREADY_BOUND","The initial primary loan contract link is already retained; recover the original action instead of creating another contract");}
    public UUID actionId(){return actionId;}
}
