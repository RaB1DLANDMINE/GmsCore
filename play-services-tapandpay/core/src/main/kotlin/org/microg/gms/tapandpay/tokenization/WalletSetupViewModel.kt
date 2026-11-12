/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay.tokenization

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.microg.gms.tapandpay.WalletStateStore
import org.microg.gms.tapandpay.backend.WalletAttestation
import org.microg.gms.tapandpay.backend.WalletAuthorizationRequired
import org.microg.gms.tapandpay.backend.WalletBackendClient
import org.microg.gms.tapandpay.backend.WalletBackendException
import org.microg.gms.tapandpay.backend.proto.TermsResponse
import org.microg.gms.tapandpay.backend.proto.ListUntokenizedCardsResponse
import org.microg.gms.tapandpay.core.R
import java.io.IOException
import java.util.UUID

/** One selectable row in the saved-card list. [eligible] cards can proceed to tokenization. */
data class WalletCardRow(val label: String, val eligible: Boolean)

data class WalletSetupState(
    val message: String,
    val busy: Boolean = false,
    val canRegister: Boolean = false,
    val terms: TermsResponse? = null,
    val canLoadCards: Boolean = false,
    val termsAccepted: Boolean = false,
    val cards: List<WalletCardRow> = emptyList(),
    val selectedCard: Int = -1,
    // True when Google offers more than one billing profile. Non-blocking: the returned
    // cards are still usable; switching profiles is an optional refinement (not implemented).
    val multipleProfiles: Boolean = false
)

