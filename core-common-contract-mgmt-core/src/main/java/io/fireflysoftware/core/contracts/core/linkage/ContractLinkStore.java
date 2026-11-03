/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contracts.core.linkage;
import io.fireflysoftware.core.contracts.interfaces.linkage.*;
import reactor.core.publisher.Mono;
/** The owning atomic action; ordinary CRUD remains in the mapped Core transaction. */
public interface ContractLinkStore {Mono<ContractLinkReceipt> link(ContractLinkRequest request);}
