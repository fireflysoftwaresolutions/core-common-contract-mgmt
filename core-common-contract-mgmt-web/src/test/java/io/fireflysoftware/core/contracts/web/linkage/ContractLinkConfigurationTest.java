/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.web.read.ContractReadSettings;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ContractLinkConfigurationTest {
    final UUID tenant=UUID.randomUUID(),role=UUID.randomUUID(),binding=UUID.randomUUID();final Instant until=Instant.now().plusSeconds(60);
    final ContractReadSettings receiver=new ContractReadSettings(true,"https://issuer.invalid","contract-core","https://issuer.invalid/jwks","domain-lending","opaque-platform",tenant,UUID.randomUUID(),1,"b".repeat(64),UUID.randomUUID(),until.plusSeconds(60),false);
    @Test void absentFeatureNeverInstallsAStoreOrRequiresAnInventedBinding(){try(var context=new AnnotationConfigApplicationContext()){
        context.register(ContractLinkConfiguration.class,ContractLinkPersistenceConfiguration.class);context.refresh();assertThat(context.getBeansOfType(ContractLinkStore.class)).isEmpty();assertThat(context.getBeansOfType(ContractLinkReadStore.class)).isEmpty();assertThat(context.getBeansOfType(ContractLinkSettings.class)).isEmpty();
    }}
    @Test void disabledResourceServerCannotEnableTheManagedWriteProfile(){var environment=environment().withProperty("firefly.security.resource-server.enabled","false");assertThatThrownBy(()->new ContractLinkConfiguration().contractLinkSettings(environment,receiver)).isInstanceOf(IllegalStateException.class).hasMessageContaining("resource-server");}
    @Test void missingServerRoleCannotBecomeASampleOrCallerRole(){var missing=new MockEnvironment();for(var row:properties().entrySet())if(!row.getKey().endsWith("role-in-contract-id"))missing.setProperty(row.getKey(),row.getValue());assertThatThrownBy(()->new ContractLinkConfiguration().contractLinkSettings(missing,receiver)).isInstanceOf(IllegalStateException.class);}
    @Test void decoderAudienceAndPhysicalNamespaceMustMatchTheExistingTrust(){for(var change:List.of(Map.entry("audience","another-core"),Map.entry("resource-tenant-id",UUID.randomUUID().toString()))){var environment=environment().withProperty("contracts.initial-primary-link."+change.getKey(),change.getValue());assertThatThrownBy(()->new ContractLinkConfiguration().contractLinkSettings(environment,receiver)).isInstanceOf(IllegalStateException.class);}}
    @Test void explicitServerRoleAndFiniteBindingArePreserved(){var actual=new ContractLinkConfiguration().contractLinkSettings(environment(),receiver);assertThat(actual.roleInContractId()).isEqualTo(role);assertThat(actual.resourceTenantId()).isEqualTo(tenant);assertThat(actual.notAfter()).isEqualTo(until);}
    MockEnvironment environment(){var environment=new MockEnvironment();properties().forEach(environment::setProperty);return environment;}
    Map<String,String> properties(){String p="contracts.initial-primary-link.";return Map.ofEntries(Map.entry(p+"issuer",receiver.issuer()),Map.entry(p+"audience",receiver.audience()),Map.entry(p+"allowed-client-id",receiver.allowedClientId()),Map.entry(p+"platform-tenant",receiver.platformTenant()),Map.entry(p+"resource-tenant-id",tenant.toString()),Map.entry(p+"role-in-contract-id",role.toString()),Map.entry(p+"binding-id",binding.toString()),Map.entry(p+"binding-version","1"),Map.entry(p+"binding-digest","a".repeat(64)),Map.entry(p+"not-after",until.toString()));}
}
