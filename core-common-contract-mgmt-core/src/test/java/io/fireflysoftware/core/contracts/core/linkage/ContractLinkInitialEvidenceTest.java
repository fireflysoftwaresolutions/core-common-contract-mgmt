/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import io.fireflysoftware.core.contracts.models.entities.linkage.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContractLinkInitialEvidenceTest {
    final UUID contractId=UUID.randomUUID(),partyId=UUID.randomUUID(),role=UUID.randomUUID(),product=UUID.randomUUID();
    final LocalDateTime at=LocalDateTime.parse("2026-11-02T12:00:00.123456");
    final ContractLinkContractMetadata contract=new ContractLinkContractMetadata(contractId,null,ContractStatusEnum.DRAFT,null,null,null,product,"EUR",null,null,at,at);
    final ContractLinkPartyMetadata party=new ContractLinkPartyMetadata(UUID.randomUUID(),contractId,partyId,role,at,null,true,at,at);
    final ContractLinkLineMetadata line=new ContractLinkLineMetadata(UUID.randomUUID(),contractId,product,"owned original role",new BigDecimal("1.2500000000000000001"),at,null,true,at,at);
    final ContractLinkHistoryMetadata history=new ContractLinkHistoryMetadata(UUID.randomUUID(),contractId,StatusCodeEnum.CREATED,at,null,at,at);
    ContractLinkInitialEvidence initial(){return ContractLinkInitialEvidence.capture(contract,party,line,history);}
    ContractLinkInitialEvidence withContract(String raw){var prior=initial();return new ContractLinkInitialEvidence(raw,prior.party(),prior.line(),prior.history(),ContractLinkInitialEvidence.hash(raw,prior.party(),prior.line(),prior.history()));}
    @Test void completeMappedRowsRoundTripWithoutInventedNullsOrLostDecimalPrecision(){var initial=initial();assertThat(initial.contractRow()).isEqualTo(contract);assertThat(initial.partyRow()).isEqualTo(party);assertThat(initial.lineRow()).isEqualTo(line);assertThat(initial.historyRow()).isEqualTo(history);assertThat(initial.contract()).contains("\"contract_number\":null");assertThat(initial.digest()).matches("[a-f0-9]{64}").isEqualTo(initial().digest());}
    @Test void laterCommercialStatusAndHistoryDoNotRewriteInitialEvidence(){var initial=initial();var later=new ContractLinkContractMetadata(contractId,null,ContractStatusEnum.ACTIVE,null,null,null,product,"EUR",null,null,at,at.plusSeconds(1));var closed=new ContractLinkHistoryMetadata(history.contractStatusHistoryId(),contractId,StatusCodeEnum.CREATED,at,at.plusSeconds(1),at,at.plusSeconds(1));assertThat(ContractLinkInitialEvidence.capture(later,party,line,closed).digest()).isNotEqualTo(initial.digest());assertThat(initial.contractRow().contractStatus()).isEqualTo(ContractStatusEnum.DRAFT);assertThat(initial.historyRow().endAt()).isNull();}
    @Test void duplicateTrailingUnknownAndMissingEvidenceFieldsAreRejected(){var initial=initial();for(String raw:List.of(initial.contract().replace("\"currency_code\":\"EUR\"","\"currency_code\":\"EUR\",\"currency_code\":\"EUR\""),initial.contract()+" {}",initial.contract().replaceFirst("\\{","{\"unowned\":null,"),initial.contract().replace("\"contract_number\":null,",""))){assertThatThrownBy(()->withContract(raw).contractRow()).isInstanceOf(IllegalArgumentException.class);}}
    @Test void unchangedDigestCannotHideAChangedRowOrEvidenceFieldBoundary(){var initial=initial();assertThatThrownBy(()->new ContractLinkInitialEvidence(initial.contract().replace("DRAFT","ACTIVE"),initial.party(),initial.line(),initial.history(),initial.digest())).isInstanceOf(IllegalArgumentException.class);assertThat(ContractLinkInitialEvidence.hash("ab","c","d","e")).isNotEqualTo(ContractLinkInitialEvidence.hash("a","bc","d","e"));assertThat(initial.toString()).doesNotContain(contractId.toString(),partyId.toString(),initial.contract());}
}
