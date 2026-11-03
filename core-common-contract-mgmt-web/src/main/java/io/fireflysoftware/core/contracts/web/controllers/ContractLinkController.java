/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.controllers;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/initial-primary-loan-contract-links")
@SecurityRequirement(name="contractReadBearer")
public class ContractLinkController {
    private final ObjectProvider<ContractLinkAdmission> writes;private final ObjectProvider<ContractLinkStore> stores;
    private final ObjectProvider<ContractLinkReadAdmission> reads;private final ObjectProvider<ContractLinkReadStore> observers;
    public ContractLinkController(ObjectProvider<ContractLinkAdmission> writes,ObjectProvider<ContractLinkStore> stores,ObjectProvider<ContractLinkReadAdmission> reads,ObjectProvider<ContractLinkReadStore> observers){this.writes=writes;this.stores=stores;this.reads=reads;this.observers=observers;}
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="linkInitialPrimaryLoanContract",summary="Create or link one initial primary loan contract under the original action and current signed party authority")
    @ApiResponses({@ApiResponse(responseCode="201",description="Actual initial primary contract graph linked and canonical pair committed",content=@Content(schema=@Schema(implementation=ContractLinkReceipt.class))),
        @ApiResponse(responseCode="200",description="Same original action and complete retained intent replayed",content=@Content(schema=@Schema(implementation=ContractLinkReceipt.class))),
        @ApiResponse(responseCode="400",description="Invalid or mismatched original action/application/product/currency",content=@Content),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current party or owner policy refused",content=@Content),
        @ApiResponse(responseCode="409",description="Changed original intent or another initial-primary action already retained for this application",content=@Content(schema=@Schema(implementation=ContractLinkError.class))),
        @ApiResponse(responseCode="503",description="REFUSED before dispatch or after proven rollback, OUTCOME_UNKNOWN after uncertain completion, or COMMITTED_RESPONSE_UNAVAILABLE; preserve the original action",content=@Content(schema=@Schema(implementation=ContractLinkError.class)))})
    public Mono<ResponseEntity<ContractLinkReceipt>> link(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody ContractLinkRequest request){return Mono.defer(()->{
        if(request==null||!applicationId.equals(request.applicationId())||!actionId.equals(request.actionId()))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact original application and action required"));
        final ContractLinkAdmission authority;final ContractLinkStore owner;
        try{authority=writes.getIfUnique();owner=stores.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(new ContractLinkFailure(actionId,ContractLinkFailure.Outcome.REFUSED));}
        if(authority==null||owner==null)return Mono.error(new ContractLinkFailure(actionId,ContractLinkFailure.Outcome.REFUSED));
        return authority.invoke(request,owner).map(value->ResponseEntity.status(value.replayed()?HttpStatus.OK:HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(value)).onErrorMap(error->{
            if(error instanceof ContractLinkFailure||error instanceof ContractLinkConflict)return error;
            if(error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;
            if(error instanceof BusinessException business&&business.getStatus()==HttpStatus.BAD_REQUEST)return error;
            return new ContractLinkFailure(actionId,ContractLinkFailure.Outcome.OUTCOME_UNKNOWN);
        });
    });}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeInitialPrimaryLoanContract",summary="Observe the original initial-primary link and completed canonical pair under current signed read authority")
    @ApiResponses({@ApiResponse(responseCode="200",description="Existing immutable original link and completed canonical pair verified",content=@Content(schema=@Schema(implementation=ContractLinkReceipt.class))),
        @ApiResponse(responseCode="204",description="No matching original action observed in this current authorized snapshot; this is not proof that an in-flight write cannot commit",content=@Content),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current observation/application/party authority refused",content=@Content),
        @ApiResponse(responseCode="503",description="Current observation unavailable; no contract or replacement action was dispatched",content=@Content(schema=@Schema(implementation=ContractLinkError.class)))})
    public Mono<ResponseEntity<ContractLinkReceipt>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return Mono.defer(()->{
        final ContractLinkReadAdmission authority;final ContractLinkReadStore owner;
        try{authority=reads.getIfUnique();owner=observers.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(new ContractLinkReadFailure(actionId));}
        if(authority==null||owner==null)return Mono.error(new ContractLinkReadFailure(actionId));
        return authority.invoke(applicationId,actionId,owner::read).map(value->value.isPresent()?ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value.orElseThrow()):ResponseEntity.noContent().cacheControl(CacheControl.noStore()).<ContractLinkReceipt>build()).onErrorMap(error->{
            if(error instanceof ContractLinkReadFailure)return error;if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return new ContractLinkReadFailure(actionId);
        });
    });}
    @ExceptionHandler(ContractLinkFailure.class)
    public ResponseEntity<ContractLinkError> unavailable(ContractLinkFailure error){return error(HttpStatus.SERVICE_UNAVAILABLE,error.actionId(),error.getCode(),ContractLinkError.Outcome.valueOf(error.outcome().name()));}
    @ExceptionHandler(ContractLinkReadFailure.class)
    public ResponseEntity<ContractLinkError> observationUnavailable(ContractLinkReadFailure error){return error(HttpStatus.SERVICE_UNAVAILABLE,error.actionId(),error.getCode(),ContractLinkError.Outcome.OBSERVATION_UNAVAILABLE);}
    @ExceptionHandler(ContractLinkConflict.class)
    public ResponseEntity<ContractLinkError> conflict(ContractLinkConflict error){return error(HttpStatus.CONFLICT,error.actionId(),error.getCode(),ContractLinkError.Outcome.CONFLICT);}
    private static ResponseEntity<ContractLinkError> error(HttpStatus status,UUID action,String code,ContractLinkError.Outcome outcome){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ContractLinkError(action,code,outcome,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}
