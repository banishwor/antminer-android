package com.example.antminerconnectiontest.relay

/**
 * Receives the relay's human-readable status as (title, content) pairs.
 *
 * The engine produces the exact status text; the host renders it —
 * the main app posts it as the foreground service's persistent
 * notification, Relay Lite will show it on its status screen. Keeping
 * the text in the engine means both hosts report identically.
 */
fun interface RelayStatusListener {
    fun onRelayStatus(title: String, content: String)
}
