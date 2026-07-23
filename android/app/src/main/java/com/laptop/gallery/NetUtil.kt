package com.laptop.gallery

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

/** Best-effort local IPv4 address for display, e.g. "192.168.1.5". Never throws. */
object NetUtil {

    fun localIp(context: Context): String? =
        fromConnectivity(context) ?: fromInterfaces()

    /** Preferred: the active network's link address. Needs ACCESS_NETWORK_STATE. */
    private fun fromConnectivity(context: Context): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val props = network?.let { cm.getLinkProperties(it) }
        props?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    /** Fallback: scan interfaces directly. Requires no special permission. */
    private fun fromInterfaces(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
