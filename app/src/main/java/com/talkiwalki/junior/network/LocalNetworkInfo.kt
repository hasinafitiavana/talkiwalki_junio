package com.talkiwalki.junior.network

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetworkInfo {
    fun localIpv4Address(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) return@runCatching address.hostAddress
            }
        }
        null
    }.getOrNull()
}
