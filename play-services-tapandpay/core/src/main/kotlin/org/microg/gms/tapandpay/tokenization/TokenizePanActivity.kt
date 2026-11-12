/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.tokenization

import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import com.google.android.gms.tapandpay.firstparty.AccountInfo
import org.microg.gms.tapandpay.WalletStateStore
import org.microg.gms.tapandpay.core.R

/** Shared setup entry point for PayActivity and the first-party Binder resolution. */
class TokenizePanActivity : FragmentActivity() {
    private var selectedAccountName: String? = null
    companion object {
        const val ACTION = "com.google.android.gms.tapandpay.ACTION_TOKENIZE_PAN"
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
        const val EXTRA_CLIENT_TOKEN_ID = "client_token_id"
        const val EXTRA_ENTRY_POINT = "EXTRA_TOKENIZATION_ENTRY_POINT"
        const val EXTRA_WALLET_ID = "wallet_id"
        const val EXTRA_ACCOUNT_NAME = "org.microg.gms.tapandpay.ACCOUNT_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup success is not tokenization success. Never return RESULT_OK for a bootstrap wallet.
        setResult(RESULT_CANCELED)
        title = getString(R.string.wallet_setup_title)
        val model = ViewModelProvider(this)[WalletSetupViewModel::class.java]
        val accounts: List<Account>
        val preferred: Account?
        val suppliedAccount = runCatching {
            SafeParcelableSerializer.deserializeFromBytes(intent.getByteArrayExtra("extra_account_info"), AccountInfo.CREATOR)
                ?.accountName
        }.getOrNull()
        val preferredName = savedInstanceState?.getString("selected_account")
            ?: intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: suppliedAccount
        WalletStateStore(this).use { store ->
            accounts = store.googleAccounts()
            preferred = accounts.firstOrNull { it.name == preferredName } ?: store.activeAccount()
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        content.addView(TextView(this).apply { setText(R.string.wallet_setup_description); textSize = 16f })
        if (accounts.isEmpty()) {
            content.addView(TextView(this).apply { setText(R.string.wallet_setup_no_accounts) })
            return
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@TokenizePanActivity, android.R.layout.simple_spinner_dropdown_item, accounts.map { it.name })
            setSelection(accounts.indexOf(preferred).coerceAtLeast(0))
        }
        selectedAccountName = accounts[spinner.selectedItemPosition].name
        content.addView(spinner)
        val status = TextView(this).apply { textSize = 16f; setPadding(0, padding, 0, padding) }
        content.addView(status)
        val progress = ProgressBar(this)
        content.addView(progress)
        val check = Button(this).apply {
            setText(R.string.wallet_setup_check)
            setOnClickListener { model.check(accounts[spinner.selectedItemPosition]) }
        }
        content.addView(check)
        val register = Button(this).apply {
            setText(R.string.wallet_setup_register)
            setOnClickListener { model.register(accounts[spinner.selectedItemPosition]) }
        }
        content.addView(register)
        val loadCards = Button(this).apply {
            setText(R.string.wallet_setup_load_cards)
            setOnClickListener { model.loadCards(accounts[spinner.selectedItemPosition]) }
        }
        content.addView(loadCards)
        val attest = Button(this).apply {
            setText(R.string.wallet_setup_attest)
            setOnClickListener { model.testAttestation(accounts[spinner.selectedItemPosition]) }
        }
        content.addView(attest)
        val cardGroup = RadioGroup(this)
        content.addView(cardGroup)
        // Rebuilt from state so eligibility and selection stay authoritative in the ViewModel.
        var renderedCards: List<WalletCardRow> = emptyList()
        val terms = TextView(this).apply { setTextIsSelectable(true) }
        content.addView(terms)
        val reviewTerms = Button(this).apply {
            setText(R.string.wallet_setup_review_terms)
            setOnClickListener {
                val url = model.state.value?.terms?.field1?.let(Uri::parse)
                if (url?.scheme == "https" && url.host == "payments.google.com") {
                    val viewIntent = Intent(Intent.ACTION_VIEW, url)
                    if (viewIntent.resolveActivity(packageManager) != null) startActivity(viewIntent)
                }
            }
        }
        content.addView(reviewTerms)
        val acceptTerms = Button(this).apply {
            setText(R.string.wallet_setup_accept_terms)
            setOnClickListener { model.acceptTerms(accounts[spinner.selectedItemPosition]) }
        }
        content.addView(acceptTerms)
        content.addView(Button(this).apply { setText(R.string.wallet_setup_close); setOnClickListener { finish() } })
        var selected = spinner.selectedItemPosition
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedAccountName = accounts[position].name
                if (position != selected) {
                    selected = position
                    model.reset()
                }
            }
        }
        model.state.observe(this) { state ->
            status.text = state.message
            progress.visibility = if (state.busy) View.VISIBLE else View.GONE
            spinner.isEnabled = !state.busy
            check.isEnabled = !state.busy
            register.isEnabled = state.canRegister && !state.busy
            loadCards.isEnabled = state.canLoadCards && !state.busy
            attest.isEnabled = state.canLoadCards && !state.busy
            if (state.cards != renderedCards) {
                renderedCards = state.cards
                cardGroup.setOnCheckedChangeListener(null)
                cardGroup.removeAllViews()
                state.cards.forEachIndexed { index, card ->
                    cardGroup.addView(RadioButton(this).apply {
                        id = index
                        text = if (card.eligible) card.label
                            else getString(R.string.wallet_setup_card_ineligible, card.label)
                        isEnabled = card.eligible
                    })
                }
            }
            cardGroup.setOnCheckedChangeListener(null)
            if (state.selectedCard in state.cards.indices) cardGroup.check(state.selectedCard)
            else cardGroup.clearCheck()
            for (i in 0 until cardGroup.childCount) {
                cardGroup.getChildAt(i).isEnabled = state.cards.getOrNull(i)?.eligible == true && !state.busy
            }
            cardGroup.setOnCheckedChangeListener { _, checkedId -> model.selectCard(checkedId) }
            val termsUrl = state.terms?.field1?.let(Uri::parse)
            val canReview = termsUrl?.scheme == "https" && termsUrl.host == "payments.google.com"
            reviewTerms.isEnabled = canReview && !state.busy
            acceptTerms.isEnabled = canReview && !state.busy && !state.termsAccepted
            terms.text = state.terms?.let {
                // Plain selectable text: no WebView, JS, or automatic URL navigation.
                getString(if (state.termsAccepted) R.string.wallet_setup_terms_recorded else R.string.wallet_setup_terms,
                    "${it.field1.orEmpty()}\n${it.field4.orEmpty()}\n(${it.field2 ?: 0}, ${it.field3 ?: 0})")
            }.orEmpty()
            model.takeAuthorization()?.let { resolution ->
                @Suppress("DEPRECATION")
                startActivityForResult(resolution, 1)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_account", selectedAccountName)
        super.onSaveInstanceState(outState)
    }
}
