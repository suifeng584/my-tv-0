package com.lizongying.mytv0

import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

object PortUtil {

    fun findFreePort(): Int {
        var port: Int
        var socket: ServerSocket? = null
        try {
            socket = ServerSocket(10086)
            port = socket.localPort
        } catch (e: IOException) {
            try {
                socket = ServerSocket(0)
                port = socket.localPort
            } catch (e: IOException) {
               return -1
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return port
    }

    fun lan(): String? {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val inetAddresses = networkInterfaces.nextElement().inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (inetAddress is Inet4Address) {
                    if (inetAddress.hostAddress == "127.0.0.1") {
                        continue
                    }
                    return inetAddress.hostAddress
                }
            }
        }
        return null
    }
}