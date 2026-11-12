/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.serviceentitlement

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.ConnectionInfo
import com.google.android.gms.common.internal.GetServiceRequest
import com.google.android.gms.common.internal.IGmsCallbacks
import com.google.android.gms.serviceentitlement.AuthenticationException
import com.google.android.gms.serviceentitlement.GmsTs43CarrierConfig
import com.google.android.gms.serviceentitlement.GmsTs43Operator
import com.google.android.gms.serviceentitlement.GmsTs43ServiceEntitlementRequest
import com.google.android.gms.serviceentitlement.TempTokenBundle
import org.microg.gms.BaseService
import org.microg.gms.common.GmsService
import org.microg.gms.common.PackageUtils

private const val TAG = "GmsServiceEntitlement"
private const val DESCRIPTOR = "com.google.android.gms.serviceentitlement.internal.IServiceEntitlement"
private const val TEMP_TOKEN_RESULT_DESCRIPTOR = "com.google.android.gms.serviceentitlement.internal.ITempTokenResult"

// IServiceEntitlement transaction codes (from GMS 26.44.32).
private const val TX_REQUEST_RAW_RESPONSE_FOR_GET_PHONE_NUMBER = 7

/**
 * {@code com.google.android.gms.serviceentitlement.service.ServiceEntitlementChimeraService} (id 359) —
 * the GSMA TS.43 service-entitlement API Google Messages binds for RCS carrier provisioning.
 *
 * For this carrier Messages only calls transaction 7 ({@code requestRawResponseForGetPhoneNumber}).
 * Unmarshals the request and runs TS.43 ODSA HTTP via opstic's EapAka/ServiceEntitlement
 * engine, returning the raw carrier response or an authentication error.
 */
class ServiceEntitlementService : BaseService(TAG, GmsService.SERVICE_ENTITLEMENT) {
    override fun handleServiceRequest(callback: IGmsCallbacks, request: GetServiceRequest, service: GmsService) {
        val packageName = PackageUtils.getAndCheckCallingPackage(this, request.packageName)
            ?: throw IllegalArgumentException("Missing package name")
        Log.d(TAG, "handleServiceRequest from $packageName (gmsVersion=${request.gmsVersion})")
        callback.onPostInitCompleteWithConnectionInfo(
            CommonStatusCodes.SUCCESS,
            ServiceEntitlementStub(applicationContext),
            ConnectionInfo().apply {
                features = arrayOf(
                    Feature("dynamic_carrier_id", 1),
                    Feature("service_entitlement", 1),
                    Feature("service_entitlement_1p_api", 1)
                )
            }
        )
    }
}

private class ServiceEntitlementStub(private val context: Context) : Binder() {
    init {
        attachInterface(null, DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TX_REQUEST_RAW_RESPONSE_FOR_GET_PHONE_NUMBER) {
            data.enforceInterface(DESCRIPTOR)
            val subId = data.readInt()
            val request = data.readTyped(GmsTs43ServiceEntitlementRequest.CREATOR)
            val operator = data.readTyped(GmsTs43Operator.CREATOR)
            val carrierConfig = data.readTyped(GmsTs43CarrierConfig.CREATOR)
            val flags2 = data.readInt()
            val resultCallback = data.readStrongBinder()
            reply?.writeNoException()

            Log.i(TAG, "requestRawResponseForGetPhoneNumber(subId=$subId, flags2=$flags2)")
            // Request/operator fields include persistent device and SIM identifiers.
            // Keep diagnostics structural rather than logging their raw values.
            Log.d(TAG, "TS.43 parameters present: request=${request != null}, " +
                    "operator=${operator != null}, carrierConfig=${carrierConfig != null}")

            // Perform the real TS.43 ODSA entitlement off the binder thread, then deliver the raw
            // response (or an error) via the oneway callback.
            Thread {
                val bundle = try {
                    val raw = org.microg.gms.constellation.core.verification.acquireTs43RawResponse(
                        context = context,
                        subId = subId,
                        entitlementUrl = carrierConfig?.serverUrl.orEmpty(),
                        eapAkaRealm = carrierConfig?.s5,
                        operation = operator?.s1.orEmpty(),
                        operationTargets = operator?.list3 ?: emptyList(),
                        appId = request?.s10,
                        entitlementVersion = request?.appId.orEmpty(),
                        terminalId = request?.s5,
                        terminalVendor = request?.s6.orEmpty(),
                        terminalModel = request?.s7.orEmpty(),
                        terminalSwVersion = request?.s8.orEmpty(),
                        appName = request?.s9.orEmpty(),
                        appVersion = "",
                        acceptContentType = request?.s12.orEmpty(),
                        iccid = operator?.s12
                    )
                    if (raw != null) {
                        Log.i(TAG, "TS.43 ODSA raw response received (${raw.length} chars)")
                        TempTokenBundle(raw, null, System.currentTimeMillis(), 0)
                    } else {
                        Log.w(TAG, "TS.43 ODSA entitlement returned no response")
                        TempTokenBundle(null, AuthenticationException(1, 0, "entitlement-failed", null, null, 0), System.currentTimeMillis(), 0)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TS.43 ODSA entitlement threw", e)
                    TempTokenBundle(null, AuthenticationException(1, 0, "entitlement-exception", null, null, 0), System.currentTimeMillis(), 0)
                }
                sendTempTokenResult(resultCallback, Status.SUCCESS, bundle)
            }.start()
            return true
        }
        Log.w(TAG, "DIAG onTransact(code=$code, flags=$flags, dataSize=${data.dataSize()})")
        return super.onTransact(code, data, reply, flags)
    }

    private fun sendTempTokenResult(callback: IBinder?, status: Status, bundle: TempTokenBundle) {
        if (callback == null) return
        val out = Parcel.obtain()
        try {
            out.writeInterfaceToken(TEMP_TOKEN_RESULT_DESCRIPTOR)
            out.writeInt(1); status.writeToParcel(out, 0)
            out.writeInt(1); bundle.writeToParcel(out, 0)
            callback.transact(1, out, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver ITempTokenResult", e)
        } finally {
            out.recycle()
        }
    }
}

private fun <T> Parcel.readTyped(creator: android.os.Parcelable.Creator<T>): T? =
    if (readInt() != 0) creator.createFromParcel(this) else null
