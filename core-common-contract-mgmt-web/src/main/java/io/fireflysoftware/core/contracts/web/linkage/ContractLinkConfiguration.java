/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage;
import io.fireflysoftware.core.contracts.core.linkage.*;
import io.fireflysoftware.core.contracts.core.linkage.impl.*;
import io.fireflysoftware.core.contracts.models.config.ContractLinkMapping;
import io.fireflysoftware.core.contracts.web.linkage.impl.*;
import io.fireflysoftware.core.contracts.web.read.ContractReadSettings;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="contracts.initial-primary-link.enabled",havingValue="true")
public class ContractLinkConfiguration {
    @Bean ContractLinkSettings contractLinkSettings(Environment env,ContractReadSettings read){String p="contracts.initial-primary-link.";
        if(!env.getProperty("firefly.security.resource-server.enabled",Boolean.class,true))throw new IllegalStateException("Managed contract linkage requires the resource-server receiver");
        var settings=new ContractLinkSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"audience"),env.getRequiredProperty(p+"allowed-client-id"),env.getRequiredProperty(p+"platform-tenant"),
            UUID.fromString(env.getRequiredProperty(p+"resource-tenant-id")),UUID.fromString(env.getRequiredProperty(p+"role-in-contract-id")),UUID.fromString(env.getRequiredProperty(p+"binding-id")),Long.parseLong(env.getRequiredProperty(p+"binding-version")),env.getRequiredProperty(p+"binding-digest"),Instant.parse(env.getRequiredProperty(p+"not-after")));
        read.requireCurrent(Clock.systemUTC().instant());
        if(!settings.issuer().equals(read.issuer())||!settings.audience().equals(read.audience())||!settings.allowedClientId().equals(read.allowedClientId())||!settings.platformTenant().equals(read.platformTenant())||!settings.resourceTenantId().equals(read.resourceTenantId())||settings.notAfter().isAfter(read.notAfter()))throw new IllegalStateException("Exact existing receiver trust and physical owner namespace required");
        return settings;
    }
    private static PolicyEvaluationPort policy(ObjectProvider<PolicyEvaluationPort> policies){var value=policies.getIfUnique();if(value==null)throw new IllegalStateException("Exactly one current contract link policy evaluator required");return value;}
    @Bean ContractLinkAdmission contractLinkAdmission(ContractLinkSettings settings,ObjectProvider<PolicyEvaluationPort> policies){return new AuthenticatedContractLinkAdmissionImpl(settings,policy(policies),Clock.systemUTC(),System::nanoTime);}
    @Bean ContractLinkReadAdmission contractLinkReadAdmission(ContractLinkSettings settings,ObjectProvider<PolicyEvaluationPort> policies){return new AuthenticatedContractLinkReadAdmissionImpl(settings,policy(policies),Clock.systemUTC(),System::nanoTime);}
    private static R2dbcEntityTemplate template(DatabaseClient client){return new R2dbcEntityTemplate(client,new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE,ContractLinkMapping.converters()));}
    @Bean ContractLinkStore contractLinkStore(@Qualifier("connectionFactory") ConnectionFactory business,ContractLinkAdmission authority){
        var factory=new OwnedLeaseConnectionFactory(business);var client=DatabaseClient.create(factory);var template=template(client);var manager=new R2dbcTransactionManager(factory);
        return new SpringDataContractLinkStoreImpl(new ContractLinkAuditRootImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),System::nanoTime));
    }
    @Bean ContractLinkReadStore contractLinkReadStore(@Qualifier("connectionFactory") ConnectionFactory business,ContractLinkReadAdmission authority){
        var factory=new OwnedLeaseConnectionFactory(business);var client=DatabaseClient.create(factory);var template=template(client);var manager=new R2dbcTransactionManager(factory);
        return new SpringDataContractLinkReaderImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),System::nanoTime);
    }
    @Bean @Order(-50) WebFilter contractLinkVerifiedSubject(@Qualifier("contractReadJwtDecoder") ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter") Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,ContractLinkSettings settings){
        return new VerifiedContractLinkJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/initial-primary-loan-contract-links");
    }
}
