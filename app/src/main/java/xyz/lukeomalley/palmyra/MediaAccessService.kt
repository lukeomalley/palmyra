package xyz.lukeomalley.palmyra

import android.service.notification.NotificationListenerService

/** Android binds this only after the owner enables notification access. No notifications are read. */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        onAccessChanged?.invoke()
    }

    override fun onListenerDisconnected() {
        onAccessChanged?.invoke()
    }

    companion object {
        // Home installs this callback only while resumed. No activity is retained when hidden.
        internal var onAccessChanged: (() -> Unit)? = null
    }
}
