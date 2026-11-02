/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.contract.sdk;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh generated compiled model contract only; no handwritten SDK model or business response is supplied. */
class InitialPrimaryContractGeneratedTemporalContractTest {
    @Test void freshlyGeneratedInitialLinkReceiptRetainsAuthoritativeInstant()throws Exception {
        var generated=Class.forName("io.fireflysoftware.core.contract.sdk.model.ContractLinkReceipt");
        assertEquals(Instant.class,generated.getMethod("getLinkedAt").getReturnType());assertNotNull(generated.getProtectionDomain().getCodeSource());
    }
}
