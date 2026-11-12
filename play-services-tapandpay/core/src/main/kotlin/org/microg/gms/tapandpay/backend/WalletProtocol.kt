/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import java.io.IOException
import org.microg.gms.tapandpay.backend.proto.BackendError
import org.microg.gms.tapandpay.backend.proto.ResponseEnvelope

class WalletBackendException(val operation: String, val error: BackendError) : IOException(
    "$operation: backend error ${error.code ?: 0}" +
            error.details.map { it.type_url.orEmpty().substringAfterLast('/') }.filter(String::isNotEmpty)
                .joinToString(prefix = " [", postfix = "]")
)

/** Keep the full structured error in memory; never dump tokens or payloads to logcat. */
fun decodeWalletResponse(operation: String, bytes: ByteArray): ByteArray {
    val envelope = ResponseEnvelope.ADAPTER.decode(bytes)
    envelope.context?.error?.let { throw WalletBackendException(operation, it) }
    val payload = envelope.payload ?: throw IOException("$operation: response is missing its payload")
    // GMS's Any uses proto3 defaults: an omitted value in a present Any is an empty message.
    return payload.body?.toByteArray() ?: byteArrayOf()
}
