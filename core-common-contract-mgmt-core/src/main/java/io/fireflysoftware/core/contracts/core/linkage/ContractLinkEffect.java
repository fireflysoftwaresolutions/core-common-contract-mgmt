/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
public final class ContractLinkEffect {
    private ContractLinkEffect(){}
    public static String digest(ContractLinkIntent intent,ContractLinkAdmission.Permit permit,UUID contract,UUID party,UUID line,UUID history,
            boolean contractCreated,boolean partyCreated,ContractStatusEnum status,Instant linkedAt,String initialEvidenceDigest){return digest(intent,permit.actor(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest(),permit.policyRevision(),contract,party,line,history,contractCreated,partyCreated,status,linkedAt,initialEvidenceDigest);}
    public static String digest(ContractLinkIntent intent,org.fireflyframework.kernel.audit.AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,UUID contract,UUID party,UUID line,UUID history,
            boolean contractCreated,boolean partyCreated,ContractStatusEnum status,Instant linkedAt,String initialEvidenceDigest){try{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.write("firefly.contract.link.effect".getBytes(StandardCharsets.US_ASCII));out.writeByte(2);out.write(HexFormat.of().parseHex(intent.digest()));out.write(HexFormat.of().parseHex(initialEvidenceDigest));
            for(UUID id:List.of(contract,party,line,history)){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
            out.writeBoolean(contractCreated);out.writeBoolean(partyCreated);text(out,status.name());out.writeLong(linkedAt.getEpochSecond());out.writeInt(linkedAt.getNano());
            text(out,actor.issuer());text(out,actor.subject());text(out,actor.clientId());text(out,actor.platformTenantId());
            out.writeLong(bindingId.getMostSignificantBits());out.writeLong(bindingId.getLeastSignificantBits());out.writeLong(bindingVersion);out.write(HexFormat.of().parseHex(bindingDigest));text(out,policyRevision);
        }return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Canonical contract effect unavailable",impossible);}}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
}
