package com.talkiwalki.junior.network

/** Pure state holder used by NSD callbacks; it makes duplicate/lost service handling testable. */
class DiscoveredGroupRegistry {
    private val groups = linkedMapOf<String, DiscoveredGroup>()
    fun contains(serviceName: String): Boolean = groups.containsKey(serviceName)
    fun add(group: DiscoveredGroup) {
        if (groups.containsKey(group.serviceName) || groups.size < MAX_GROUPS) groups[group.serviceName] = group
    }
    fun remove(serviceName: String) { groups.remove(serviceName) }
    fun clear() { groups.clear() }
    fun values(): List<DiscoveredGroup> = groups.values.sortedBy { it.groupName }
    private companion object { const val MAX_GROUPS = 32 }
}
