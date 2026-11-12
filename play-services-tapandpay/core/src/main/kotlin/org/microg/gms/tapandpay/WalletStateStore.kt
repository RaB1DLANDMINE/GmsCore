/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.tapandpay

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.android.gms.tapandpay.firstparty.AccountInfo
import org.microg.gms.checkin.LastCheckinInfo

/** SQLite, rather than SharedPreferences: the Binder and :ui process share this state. */
class WalletStateStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "tapandpay-setup.db", null, 1) {
    private val context = context.applicationContext
    private val accounts = AccountManager.get(context.applicationContext)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE wallets (account TEXT PRIMARY KEY, android_id INTEGER NOT NULL, wallet_id TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun googleAccounts(): List<Account> = accounts.getAccountsByType("com.google").toList()

    fun activeAccount(): Account? {
        val name = readableDatabase.query("settings", arrayOf("value"), "name=?", arrayOf("active_account"), null, null, null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        return googleAccounts().firstOrNull { it.name == name }
    }

    fun selectAccount(name: String): Boolean {
        if (googleAccounts().none { it.name == name }) return false
        writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply {
            put("name", "active_account")
            put("value", name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun accountInfo(): AccountInfo? = activeAccount()?.let { account ->
        // Never substitute the email address for a GAIA ID.
        val id = accounts.getUserData(account, "GoogleUserId")?.takeIf { it.isNotBlank() }
        AccountInfo(id, account.name, 1)
    }

    fun walletId(account: Account): String? = readableDatabase.query(
        "wallets", arrayOf("wallet_id"), "account=? AND android_id=?",
        arrayOf(account.name, LastCheckinInfo.read(context).androidId.toString()), null, null, null
    ).use { if (it.moveToFirst()) it.getString(0).takeIf(String::isNotBlank) else null }

    fun saveWalletId(account: Account, walletId: String) {
        require(walletId.isNotBlank())
        val androidId = LastCheckinInfo.read(context).androidId
        require(androidId != 0L)
        writableDatabase.insertWithOnConflict("wallets", null, ContentValues().apply {
            put("account", account.name)
            put("android_id", androidId)
            put("wallet_id", walletId)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
