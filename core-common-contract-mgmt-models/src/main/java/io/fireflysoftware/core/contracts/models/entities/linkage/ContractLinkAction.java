/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.entities.linkage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
@Table(value="action_receipt",schema="contract_linkage")
public record ContractLinkAction(@Id @Column("action_id") UUID actionId,@Column("resource_tenant_id") UUID resourceTenantId,@Column("purpose") String purpose,
        @Column("application_id") UUID applicationId,@Column("party_id") UUID partyId,@Column("role_in_contract_id") UUID roleInContractId,
        @Column("requested_contract_id") UUID requestedContractId,@Column("contract_id") UUID contractId,@Column("contract_party_id") UUID contractPartyId,
        @Column("contract_line_id") UUID contractLineId,@Column("status_history_id") UUID statusHistoryId,@Column("contract_party_created") boolean contractPartyCreated,
        @Column("product_id") UUID productId,@Column("currency_code") String currencyCode,@Column("contract_created") boolean contractCreated,
        @Column("contract_status_at_link") String contractStatusAtLink,@Column("intent_digest") String intentDigest,@Column("effect_digest") String effectDigest,
        @Column("initial_contract_evidence") String initialContractEvidence,@Column("initial_party_evidence") String initialPartyEvidence,
        @Column("initial_line_evidence") String initialLineEvidence,@Column("initial_history_evidence") String initialHistoryEvidence,
        @Column("initial_evidence_digest") String initialEvidenceDigest,
        @Column("actor_issuer") String actorIssuer,@Column("actor_subject") String actorSubject,@Column("actor_client_id") String actorClientId,
        @Column("actor_platform_tenant_id") String actorPlatformTenantId,@Column("binding_id") UUID bindingId,@Column("binding_version") long bindingVersion,
        @Column("binding_digest") String bindingDigest,@Column("policy_revision") String policyRevision,@Column("linked_at") Instant linkedAt,
        @Column("audit_context_nonce") UUID auditContextNonce,@Column("audit_event_id") UUID auditEventId,@Column("audit_sequence") long auditSequence,@Column("audit_record_hash") String auditRecordHash){
    @Override public String toString(){return "ContractLinkAction[redacted]";}
}
