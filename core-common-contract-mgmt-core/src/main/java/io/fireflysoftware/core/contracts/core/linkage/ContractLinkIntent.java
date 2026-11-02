/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Canonical data only. Constructing an intent never grants current owner authority. */
public record ContractLinkIntent(UUID actionId,UUID applicationId,UUID resourceTenantId,UUID partyId,UUID roleInContractId,
        UUID existingContractId,UUID productId,String currencyCode) {
    public ContractLinkIntent {
        for(UUID id:Arrays.asList(actionId,applicationId,resourceTenantId,partyId,roleInContractId,productId))required(id);
        if(existingContractId!=null)required(existingContractId);
        if(currencyCode==null||!currencyCode.matches("[A-Z]{3}"))throw new IllegalArgumentException("Exact ISO currency required");
    }
    public static ContractLinkIntent of(ContractLinkRequest request,UUID namespace,UUID signedParty,UUID serverRole){
        if(request==null)throw new IllegalArgumentException("Original contract link request required");
        return new ContractLinkIntent(request.actionId(),request.applicationId(),namespace,signedParty,serverRole,request.existingContractId(),request.productId(),request.currencyCode());
    }
    public ContractLinkPurpose purpose(){return ContractLinkPurpose.INITIAL_PRIMARY_LOAN_CONTRACT;}
    public boolean createsContract(){return existingContractId==null;}
    public String digest(){try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
        out.write("firefly.contract.link.intent".getBytes(StandardCharsets.US_ASCII));out.writeByte(1);byte[] purpose=purpose().name().getBytes(StandardCharsets.US_ASCII);out.writeInt(purpose.length);out.write(purpose);
        for(UUID id:List.of(actionId,applicationId,resourceTenantId,partyId,roleInContractId,productId))uuid(out,id);
        out.writeBoolean(existingContractId!=null);if(existingContractId!=null)uuid(out,existingContractId);out.write(currencyCode.getBytes(StandardCharsets.US_ASCII));
    }return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Canonical contract intent unavailable",impossible);}}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static void required(UUID id){if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Nonzero contract identity required");}
}
