/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import org.microg.gms.checkin.LastCheckinInfo
import org.microg.gms.tapandpay.backend.proto.*

private const val TAG = "GmsWalletBackend"
private const val TAP_SCOPE = "oauth2:https://www.googleapis.com/auth/tapandpay"
private const val SETUP_SCOPE = "oauth2:https://www.googleapis.com/auth/sierra https://www.googleapis.com/auth/tapandpay"

class WalletAuthorizationRequired(val resolution: Intent) : IOException("Google account authorization is required")

/** GMS 26.44.32 gnli/gnlw: HTTP protobuf envelopes, not gRPC framing. Call off the main thread. */
class WalletBackendClient(private val context: Context, private val account: Account, private val sessionId: String) {
    private val accountManager = AccountManager.get(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private val androidId: Long
        get() = LastCheckinInfo.read(context).androidId.takeIf { it != 0L }
            ?: throw IOException("Google device registration is missing. Enable device registration in microG settings and retry.")

    private fun token(scope: String): String {
        val result = accountManager.getAuthToken(account, scope, null, false, null, null)
            .getResult(60, TimeUnit.SECONDS)
        @Suppress("DEPRECATION")
        val resolution = result.getParcelable<Intent>(AccountManager.KEY_INTENT)
        if (resolution != null) throw WalletAuthorizationRequired(resolution)
        return result.getString(AccountManager.KEY_AUTHTOKEN)?.takeIf(String::isNotBlank)
            ?: throw IOException("Google account did not return an authorization token")
    }

    private fun requestContext(): RequestContext {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return RequestContext(
            android_id = androidId,
            client = ClientInfo(context.packageName, versionCode.toString(), info.versionName.orEmpty(), 1),
            platform = 1,
            session_id = sessionId,
            // gnli's standard caller uses 3; iylc.a(i) returns i - 2.
            request_source = 1,
            device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT.toLong(), Build.VERSION.RELEASE, Build.ID)
        )
    }

    private fun post(path: String, body: ByteArray, scope: String = SETUP_SCOPE): ByteArray {
        val encoded = RequestEnvelope(requestContext(), Payload(body = body.toByteString())).encode()
        val url = (if (path.startsWith("g/")) "https://pay-users-pa.googleapis.com/"
            else "https://androidpay-users-pa.googleapis.com/cm/").toHttpUrl().newBuilder()
            .addEncodedPathSegments(path)
            .addQueryParameter("forcehl", "1")
            .addQueryParameter("hl", Locale.getDefault().toLanguageTag()).build()
        var authToken = token(scope)
        repeat(2) { attempt ->
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $authToken")
                .header("User-Agent", "androidpay microG (Android ${Build.PRODUCT} ${Build.ID} ${Build.MODEL} ${Build.TAGS})")
                .post(encoded.toRequestBody("application/x-protobuf".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                Log.d(TAG, "$path: HTTP ${response.code}")
                if (response.code == 401 && attempt == 0) {
                    accountManager.invalidateAuthToken("com.google", authToken)
                    authToken = token(scope)
                } else {
                    if (!response.isSuccessful) throw IOException("$path: HTTP ${response.code}")
                    val source = response.body?.source() ?: throw IOException("$path: empty HTTP response")
                    // Bound untrusted response allocation; don't log the body (it can contain secrets).
                    if (source.request(MAX_RESPONSE_BYTES + 1)) throw IOException("$path: response too large")
                    return decodeWalletResponse(path, source.readByteArray())
                }
            }
        }
        throw IOException("$path: authentication failed")
    }

    fun checkAccount(): AccountEnablementResponse = AccountEnablementResponse.ADAPTER.decode(
        post("r/compliance/getaccountenablementstatus", EmptyRequest().encode(), TAP_SCOPE)
    )

    fun getTerms(): TermsResponse = TermsResponse.ADAPTER.decode(
        post("t/termsofservice/get", EmptyRequest().encode())
    )

    fun registerWallet(): RegisterWalletResponse = RegisterWalletResponse.ADAPTER.decode(
        // Optional fields intentionally omitted until normal phone registration requirements
        // are established. The server may reject this bootstrap request; surface that result.
        post("t/setup/registerwallet", RegisterWalletRequest().encode())
    )

    fun listUntokenizedCards(): ListUntokenizedCardsResponse = ListUntokenizedCardsResponse.ADAPTER.decode(
        post("t/untokenizedcards/list", ListUntokenizedCardsRequest(
            client_token = PaymentsClientTokenFactory.create(context).toByteString()
        ).encode())
    )

    /**
     * `t/security/getnonce`: returns the server nonce a DroidGuard `tapandpay_attestation` verdict
     * must be computed over. Read-only; it does not mutate the payment account.
     */
    fun getNonce(): ByteArray = GetNonceResponse.ADAPTER.decode(
        post("t/security/getnonce", GetNonceRequest().encode())
    ).nonce?.toByteArray() ?: throw IOException("getnonce: server returned an empty nonce")

    fun acceptTerms(terms: TermsResponse) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        post("t/termsofservice/update", UpdateTermsRequest(
            field1 = terms.field2 ?: 0,
            field2 = terms.field3 ?: 0,
            version_code = version.toString(),
            locale = terms.field4.orEmpty(),
            document_url = terms.field1.orEmpty(),
            android_id = androidId
        ).encode())
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    }
}
