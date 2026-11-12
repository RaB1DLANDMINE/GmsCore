/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.pay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView

private const val TAG = "GmsPay"

/**
 * Actions sent by the Google Wallet client to the exported proxy
 * `com.google.android.gms.pay.main.PayActivity` (alias of this activity).
 */
object PayActions {
    /** "Add a payment method / card" entry point from the Wallet home screen. */
    const val ADD_PAYMENT_METHOD = "com.google.android.gms.pay.homescreen.additem.ADD_PAYMENT_METHOD"
}

/**
 * Internal tap-and-pay actions this router dispatches to. These are handled by
 * activities in the `play-services-tapandpay` module within the same app.
 */
private object TapAndPayActions {
    /** Add-card / tokenize-PAN UI (`tapandpay.tokenization.TokenizePanChimeraActivity`). */
    const val TOKENIZE_PAN = "com.google.android.gms.tapandpay.ACTION_TOKENIZE_PAN"
}

/**
 * Entry point behind the exported `com.google.android.gms.pay.main.PayActivity` alias.
 *
 * Plays the role of the real GMS `pay.main.PayChimeraActivity` router: it inspects the
 * incoming action and forwards it to the matching feature flow. Unhandled actions fall
 * back to the previous placeholder so behaviour is unchanged for them.
 */
class PayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        Log.d(TAG, "PayActivity dispatch: action=$action")
        when (action) {
            PayActions.ADD_PAYMENT_METHOD -> dispatch(TapAndPayActions.TOKENIZE_PAN)
            else -> showUnsupported(action)
        }
    }

    /**
     * Forwards the request to an internal action handler within this app, carrying over all
     * extras supplied by the Wallet client. Finishes afterwards so this router leaves no
     * extra entry on the back stack.
     */
    private fun dispatch(targetAction: String) {
        val forward = Intent(targetAction).setPackage(packageName)
        intent?.extras?.let { forward.putExtras(it) }
        forward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        try {
            startActivity(forward)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No handler for $targetAction", e)
            showUnsupported(intent?.action)
            return
        }
        finish()
    }

    private fun showUnsupported(action: String?) {
        setContentView(TextView(this).apply {
            text = "Not yet supported:\n$action"
            gravity = Gravity.CENTER
        })
    }
}
