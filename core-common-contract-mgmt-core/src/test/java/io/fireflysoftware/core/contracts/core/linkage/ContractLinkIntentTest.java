/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkRequest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ContractLinkIntentTest {
    final UUID action=UUID.randomUUID(),app=UUID.randomUUID(),tenant=UUID.randomUUID(),party=UUID.randomUUID(),role=UUID.randomUUID(),product=UUID.randomUUID();
    ContractLinkRequest request(UUID existing){return new ContractLinkRequest(action,app,existing,product,"EUR");}
    @Test void purposeIsTheInitialPrimaryLoanBoundaryRatherThanEveryContractType(){assertThat(ContractLinkIntent.of(request(null),tenant,party,role).purpose().name()).isEqualTo("INITIAL_PRIMARY_LOAN_CONTRACT");}
    @Test void sameOriginalActionAndCompleteOwnerIntentHasStableDigest(){var first=ContractLinkIntent.of(request(null),tenant,party,role);var second=ContractLinkIntent.of(request(null),tenant,party,role);assertThat(first.digest()).matches("[a-f0-9]{64}").isEqualTo(second.digest());}
    @Test void actualExistingContractCannotBecomeAnInventedNewContractOnReplay(){var draft=ContractLinkIntent.of(request(null),tenant,party,role);var existing=ContractLinkIntent.of(request(UUID.randomUUID()),tenant,party,role);assertThat(draft.createsContract()).isTrue();assertThat(existing.createsContract()).isFalse();assertThat(existing.digest()).isNotEqualTo(draft.digest());}
    @Test void everyActionApplicationPartyProductRoleAndNamespaceDifferenceConflicts(){var original=ContractLinkIntent.of(request(null),tenant,party,role).digest();for(var changed:List.of(
            ContractLinkIntent.of(new ContractLinkRequest(UUID.randomUUID(),app,null,product,"EUR"),tenant,party,role),
            ContractLinkIntent.of(new ContractLinkRequest(action,UUID.randomUUID(),null,product,"EUR"),tenant,party,role),
            ContractLinkIntent.of(new ContractLinkRequest(action,app,null,UUID.randomUUID(),"EUR"),tenant,party,role),
            ContractLinkIntent.of(new ContractLinkRequest(action,app,null,product,"USD"),tenant,party,role),
            ContractLinkIntent.of(request(null),UUID.randomUUID(),party,role),ContractLinkIntent.of(request(null),tenant,UUID.randomUUID(),role),ContractLinkIntent.of(request(null),tenant,party,UUID.randomUUID())))assertThat(changed.digest()).isNotEqualTo(original);}
    @Test void missingOrZeroServerRolePartyNamespaceAndBusinessIdentityNeverDefaults(){UUID zero=new UUID(0,0);for(UUID invalid:Arrays.asList(null,zero)){
        assertThatThrownBy(()->ContractLinkIntent.of(request(null),invalid,party,role)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ContractLinkIntent.of(request(null),tenant,invalid,role)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ContractLinkIntent.of(request(null),tenant,party,invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ContractLinkIntent.of(new ContractLinkRequest(invalid,app,null,product,"EUR"),tenant,party,role)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ContractLinkIntent.of(new ContractLinkRequest(action,invalid,null,product,"EUR"),tenant,party,role)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ContractLinkIntent.of(new ContractLinkRequest(action,app,null,invalid,"EUR"),tenant,party,role)).isInstanceOf(IllegalArgumentException.class);
    }assertThatThrownBy(()->ContractLinkIntent.of(request(zero),tenant,party,role)).isInstanceOf(IllegalArgumentException.class);}
    @Test void currencyAndRequestAreRequiredWithoutSilentNormalization(){for(String invalid:Arrays.asList(null,"","eur","EURO"," EUR","EUR\n"))assertThatThrownBy(()->ContractLinkIntent.of(new ContractLinkRequest(action,app,null,product,invalid),tenant,party,role)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->ContractLinkIntent.of(null,tenant,party,role)).isInstanceOf(IllegalArgumentException.class);}
}
