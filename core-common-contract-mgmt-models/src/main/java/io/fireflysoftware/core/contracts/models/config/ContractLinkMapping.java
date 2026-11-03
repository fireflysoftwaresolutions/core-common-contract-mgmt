/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.models.config;
import io.fireflysoftware.core.contracts.interfaces.enums.*;
import io.fireflysoftware.core.contracts.models.config.impl.*;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.postgresql.extension.CodecRegistrar;
import java.util.List;
public final class ContractLinkMapping {
    private ContractLinkMapping(){}
    public static CodecRegistrar codecs(){return EnumCodec.builder().withEnum("contract_status_enum",ContractStatusEnum.class).withEnum("status_code_enum",StatusCodeEnum.class).withEnum("contract_link_kind_enum",ContractLinkKind.class).build();}
    public static List<Object> converters(){return List.of(new ContractStatusEnumWriteConverterImpl(),new StatusCodeEnumWriteConverterImpl(),new ContractLinkKindWriteConverterImpl());}
}