/** Retained across rotation: a registration must not be replayed by onCreate. */
class WalletSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionId = UUID.randomUUID().toString()
    private var checkedAccount: Account? = null
    private var authorization: Intent? = null
    // Orchestration tokens/card identifiers remain in memory, never SharedPreferences or logs.
    private var cardsResponse: ListUntokenizedCardsResponse? = null
    val state = MutableLiveData(WalletSetupState(application.getString(R.string.wallet_setup_idle)))

    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    fun takeAuthorization(): Intent? = authorization.also { authorization = null }

    fun reset() {
        if (state.value?.busy == true) return
        checkedAccount = null
        cardsResponse = null
        state.value = WalletSetupState(text(R.string.wallet_setup_idle))
    }

    fun check(account: Account) = runOperation(R.string.wallet_setup_checking) {
        checkedAccount = null
        if (!withContext(Dispatchers.IO) { WalletStateStore(getApplication()).use { it.selectAccount(account.name) } }) {
            throw IOException(text(R.string.wallet_setup_invalid_account))
        }
        val client = WalletBackendClient(getApplication(), account, sessionId)
        val status = withContext(Dispatchers.IO) { client.checkAccount().status ?: 0 }
        // gnkw.b tests iysx.a(status) == 4, which corresponds to wire value 2.
        if (status == 2) {
            state.value = WalletSetupState(text(R.string.wallet_setup_account_blocked))
            return@runOperation
        }
        val terms = withContext(Dispatchers.IO) { client.getTerms() }
        val wallet = withContext(Dispatchers.IO) { WalletStateStore(getApplication()).use { it.walletId(account) } }
        checkedAccount = account
        state.value = WalletSetupState(
            if (wallet != null) text(R.string.wallet_setup_existing) else text(R.string.wallet_setup_checked, status),
            canRegister = wallet == null,
            terms = terms,
            canLoadCards = wallet != null
        )
    }

    fun register(account: Account) {
        if (account != checkedAccount || state.value?.canRegister != true) return
        val terms = state.value?.terms
        runOperation(R.string.wallet_setup_registering) {
            withContext(Dispatchers.IO) {
                registrationMutex.withLock {
                    WalletStateStore(getApplication()).use { store ->
                        if (store.googleAccounts().none { it == account }) throw IOException(text(R.string.wallet_setup_invalid_account))
                        if (store.walletId(account) == null) {
                            val response = WalletBackendClient(getApplication(), account, sessionId).registerWallet()
                            val walletId = response.wallet_id?.takeIf(String::isNotBlank)
                                ?: throw IOException(text(R.string.wallet_setup_empty_wallet))
                            // Commit before returning to UI, even if the activity is closed during the response.
                            store.saveWalletId(account, walletId)
                        }
                    }
                }
            }
            state.value = WalletSetupState(text(R.string.wallet_setup_registered), terms = terms, canLoadCards = true)
        }
    }

    fun loadCards(account: Account) {
        if (account != checkedAccount || state.value?.canLoadCards != true) return
        val previous = state.value!!
        runOperation(R.string.wallet_setup_loading_cards) {
            val response = withContext(Dispatchers.IO) {
                WalletBackendClient(getApplication(), account, sessionId).listUntokenizedCards()
            }
            cardsResponse = response
            val multipleProfiles = response.show_customer_selector == true
            // gohc.getView / SelectUntokenizedCardChimeraActivity.i(): eligibility status 3 is the
            // ineligible bucket shown greyed; any other value (including absent) is selectable.
            val rows = response.cards.mapIndexed { index, card ->
                WalletCardRow(
                    label = card.label?.takeIf(String::isNotBlank) ?: text(R.string.wallet_setup_saved_card, index + 1),
                    eligible = card.eligibility?.status != 3
                )
            }
            Log.d("GmsWalletSetup", "Saved cards: count=${rows.size}, eligible=${rows.count { it.eligible }}, " +
                    "multipleProfiles=$multipleProfiles")
            // The customer selector is non-blocking: the response already carries this profile's cards.
            val message = when {
                rows.isEmpty() -> text(R.string.wallet_setup_no_cards)
                else -> text(R.string.wallet_setup_cards_listed, rows.size)
            }
            state.value = previous.copy(
                message = message,
                cards = rows,
                selectedCard = rows.indexOfFirst { it.eligible },
                multipleProfiles = multipleProfiles
            )
        }
    }

    /**
     * Stage 3 attestation probe: fetch a server nonce and run the DroidGuard
     * `tapandpay_attestation` flow, reporting only whether a verdict was produced and its size.
     * Deliberately does NOT send the verdict anywhere and does not tokenize, so it cannot mutate
     * the payment account. It answers the one open question: can microG attest for this flow?
     */
    fun testAttestation(account: Account) {
        if (account != checkedAccount) return
        runOperation(R.string.wallet_setup_attesting) {
            val previous = state.value!!
            val verdictLength = withContext(Dispatchers.IO) {
                val nonce = WalletBackendClient(getApplication(), account, sessionId).getNonce()
                WalletAttestation.attest(getApplication(), nonce).verdict?.length ?: 0
            }
            Log.d("GmsWalletSetup", "Attestation verdict produced: length=$verdictLength")
            state.value = previous.copy(message = text(R.string.wallet_setup_attested, verdictLength))
        }
    }

    fun selectCard(index: Int) {
        val current = state.value ?: return
        if (current.busy || index !in current.cards.indices || !current.cards[index].eligible) return
        if (current.selectedCard == index) return
        state.value = current.copy(selectedCard = index)
    }

    fun acceptTerms(account: Account) {
        if (account != checkedAccount || state.value?.termsAccepted == true) return
        val previous = state.value ?: return
        val terms = previous.terms ?: return
        runOperation(R.string.wallet_setup_accepting_terms) {
            withContext(Dispatchers.IO) { WalletBackendClient(getApplication(), account, sessionId).acceptTerms(terms) }
            state.value = previous.copy(message = text(R.string.wallet_setup_terms_accepted), termsAccepted = true)
        }
    }

    private fun runOperation(messageId: Int, action: suspend () -> Unit) {
        if (state.value?.busy == true) return
        val previous = state.value!!
        state.value = previous.copy(message = text(messageId), busy = true)
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WalletAuthorizationRequired) {
                authorization = e.resolution
                state.value = if (checkedAccount == null) WalletSetupState(text(R.string.wallet_setup_authorization))
                    else previous.copy(message = text(R.string.wallet_setup_authorization))
            } catch (e: Exception) {
                // Structured backend details stay in e.error; never log their opaque bytes or server strings.
                val description = if (e is WalletBackendException) {
                    Log.w("GmsWalletSetup", e.message.orEmpty())
                    // Server text is shown only to the user, not written to logcat.
                    e.message.orEmpty() + "\n" + listOfNotNull(e.error.field2, e.error.field3, e.error.field5)
                        .filter(String::isNotBlank).joinToString("\n").take(1000)
                }
                    else if (e is IOException) e.message.orEmpty() else e.javaClass.simpleName
                Log.w("GmsWalletSetup", "Setup stopped: ${e.javaClass.simpleName}")
                val message = text(R.string.wallet_setup_failed, description)
                state.value = if (checkedAccount == null) WalletSetupState(message) else previous.copy(message = message)
            }
        }
    }

    companion object {
        // Multiple entry points can open separate activities in :ui. Check the cache under
        // one lock so they don't concurrently register the same account/device.
        private val registrationMutex = Mutex()
    }

}
