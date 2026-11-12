/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import org.junit.Assert.*
import org.junit.Test
import org.microg.gms.tapandpay.backend.proto.*

class PaymentsClientTokenTest {
    private fun hex(value: String) = value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun encodesTapAndPayConfigWithRecoveredFieldTags() {
        // jjej field 1=context(locale field7="en-GB", platform field8=2);
        // field 2=config(field3=1, repeated field4=2, field5=1).
        val encoded = PaymentsClientTokenFactory.encode(PaymentsContext(locale = "en-GB", platform = 2), 1)
        assertArrayEquals(hex("0a 09 3a 05 65 6e 2d 47 42 40 02 12 06 18 01 20 02 28 01"), encoded)
    }

    @Test fun rejectsUnknownClientTypeInsteadOfInventingConfig() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentsClientTokenFactory.encode(PaymentsContext(), 3)
        }
    }

    @Test fun decodesCardLabelIdAndOpaqueInitializationTokenAtStockTags() {
        // iydh.cards[0]: label(field3)="Visa", instrument_id(field6)="123";
        // add-token(field2)="TOK", customer-selector(field3)=true.
        val response = ListUntokenizedCardsResponse.ADAPTER.decode(
            hex("0a 0b 1a 04 56 69 73 61 32 03 31 32 33 12 03 54 4f 4b 18 01"))
        assertEquals("Visa", response.cards.single().label)
        assertEquals("123", response.cards.single().instrument_id)
        assertEquals("TOK", response.add_card_token!!.utf8())
        assertEquals(true, response.show_customer_selector)
    }

    @Test fun encodesTermsSubmissionWithRecoveredNonConsecutiveTags() {
        val request = UpdateTermsRequest(field1 = 1, field2 = 2, version_code = "v", locale = "en", document_url = "u", android_id = 7)
        assertArrayEquals(hex("08 01 10 02 1a 01 76 2a 02 65 6e 32 01 75 38 07"), request.encode())
    }
}
