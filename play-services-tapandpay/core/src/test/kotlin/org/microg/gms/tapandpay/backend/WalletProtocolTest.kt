/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.microg.gms.tapandpay.backend.proto.RegisterWalletResponse
import org.microg.gms.tapandpay.backend.proto.RequestEnvelope
import org.microg.gms.tapandpay.backend.proto.ResponseEnvelope

class WalletProtocolTest {
    // Hand-encoded protobuf fixtures from the recovered field tables, not captured server traffic.
    private fun hex(value: String) = value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun unwrapsWalletIdAndToleratesUnknownEnvelopeFields() {
        // envelope.payload(field 2).body(field 2).wallet_id(field 1) = "abc";
        // envelope field 99 is an unknown varint and must not break decoding.
        val bytes = hex("12 07 12 05 0a 03 61 62 63 98 06 01")
        val body = decodeWalletResponse("register", bytes)
        assertEquals("abc", RegisterWalletResponse.ADAPTER.decode(body).wallet_id)
        assertEquals(hex("98 06 01").toList(), ResponseEnvelope.ADAPTER.decode(bytes).unknownFields.toByteArray().toList())
    }

    @Test fun acceptsPresentAnyWithOmittedDefaultBody() {
        assertArrayEquals(byteArrayOf(), decodeWalletResponse("empty", hex("12 00")))
    }

    @Test fun errorPresenceWinsOverSuccessPayloadEvenWithDefaultCode() {
        // context.error is present but empty: GMS tests presence, not error.code != 0.
        val error = assertThrows(WalletBackendException::class.java) {
            decodeWalletResponse("check", hex("0a 02 0a 00 12 00"))
        }
        assertEquals("check", error.operation)
        assertNull(error.error.code)
    }

    @Test fun retainsOpaqueErrorDetailsWithoutIncludingThemInMessage() {
        // context.error: code=9, details=[Any{type_url="X", body="SECRET"}].
        val error = assertThrows(WalletBackendException::class.java) {
            decodeWalletResponse("check", hex("0a 11 0a 0f 08 09 22 0b 0a 01 58 12 06 53 45 43 52 45 54"))
        }
        assertEquals(9, error.error.code)
        assertEquals("SECRET", error.error.details.single().body!!.utf8())
        assertFalse(error.message!!.contains("SECRET"))
    }

    @Test fun rejectsMissingPayloadAndMalformedEnvelope() {
        assertThrows(IOException::class.java) { decodeWalletResponse("check", byteArrayOf()) }
        assertThrows(IOException::class.java) { decodeWalletResponse("check", hex("12 08 12")) }
    }

    @Test fun decodesRequestContextUsingRecoveredFieldNumbers() {
        // context: android_id=123, platform=1, source=1, session_id="s"; payload=empty Any.
        val request = RequestEnvelope.ADAPTER.decode(hex("0a 09 08 7b 30 01 60 01 5a 01 73 12 00"))
        assertEquals(123L, request.context!!.android_id)
        assertEquals(1, request.context!!.platform)
        assertEquals(1, request.context!!.request_source)
        assertEquals("s", request.context!!.session_id)
        assertNotNull(request.payload)
    }
}
