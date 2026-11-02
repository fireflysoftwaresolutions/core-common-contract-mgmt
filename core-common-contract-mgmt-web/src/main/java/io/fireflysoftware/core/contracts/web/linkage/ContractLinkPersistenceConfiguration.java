/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.web.linkage;

import io.fireflysoftware.core.contracts.models.config.ContractLinkMapping;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.extension.Extension;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="contracts.initial-primary-link.enabled",havingValue="true")
public class ContractLinkPersistenceConfiguration {
    @Bean R2dbcCustomConversions contractLinkConversions(){return R2dbcCustomConversions.of(PostgresDialect.INSTANCE,ContractLinkMapping.converters());}
    @Bean ConnectionFactoryOptionsBuilderCustomizer contractLinkEnumCodecs(){return options->{
        var extensions=new ArrayList<Extension>();Object configured=options.build().getValue(PostgresqlConnectionFactoryProvider.EXTENSIONS);
        if(configured!=null){
            if(!(configured instanceof Collection<?> collection))throw new IllegalStateException("Declared PostgreSQL driver extensions required");
            for(Object extension:collection){if(!(extension instanceof Extension typed))throw new IllegalStateException("Declared PostgreSQL driver extension required");extensions.add(typed);}
        }
        extensions.add(ContractLinkMapping.codecs());options.option(PostgresqlConnectionFactoryProvider.EXTENSIONS,List.copyOf(extensions));
    };}
}
