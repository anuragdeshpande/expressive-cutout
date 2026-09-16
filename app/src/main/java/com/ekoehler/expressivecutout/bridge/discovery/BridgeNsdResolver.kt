package com.ekoehler.expressivecutout.bridge.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Resolves local Droppy Mac companion services on the LAN using Android Network Service Discovery (mDNS).
 */
class BridgeNsdResolver(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /**
     * Resolves the IP address and port for the given Mac service instance name.
     *
     * @param serviceName Name of the service instance from the pairing record.
     * @param timeoutMillis Maximum time to wait for mDNS discovery.
     * @return Discovered `host:port` string, or null if timed out or failed.
     */
    suspend fun resolveService(
        serviceName: String,
        timeoutMillis: Long = DEFAULT_RESOLVE_TIMEOUT_MS,
    ): String? = withContext(Dispatchers.IO) {
        val manager = nsdManager ?: return@withContext null

        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var isDiscoveryActive = false

                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        // Keep searching if resolve of a particular candidate failed
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                        val host = serviceInfo?.host?.hostAddress
                        val port = serviceInfo?.port
                        if (host != null && port != null && port > 0) {
                            if (continuation.isActive) {
                                continuation.resume("$host:$port")
                            }
                        }
                    }
                }

                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        isDiscoveryActive = false
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        isDiscoveryActive = false
                    }

                    override fun onDiscoveryStarted(serviceType: String?) {
                        isDiscoveryActive = true
                    }

                    override fun onDiscoveryStopped(serviceType: String?) {
                        isDiscoveryActive = false
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        if (serviceInfo?.serviceName == serviceName) {
                            runCatching {
                                manager.resolveService(serviceInfo, resolveListener)
                            }
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                        // No action required on lost service during active discovery
                    }
                }

                continuation.invokeOnCancellation {
                    if (isDiscoveryActive) {
                        runCatching { manager.stopServiceDiscovery(discoveryListener) }
                    }
                }

                try {
                    manager.discoverServices(
                        SERVICE_TYPE,
                        NsdManager.PROTOCOL_DNS_SD,
                        discoveryListener,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_androidbridge._tcp."
        const val DEFAULT_RESOLVE_TIMEOUT_MS = 6000L
    }
}
