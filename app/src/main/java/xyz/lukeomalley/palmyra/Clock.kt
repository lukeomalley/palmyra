package xyz.lukeomalley.palmyra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.DateFormat
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Clock(
    private val context: Context,
    private val view: TextView,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var registered = false
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                update()
            }
        }

    fun start() {
        if (registered) return
        update()
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun stop() {
        if (registered) context.unregisterReceiver(receiver)
        registered = false
    }

    fun update() {
        val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        view.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now()))
    }
}
