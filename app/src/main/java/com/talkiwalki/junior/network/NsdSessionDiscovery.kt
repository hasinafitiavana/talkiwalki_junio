package com.talkiwalki.junior.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredGroup(val serviceName: String, val groupName: String, val host: String, val port: Int)
data class DiscoverySnapshot(
    val isSearching: Boolean = false,
    val groups: List<DiscoveredGroup> = emptyList(),
    val resolvingCount: Int = 0,
    val error: String? = null
)

interface SessionDiscovery {
    val snapshot: StateFlow<DiscoverySnapshot>
    fun advertise(groupName: String, port: Int)
    fun stopAdvertising()
    fun startDiscovery()
    fun stopDiscovery()
}

class NoopSessionDiscovery : SessionDiscovery {
    override val snapshot = MutableStateFlow(DiscoverySnapshot())
    override fun advertise(groupName: String, port: Int) = Unit
    override fun stopAdvertising() = Unit
    override fun startDiscovery() = Unit
    override fun stopDiscovery() = Unit
}

/** NSD/DNS-SD lifecycle adapter. Service resolution is deliberately serialized for API 26+ OEM compatibility. */
class NsdSessionDiscovery(context: Context) : SessionDiscovery {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val mutableSnapshot = MutableStateFlow(DiscoverySnapshot())
    override val snapshot: StateFlow<DiscoverySnapshot> = mutableSnapshot.asStateFlow()
    private val monitor = Any()
    private val discovered = DiscoveredGroupRegistry()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val pendingNames = mutableSetOf<String>()
    private var activeResolution: String? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun advertise(groupName: String, port: Int) {
        stopAdvertising()
        val service = NsdServiceInfo().apply {
            serviceName = "TWJ-${groupName.take(32).replace(Regex("[^A-Za-z0-9 -]"), "")}-${UUID.randomUUID().toString().take(6)}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("group", groupName.take(32))
            setAttribute("protocol", "1")
        }
        Log.i(TAG, "Annonce NSD demandée : ${service.serviceName}, port=$port, type=$SERVICE_TYPE")
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { Log.i(TAG, "Annonce NSD enregistrée : ${info.serviceName}, port=${info.port}") }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.e(TAG, "Échec annonce NSD ($errorCode) : ${info.serviceName}"); reportError("Annonce réseau indisponible.") }
            override fun onServiceUnregistered(info: NsdServiceInfo) { Log.i(TAG, "Annonce NSD retirée : ${info.serviceName}") }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "Échec retrait annonce NSD ($errorCode)") }
        }
        runCatching { manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { registration = listener }
            .onFailure { Log.e(TAG, "Exception annonce NSD", it); reportError("Autorisation de réseau local requise.") }
    }

    override fun stopAdvertising() {
        registration?.let { runCatching { manager.unregisterService(it) }.onFailure { error -> Log.w(TAG, "Exception retrait annonce NSD", error) } }
        registration = null
    }

    override fun startDiscovery() {
        if (discovery != null) return
        acquireMulticastLock()
        synchronized(monitor) { publishLocked(searching = true, error = null) }
        Log.i(TAG, "Recherche NSD démarrée : type=$SERVICE_TYPE")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { Log.i(TAG, "Recherche NSD active : $regType") }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.e(TAG, "Échec démarrage recherche NSD ($errorCode)"); discovery = null; releaseMulticastLock(); reportError("Recherche réseau impossible.") }
            override fun onDiscoveryStopped(serviceType: String) { Log.i(TAG, "Recherche NSD arrêtée : $serviceType"); synchronized(monitor) { publishLocked(searching = false, error = null) } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "Échec arrêt recherche NSD ($errorCode)"); discovery = null; releaseMulticastLock() }
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service NSD trouvé : ${service.serviceName}, type=${service.serviceType}")
                if (sameServiceType(service.serviceType)) enqueueResolution(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service NSD perdu : ${service.serviceName}")
                synchronized(monitor) { discovered.remove(service.serviceName); pending.removeAll { it.serviceName == service.serviceName }; pendingNames.remove(service.serviceName); publishLocked(searching = true, error = null) }
            }
        }
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { discovery = listener }
            .onFailure { Log.e(TAG, "Exception recherche NSD", it); releaseMulticastLock(); reportError("Autorisation de réseau local requise.") }
    }

    override fun stopDiscovery() {
        val listener = discovery
        discovery = null
        listener?.let { runCatching { manager.stopServiceDiscovery(it) }.onFailure { error -> Log.w(TAG, "Exception arrêt recherche NSD", error) } }
        synchronized(monitor) { pending.clear(); pendingNames.clear(); activeResolution = null; discovered.clear(); mutableSnapshot.value = DiscoverySnapshot() }
        releaseMulticastLock()
        Log.i(TAG, "Recherche NSD nettoyée")
    }

    private fun enqueueResolution(service: NsdServiceInfo) {
        synchronized(monitor) {
            if (discovered.contains(service.serviceName) || activeResolution == service.serviceName || !pendingNames.add(service.serviceName)) return
            pending.addLast(service)
            publishLocked(searching = true, error = null)
            startNextResolutionLocked()
        }
    }

    private fun startNextResolutionLocked() {
        if (activeResolution != null || pending.isEmpty() || discovery == null) return
        val service = pending.removeFirst()
        activeResolution = service.serviceName
        Log.d(TAG, "Résolution NSD démarrée : ${service.serviceName}")
        runCatching {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Résolution NSD échouée ($errorCode) : ${info.serviceName}")
                    completeResolution(info.serviceName, null)
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val address = info.host as? Inet4Address
                    if (address == null) {
                        Log.w(TAG, "Résolution NSD sans IPv4 utilisable : ${info.serviceName}, host=${info.host}")
                        completeResolution(info.serviceName, null)
                        return
                    }
                    val group = info.attributes["group"]?.toString(Charsets.UTF_8)
                        ?: info.serviceName.removePrefix("TWJ-").substringBeforeLast('-')
                    val resolved = if (info.port in 1..65535) DiscoveredGroup(info.serviceName, group, address.hostAddress, info.port) else null
                    Log.i(TAG, "Résolution NSD réussie : ${info.serviceName} -> ${address.hostAddress}:${info.port}")
                    completeResolution(info.serviceName, resolved)
                }
            })
        }.onFailure { error -> Log.e(TAG, "Exception résolution NSD", error); completeResolution(service.serviceName, null) }
    }

    private fun completeResolution(serviceName: String, group: DiscoveredGroup?) {
        synchronized(monitor) {
            if (activeResolution != serviceName) return
            activeResolution = null; pendingNames.remove(serviceName)
            group?.let(discovered::add)
            publishLocked(searching = discovery != null, error = null)
            startNextResolutionLocked()
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        runCatching {
            wifiManager.createMulticastLock("TalkiWalkiNSD").apply { setReferenceCounted(false); acquire() }
        }.onSuccess { multicastLock = it; Log.i(TAG, "MulticastLock acquis") }
            .onFailure { Log.w(TAG, "MulticastLock indisponible ; NSD continue sans verrou", it) }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() }.onFailure { Log.w(TAG, "Échec libération MulticastLock", it) } }
        if (multicastLock != null) Log.i(TAG, "MulticastLock libéré")
        multicastLock = null
    }

    private fun publishLocked(searching: Boolean, error: String?) {
        mutableSnapshot.value = DiscoverySnapshot(searching, discovered.values(), pending.size + if (activeResolution == null) 0 else 1, error)
    }
    private fun reportError(message: String) { synchronized(monitor) { publishLocked(searching = false, error = message) } }
    private fun sameServiceType(type: String?) = type?.trimEnd('.')?.equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true) == true
    private companion object { const val TAG = "TalkiWalkiNSD"; const val SERVICE_TYPE = "_talkiwalki._tcp." }
}
