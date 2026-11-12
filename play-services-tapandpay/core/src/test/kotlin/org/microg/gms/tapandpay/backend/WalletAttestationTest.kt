/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import org.microg.gms.tapandpay.backend.proto.AttestationContext
import org.microg.gms.tapandpay.backend.proto.GetNonceResponse

class WalletAttestationTest {
    private fun hex(value: String) = value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun nonceResponseReadsField1Bytes() {
        // iynb: field 1 (bytes) carries the nonce.
        assertArrayEquals(hex("01 02 03"), GetNonceResponse.ADAPTER.decode(hex("0a 03 01 02 03")).nonce?.toByteArray())
    }

    @Test fun attestationContextUsesRecoveredFieldNumbers() {
        // iycv: 1=type(enum/varint) 2=verdict(string) 3=nonce(bytes) 5=fingerprint(string) 6=status(enum/varint).
        val encoded = AttestationContext(
            type = 2,
            verdict = "x",
            nonce = hex("ab").toByteString(),
            fingerprint = "f",
            status = 1
        ).encode()
        assertEquals(hex("08 02 12 01 78 1a 01 ab 2a 01 66 30 01").toList(), encoded.toList())
    }

    @Test fun droidguardTypeAndSuccessStatusAreWireValues() {
        val decoded = AttestationContext.ADAPTER.decode(hex("08 02 30 01"))
        assertEquals(2, decoded.type)  // iyct.DROIDGUARD
        assertEquals(1, decoded.status) // iycu.STATUS_SUCCESS
    }
}
