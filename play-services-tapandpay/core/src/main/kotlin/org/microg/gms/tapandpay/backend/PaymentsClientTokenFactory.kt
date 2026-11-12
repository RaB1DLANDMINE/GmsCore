/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.backend

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.microg.gms.tapandpay.backend.proto.PaymentsClientConfig
import org.microg.gms.tapandpay.backend.proto.PaymentsClientToken
import org.microg.gms.tapandpay.backend.proto.PaymentsContext
import org.microg.gms.tapandpay.backend.proto.PaymentsDeviceContext
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Initial Payments token producer. Optional unrecovered capabilities remain absent. */
object PaymentsClientTokenFactory {
    @JvmStatic
    @JvmOverloads
    fun create(context: Context, clientType: Int = 1): ByteArray {
        require(clientType == 1 || clientType == 2)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val metrics = context.resources.displayMetrics
        val device = PaymentsDeviceContext(
            sdk_version = Build.VERSION.SDK_INT.toString(),
            device = Build.DEVICE,
            model = Build.MODEL,
            brand = Build.BRAND,
            screen_width = metrics.widthPixels,
            screen_height = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            density_dpi = metrics.densityDpi,
            package_name = context.packageName,
            version_code = version.toString(),
            version_name = info.versionName.orEmpty(),
            calling_package = context.packageName,
            calling_version_code = version.toString(),
            gms_version_code = version.toString(),
            client_type = 2, // iayf.m sets w=3; iaym.b writes w-1.
            uptime_ms = SystemClock.uptimeMillis(),
            timezone_name = TimeZone.getDefault().displayName
        )
        return encode(PaymentsContext(
            locale = Locale.getDefault().toLanguageTag(),
            platform = 2,
            version_code = version,
            request_id = UUID.randomUUID().leastSignificantBits.toString(),
            android = device
        ), clientType)
    }

    internal fun encode(context: PaymentsContext, clientType: Int): ByteArray {
        require(clientType == 1 || clientType == 2)
        // gwdr.b writes 1 for the tapandpay client type (request.c=1), 2 for c=2.
        return PaymentsClientToken(context, PaymentsClientConfig(
            field3 = clientType,
            field5 = clientType,
            supported_ui = listOf(2)
        )).encode()
    }
}
