/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.linkage.ContractLinkReceipt;
import reactor.core.publisher.Mono;
public interface ContractLinkReadStore {Mono<java.util.Optional<ContractLinkReceipt>> read(ContractLinkReadIdentity identity);}
