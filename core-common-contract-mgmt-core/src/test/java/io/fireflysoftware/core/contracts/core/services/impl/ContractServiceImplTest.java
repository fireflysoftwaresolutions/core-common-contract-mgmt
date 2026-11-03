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

import io.fireflysoftware.core.contracts.core.mappers.ContractMapper;
import io.fireflysoftware.core.contracts.interfaces.dtos.ContractDTO;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractLinkKind;
import io.fireflysoftware.core.contracts.interfaces.enums.ContractStatusEnum;
import io.fireflysoftware.core.contracts.interfaces.enums.StatusCodeEnum;
import io.fireflysoftware.core.contracts.models.entities.Contract;
import io.fireflysoftware.core.contracts.models.entities.ContractStatusHistory;
import io.fireflysoftware.core.contracts.models.repositories.ContractRepository;
import io.fireflysoftware.core.contracts.models.repositories.ContractStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    @Mock
    private ContractRepository repository;

    @Mock
    private ContractMapper mapper;

    @Mock
    private ContractStatusHistoryRepository statusHistoryRepository;

    @Mock
    private ContractLineSyncImpl lineSync;

    @InjectMocks
    private ContractServiceImpl service;

    private final UUID contractId = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");

    @BeforeEach
    void historySaveEchoesTheRow() {
        lenient().when(statusHistoryRepository.findByContractIdAndStatusEndDateIsNull(any()))
                .thenReturn(Flux.empty());
        lenient().when(statusHistoryRepository.save(any(ContractStatusHistory.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void createContract_defaultsToDraftAndRecordsHistory() {
        ContractDTO request = new ContractDTO();
        Contract entity = new Contract();
        Contract saved = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.DRAFT).build();
        ContractDTO response = new ContractDTO();
        response.setContractId(contractId);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(Mono.just(saved));
        when(mapper.toDTO(saved)).thenReturn(response);

        StepVerifier.create(service.createContract(request))
                .expectNext(response)
                .verifyComplete();

        assertEquals(ContractStatusEnum.DRAFT, entity.getContractStatus());
        ArgumentCaptor<ContractStatusHistory> history = ArgumentCaptor.forClass(ContractStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertEquals(StatusCodeEnum.CREATED, history.getValue().getStatusCode());
        assertEquals(contractId, history.getValue().getContractId());
    }

    @Test
    void updateContract_rejectsAJumpFromActiveBackToDraft() {
        Contract existing = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.ACTIVE).build();
        Contract replacement = Contract.builder().contractStatus(ContractStatusEnum.DRAFT).build();
        when(repository.findById(contractId)).thenReturn(Mono.just(existing));
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(replacement);

        StepVerifier.create(service.updateContract(contractId, new ContractDTO()))
                .expectErrorMatches(error -> error.getMessage().contains("cannot move from ACTIVE to DRAFT"))
                .verify();
    }

    @Test
    void updateContract_activatesADraftAndRecordsTheNewStatus() {
        Contract existing = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.DRAFT).build();
        Contract replacement = Contract.builder().contractStatus(ContractStatusEnum.ACTIVE).build();
        Contract saved = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.ACTIVE).build();
        ContractDTO response = new ContractDTO();
        when(repository.findById(contractId)).thenReturn(Mono.just(existing));
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(replacement);
        when(repository.save(replacement)).thenReturn(Mono.just(saved));
        when(mapper.toDTO(saved)).thenReturn(response);

        StepVerifier.create(service.updateContract(contractId, new ContractDTO()))
                .expectNext(response)
                .verifyComplete();

        ArgumentCaptor<ContractStatusHistory> history = ArgumentCaptor.forClass(ContractStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertEquals(StatusCodeEnum.ACTIVATED, history.getValue().getStatusCode());
    }

    @Test
    void deleteContract_keepsAnActiveAgreement() {
        Contract existing = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.ACTIVE).build();
        when(repository.findById(contractId)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.deleteContract(contractId))
                .expectErrorMatches(error -> error.getMessage().contains("cannot be deleted"))
                .verify();
    }

    @Test
    void deleteContract_removesADraft() {
        Contract existing = Contract.builder().contractId(contractId).contractStatus(ContractStatusEnum.DRAFT).build();
        when(repository.findById(contractId)).thenReturn(Mono.just(existing));
        when(repository.deleteById(contractId)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteContract(contractId))
                .verifyComplete();
    }

    @Test
    void createContract_rejectsASelfLink() {
        Contract entity = new Contract();
        entity.setContractId(contractId);
        entity.setPredecessorContractId(contractId);
        entity.setLinkKind(ContractLinkKind.AMENDMENT);
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(entity);

        StepVerifier.create(service.createContract(new ContractDTO()))
                .expectErrorMatches(error -> error.getMessage().contains("itself"))
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void createContract_requiresBothPredecessorAndLinkKind() {
        Contract entity = new Contract();
        entity.setPredecessorContractId(UUID.randomUUID());
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(entity);

        StepVerifier.create(service.createContract(new ContractDTO()))
                .expectErrorMatches(error -> error.getMessage().contains("link kind"))
                .verify();
    }

    @Test
    void createContract_rejectsAnUnknownPredecessor() {
        UUID predecessor = UUID.randomUUID();
        Contract entity = new Contract();
        entity.setPredecessorContractId(predecessor);
        entity.setLinkKind(ContractLinkKind.RENEWAL);
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(entity);
        when(repository.findById(predecessor)).thenReturn(Mono.empty());

        StepVerifier.create(service.createContract(new ContractDTO()))
                .expectErrorMatches(error -> error.getMessage().contains("Predecessor contract not found"))
                .verify();
    }

    @Test
    void createContract_rejectsACurrencyThatIsNotThreeLetters() {
        Contract entity = new Contract();
        entity.setCurrencyCode("eur");
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(entity);

        StepVerifier.create(service.createContract(new ContractDTO()))
                .expectErrorMatches(error -> error.getMessage().contains("Currency"))
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void createContract_keepsTheHeaderProductOnThePrimaryLine() {
        UUID productId = UUID.randomUUID();
        Contract entity = new Contract();
        entity.setProductId(productId);
        Contract saved = Contract.builder()
                .contractId(contractId)
                .productId(productId)
                .contractStatus(ContractStatusEnum.DRAFT)
                .currencyCode("EUR")
                .build();
        when(mapper.toEntity(any(ContractDTO.class))).thenReturn(entity);
        when(repository.save(entity)).thenReturn(Mono.just(saved));
        when(lineSync.alignPrimaryLine(saved)).thenReturn(Mono.just(saved));
        when(mapper.toDTO(saved)).thenReturn(new ContractDTO());

        StepVerifier.create(service.createContract(new ContractDTO()))
                .expectNextCount(1)
                .verifyComplete();

        verify(lineSync).alignPrimaryLine(saved);
    }
}
