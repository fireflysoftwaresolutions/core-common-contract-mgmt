/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fireflysoftware.core.contracts.models.entities.linkage.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.lang.reflect.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Column;

/** Complete immutable initial row evidence. Live commercial rows remain owned mutable state. */
public record ContractLinkInitialEvidence(String contract,String party,String line,String history,String digest){
    private static final ObjectMapper JSON=new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public ContractLinkInitialEvidence{for(String value:List.of(contract,party,line,history))if(value.isEmpty()||value.getBytes(StandardCharsets.UTF_8).length>16384)throw new IllegalArgumentException("Bounded complete original contract evidence required");if(!hash(contract,party,line,history).equals(digest))throw new IllegalArgumentException("Exact original contract evidence digest required");}
    public static ContractLinkInitialEvidence capture(ContractLinkContractMetadata contract,ContractLinkPartyMetadata party,ContractLinkLineMetadata line,ContractLinkHistoryMetadata history){String c=encode(contract),p=encode(party),l=encode(line),h=encode(history);return new ContractLinkInitialEvidence(c,p,l,h,hash(c,p,l,h));}
    public static ContractLinkInitialEvidence retained(ContractLinkAction row){return new ContractLinkInitialEvidence(row.initialContractEvidence(),row.initialPartyEvidence(),row.initialLineEvidence(),row.initialHistoryEvidence(),row.initialEvidenceDigest());}
    public ContractLinkContractMetadata contractRow(){return decode(contract,ContractLinkContractMetadata.class);}
    public ContractLinkPartyMetadata partyRow(){return decode(party,ContractLinkPartyMetadata.class);}
    public ContractLinkLineMetadata lineRow(){return decode(line,ContractLinkLineMetadata.class);}
    public ContractLinkHistoryMetadata historyRow(){return decode(history,ContractLinkHistoryMetadata.class);}
    private static String encode(Record record){try{var fields=new TreeMap<String,Object>();for(var component:record.getClass().getRecordComponents()){var column=record.getClass().getDeclaredField(component.getName()).getAnnotation(Column.class);if(column==null||column.value().isBlank()||fields.containsKey(column.value()))throw new IllegalArgumentException("Exact mapped original row shape required");fields.put(column.value(),component.getAccessor().invoke(record));}return JSON.writeValueAsString(fields);}catch(ReflectiveOperationException|IOException error){throw new IllegalArgumentException("Original mapped contract evidence unavailable",error);}}
    private static <T extends Record> T decode(String raw,Class<T> type){try{
        Map<String,Object> fields=JSON.readValue(raw,new TypeReference<TreeMap<String,Object>>(){});var components=type.getRecordComponents();var names=new HashSet<String>();var types=new Class<?>[components.length];var values=new Object[components.length];
        for(int index=0;index<components.length;index++){var component=components[index];var column=type.getDeclaredField(component.getName()).getAnnotation(Column.class);if(column==null||!names.add(column.value())||!fields.containsKey(column.value()))throw new IllegalArgumentException("Complete mapped initial evidence fields required");types[index]=component.getType();Object value=fields.get(column.value());if(value==null&&types[index].isPrimitive())throw new IllegalArgumentException("Original primitive evidence required");values[index]=value==null?null:JSON.convertValue(value,types[index]);}
        if(!names.equals(fields.keySet()))throw new IllegalArgumentException("No unowned initial evidence field permitted");return type.getDeclaredConstructor(types).newInstance(values);
    }catch(ReflectiveOperationException|IOException error){throw new IllegalArgumentException("Original typed contract evidence unavailable",error);}}
    public static String hash(String... values){try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.write("firefly.contract.initial-evidence".getBytes(StandardCharsets.US_ASCII));out.writeByte(1);for(String value:values){byte[] raw=value.getBytes(StandardCharsets.UTF_8);out.writeInt(raw.length);out.write(raw);}}return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException("Initial contract evidence hash unavailable",impossible);}}
    @Override public String toString(){return "ContractLinkInitialEvidence[redacted]";}
}
