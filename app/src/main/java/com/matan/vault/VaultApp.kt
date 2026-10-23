package com.matan.vault

import android.app.Application
import com.matan.vault.reminders.ExpiryWorker
import com.matan.vault.share.ShareHelper

class VaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShareHelper.clearShared(this)
        ExpiryWorker.createChannel(this)
        ExpiryWorker.schedule(this)
    }
}
