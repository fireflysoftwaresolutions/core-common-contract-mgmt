/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.contracts.core.services.impl;

import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.interfaces.enums.StatusCodeEnum;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed moves between the statuses already stored on a contract.
 * Terminal agreements stay on file; they are not rewritten back into a draft.
 */
final class ContractLifecycleImpl {

    private static final Map<ContractStatusEnum, Set<ContractStatusEnum>> ALLOWED = Map.of(
            ContractStatusEnum.DRAFT, EnumSet.of(
                    ContractStatusEnum.PENDING_APPROVAL, ContractStatusEnum.ACTIVE, ContractStatusEnum.CANCELLED),
            ContractStatusEnum.PENDING_APPROVAL, EnumSet.of(
                    ContractStatusEnum.DRAFT, ContractStatusEnum.ACTIVE, ContractStatusEnum.CANCELLED),
            ContractStatusEnum.ACTIVE, EnumSet.of(
                    ContractStatusEnum.SUSPENDED, ContractStatusEnum.TERMINATED, ContractStatusEnum.EXPIRED),
            ContractStatusEnum.SUSPENDED, EnumSet.of(
                    ContractStatusEnum.ACTIVE, ContractStatusEnum.TERMINATED)
    );

    private ContractLifecycleImpl() {
    }

    static boolean canTransition(ContractStatusEnum from, ContractStatusEnum to) {
        if (from == to) {
            return true;
        }
        Set<ContractStatusEnum> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }

    static StatusCodeEnum historyCode(ContractStatusEnum status) {
        return switch (status) {
            case DRAFT -> StatusCodeEnum.CREATED;
            case PENDING_APPROVAL -> StatusCodeEnum.SUBMITTED_FOR_APPROVAL;
            case ACTIVE -> StatusCodeEnum.ACTIVATED;
            case SUSPENDED -> StatusCodeEnum.SUSPENDED;
            case TERMINATED -> StatusCodeEnum.TERMINATED;
            case EXPIRED -> StatusCodeEnum.EXPIRED;
            case CANCELLED -> StatusCodeEnum.CANCELLED;
        };
    }
}
