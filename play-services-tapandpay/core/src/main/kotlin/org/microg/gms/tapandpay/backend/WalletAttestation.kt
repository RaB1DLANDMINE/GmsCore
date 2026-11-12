/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.android.gms.droidguard.DroidGuardClient
import com.google.android.gms.tasks.await
import okio.ByteString.Companion.toByteString
import org.microg.gms.tapandpay.backend.proto.AttestationContext

/**
 * Reconstructs GMS 26.44.32 `gnnx.c`: turn a server nonce into the DroidGuard-backed
 * [AttestationContext] (`iycv`) that the tokenize request carries. This is the single real
 * attestation dependency of card tokenization; it is reproduced 1:1 against microG's own
 * DroidGuard client. It performs no payment-account mutation.
 */
object WalletAttestation {
    const val FLOW = "tapandpay_attestation"

    // iyct.DROIDGUARD and iycu.STATUS_SUCCESS wire values.
    private const val TYPE_DROIDGUARD = 2
    private const val STATUS_SUCCESS = 1

    /**
     * gnnx.c passes the nonce to DroidGuard as standard base64 without padding
     * (`ihnt.d.n().p(nonce)`), keyed "nonce".
     */
    fun encodeNonce(nonce: ByteArray): String =
        Base64.encodeToString(nonce, Base64.NO_PADDING or Base64.NO_WRAP)

    /**
     * Runs the DroidGuard `tapandpay_attestation` flow over [nonce] and wraps the verdict as
     * `iycv`. A blank verdict (DroidGuard could not attest) is surfaced as an error, exactly as the
     * caller needs: whether Google accepts a microG verdict is the one unknown of this stage.
     */
    suspend fun attest(context: Context, nonce: ByteArray): AttestationContext {
        val verdict = DroidGuardClient.getResults(context, FLOW, mapOf("nonce" to encodeNonce(nonce))).await()
        if (verdict.isNullOrBlank()) throw IllegalStateException("DroidGuard returned no attestation verdict")
        return AttestationContext(
            type = TYPE_DROIDGUARD,
            verdict = verdict,
            nonce = nonce.toByteString(),
            fingerprint = Build.FINGERPRINT,
            status = STATUS_SUCCESS
        )
    }
}
