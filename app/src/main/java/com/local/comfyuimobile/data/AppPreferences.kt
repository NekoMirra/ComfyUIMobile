package com.local.comfyuimobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.local.comfyuimobile.network.LanAddress
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.CacheOutputRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "comfy_mobile")

data class StoredSettings(
    val onboardingCompleted: Boolean = false,
    val profiles: List<ServerProfile> = emptyList(),
    val pinnedServerUrls: Set<String> = emptySet(),
    val activeServerUrl: String = "",
    val promptHistory: List<String> = emptyList(),
    val submittedJobs: Set<String> = emptySet(),
    val trackedJobs: Set<String> = emptySet(),
    val autoSaveResults: Boolean = false,
    val localDraftsEnabled: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val recentWorkflows: List<String> = emptyList(),
    val cacheOutputRules: List<CacheOutputRule> = emptyList(),
    val cacheClearedAt: Long = 0L,
    val favoriteResultKeys: Set<String> = emptySet(),
)

class AppPreferences(context: Context, private val store: DataStore<Preferences> = context.dataStore) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed_v1")
        val profiles = stringPreferencesKey("profiles")
        val activeServerUrl = stringPreferencesKey("active_server_url")
        val promptHistory = stringPreferencesKey("prompt_history")
        val submittedJobs = stringPreferencesKey("submitted_jobs")
        val trackedJobs = stringPreferencesKey("tracked_jobs_v1")
        val autoSaveResults = booleanPreferencesKey("auto_save_results")
        val localDraftsEnabled = booleanPreferencesKey("local_drafts_enabled")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val recentWorkflow = stringPreferencesKey("recent_workflow")
        val recentWorkflows = stringPreferencesKey("recent_workflows")
        val cacheOutputRules = stringPreferencesKey("cache_output_rules")
        val cacheClearedAt = longPreferencesKey("cache_cleared_at")
        val favoriteResultKeys = stringPreferencesKey("favorite_result_keys")
    }

    val settings: Flow<StoredSettings> = store.data.onStart {
        // Persist the migration once, so deleting the last old server later cannot
        // unexpectedly turn an established install back into a first launch.
        store.edit { preferences ->
            if (preferences[Keys.onboardingCompleted] == null) {
                preferences[Keys.onboardingCompleted] = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty()).isNotEmpty()
                    || !preferences[Keys.recentWorkflow].isNullOrBlank()
                    || !preferences[Keys.recentWorkflows].isNullOrBlank()
            }
        }
    }.map { preferences ->
        val storedProfiles = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
        StoredSettings(
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
            profiles = ServerProfileStore.order(storedProfiles).map { it.profile },
            pinnedServerUrls = storedProfiles.filter { it.pinned }.map { it.profile.baseUrl }.toSet(),
            activeServerUrl = preferences[Keys.activeServerUrl].orEmpty(),
            promptHistory = decodeStrings(preferences[Keys.promptHistory].orEmpty()).take(PromptHistory.MAX_SIZE),
            submittedJobs = decodeStrings(preferences[Keys.submittedJobs].orEmpty()).toSet(),
            trackedJobs = decodeStrings(preferences[Keys.trackedJobs].orEmpty()).toSet(),
            autoSaveResults = preferences[Keys.autoSaveResults] ?: false,
            localDraftsEnabled = preferences[Keys.localDraftsEnabled] ?: false,
            lastUpdateCheck = preferences[Keys.lastUpdateCheck] ?: 0L,
            recentWorkflows = decodeStrings(preferences[Keys.recentWorkflows].orEmpty())
                .ifEmpty { listOfNotNull(preferences[Keys.recentWorkflow]?.takeIf(String::isNotBlank)) }
                .take(RecentWorkflows.MAX_SIZE),
            cacheOutputRules = decodeCacheOutputRules(preferences[Keys.cacheOutputRules].orEmpty()),
            cacheClearedAt = preferences[Keys.cacheClearedAt] ?: 0L,
            favoriteResultKeys = decodeStrings(preferences[Keys.favoriteResultKeys].orEmpty()).toSet(),
        )
    }

    suspend fun saveServer(profile: ServerProfile) {
        store.edit { preferences ->
            val current = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
            val merged = ServerProfileStore.merge(current, profile).take(ServerProfileStore.MAX_PROFILES)
            preferences[Keys.profiles] = ServerProfileStore.encode(merged)
            preferences[Keys.activeServerUrl] = profile.baseUrl
        }
    }

    suspend fun completeOnboarding() {
        store.edit { it[Keys.onboardingCompleted] = true }
    }

    /** Commit an explicit connection choice before network work can fail or be cancelled. */
    suspend fun rememberServerAddress(address: String) {
        val normalized = LanAddress.normalize(address)
        store.edit { preferences ->
            val current = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
            val existing = current.firstOrNull { it.profile.baseUrl == normalized }
            val profile = existing?.profile ?: ServerProfile(
                java.util.UUID.nameUUIDFromBytes(normalized.toByteArray()).toString(),
                LanAddress.serverHost(normalized),
                normalized,
            )
            val merged = ServerProfileStore.merge(current, profile).take(ServerProfileStore.MAX_PROFILES)
            preferences[Keys.profiles] = ServerProfileStore.encode(merged)
            preferences[Keys.activeServerUrl] = normalized
        }
    }

    suspend fun removeServer(baseUrl: String) {
        store.edit { preferences ->
            val remaining = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
                .filterNot { it.profile.baseUrl == baseUrl }
            preferences[Keys.profiles] = ServerProfileStore.encode(remaining)
            if (preferences[Keys.activeServerUrl] == baseUrl) preferences.remove(Keys.activeServerUrl)
        }
    }

    /** Rename a saved server. A blank name is ignored so the list never shows empty labels. */
    suspend fun renameServer(baseUrl: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        store.edit { preferences ->
            val current = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
            preferences[Keys.profiles] = ServerProfileStore.encode(ServerProfileStore.rename(current, baseUrl, trimmed))
        }
    }

    /** Pin a server to the top of the list, or release it back to the normal order. */
    suspend fun setServerPinned(baseUrl: String, pinned: Boolean) {
        store.edit { preferences ->
            val current = ServerProfileStore.decode(preferences[Keys.profiles].orEmpty())
            preferences[Keys.profiles] = ServerProfileStore.encode(ServerProfileStore.setPinned(current, baseUrl, pinned))
        }
    }

    suspend fun savePromptHistory(history: List<String>) {
        store.edit { it[Keys.promptHistory] = encodeStrings(history.take(PromptHistory.MAX_SIZE)) }
    }

    suspend fun saveSubmittedJobs(ids: Set<String>) {
        store.edit { it[Keys.submittedJobs] = encodeStrings(ids.toList().takeLast(200)) }
    }

    suspend fun setTaskTracked(server: String, id: String, tracked: Boolean) {
        val key = com.local.comfyuimobile.network.TaskLifecycle.key(server, id)
        store.edit { values ->
            val current = decodeStrings(values[Keys.trackedJobs].orEmpty()).toSet()
            values[Keys.trackedJobs] = encodeStrings(if (tracked) current + key else current - key)
        }
    }

    suspend fun setAutoSaveResults(enabled: Boolean) {
        store.edit { it[Keys.autoSaveResults] = enabled }
    }

    suspend fun setLocalDraftsEnabled(enabled: Boolean) {
        store.edit { it[Keys.localDraftsEnabled] = enabled }
    }

    suspend fun setLastUpdateCheck(timestamp: Long) {
        store.edit { it[Keys.lastUpdateCheck] = timestamp }
    }

    suspend fun setRecentWorkflow(path: String, replacedPath: String? = null) {
        store.edit { preferences ->
            val current = decodeStrings(preferences[Keys.recentWorkflows].orEmpty())
                .ifEmpty { listOfNotNull(preferences[Keys.recentWorkflow]?.takeIf(String::isNotBlank)) }
            val updated = RecentWorkflows.add(current, path, replacedPath)
            preferences[Keys.recentWorkflow] = path
            preferences[Keys.recentWorkflows] = encodeStrings(updated)
        }
    }

    suspend fun removeRecentWorkflow(path: String) {
        store.edit { preferences ->
            val updated = RecentWorkflows.remove(
                decodeStrings(preferences[Keys.recentWorkflows].orEmpty()),
                path,
            )
            preferences[Keys.recentWorkflows] = encodeStrings(updated)
            if (preferences[Keys.recentWorkflow] == path) {
                updated.firstOrNull()?.let { preferences[Keys.recentWorkflow] = it }
                    ?: preferences.remove(Keys.recentWorkflow)
            }
        }
    }

    suspend fun saveCacheOutputRules(rules: List<CacheOutputRule>) {
        store.edit { preferences ->
            preferences[Keys.cacheOutputRules] = JSONArray().apply {
                rules.forEach { rule ->
                    put(
                        JSONObject()
                            .put("serverUrl", rule.serverUrl)
                            .put("workflowPath", rule.workflowPath)
                            .put("workflowName", rule.workflowName)
                            .put("nodeId", rule.nodeId)
                            .put("nodeTitle", rule.nodeTitle)
                            .put("nodeType", rule.nodeType)
                            .put("enabled", rule.enabled),
                    )
                }
            }.toString()
        }
    }

    suspend fun setCacheClearedAt(timestamp: Long) {
        store.edit { it[Keys.cacheClearedAt] = timestamp }
    }

    suspend fun saveFavoriteResultKeys(keys: Set<String>) {
        store.edit { it[Keys.favoriteResultKeys] = encodeStrings(keys.take(1_000)) }
    }

    private fun decodeStrings(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun encodeStrings(values: Collection<String>): String = JSONArray(values).toString()

    private fun decodeCacheOutputRules(raw: String): List<CacheOutputRule> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        List(array.length()) { index ->
            array.getJSONObject(index).let { item ->
                CacheOutputRule(
                    serverUrl = item.optString("serverUrl"),
                    workflowPath = item.optString("workflowPath"),
                    workflowName = item.optString("workflowName"),
                    nodeId = item.optString("nodeId"),
                    nodeTitle = item.optString("nodeTitle"),
                    nodeType = item.optString("nodeType"),
                    enabled = item.optBoolean("enabled", true),
                )
            }
        }
            .filter { it.serverUrl.isNotBlank() && it.nodeType.isNotBlank() }
            .groupBy { "${it.serverUrl}/${it.nodeType}" }
            .values
            .map { matching ->
                matching.first().copy(
                    workflowPath = "",
                    workflowName = "",
                    nodeId = "",
                    enabled = matching.any { it.enabled },
                )
            }
    }.getOrDefault(emptyList())
}

/**
 * A saved server together with the metadata the server list needs.
 *
 * [pinned] keeps a server at the top of the list and [lastConnectedAt] records
 * the last successful connection. Entries written by older builds simply do not
 * carry those keys; they are decoded with the defaults below and rewritten in
 * the current format on the next save.
 */
data class StoredServerProfile(
    val profile: ServerProfile,
    val pinned: Boolean = false,
    val lastConnectedAt: Long = profile.lastSeen,
)

/**
 * Pure codec and ordering rules for the saved server list.
 *
 * The encoding is a JSON array, one object per server. The first version only
 * stored id / name / baseUrl / lastSeen / comfyVersion; the pinned flag and the
 * last connection time were added later and default to safe values.
 */
object ServerProfileStore {
    const val MAX_PROFILES = 12

    fun decode(raw: String): List<StoredServerProfile> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val baseUrl = item.optString("baseUrl")
                if (baseUrl.isBlank()) return@repeat
                val lastSeen = item.optLong("lastSeen", 0L)
                add(
                    StoredServerProfile(
                        profile = ServerProfile(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            baseUrl = baseUrl,
                            lastSeen = lastSeen,
                            comfyVersion = item.optString("comfyVersion"),
                        ),
                        pinned = item.optBoolean("pinned", false),
                        lastConnectedAt = item.optLong("lastConnectedAt", lastSeen),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encode(profiles: List<StoredServerProfile>): String = JSONArray().apply {
        profiles.forEach { stored ->
            put(
                JSONObject()
                    .put("id", stored.profile.id)
                    .put("name", stored.profile.name)
                    .put("baseUrl", stored.profile.baseUrl)
                    .put("lastSeen", stored.profile.lastSeen)
                    .put("comfyVersion", stored.profile.comfyVersion)
                    .put("pinned", stored.pinned)
                    .put("lastConnectedAt", stored.lastConnectedAt),
            )
        }
    }.toString()

    /** Pinned servers first, then the most recently connected ones. */
    fun order(profiles: List<StoredServerProfile>): List<StoredServerProfile> =
        profiles.sortedWith(
            compareByDescending<StoredServerProfile> { it.pinned }
                .thenByDescending { it.lastConnectedAt },
        )

    /** Puts [incoming] first and keeps the pin flag of the entry it replaces. */
    fun merge(current: List<StoredServerProfile>, incoming: ServerProfile): List<StoredServerProfile> {
        val existing = current.firstOrNull { it.profile.baseUrl == incoming.baseUrl }
        val stored = StoredServerProfile(
            profile = incoming.copy(name = incoming.name.ifBlank { existing?.profile?.name ?: incoming.name }),
            pinned = existing?.pinned ?: false,
            lastConnectedAt = maxOf(existing?.lastConnectedAt ?: 0L, incoming.lastSeen),
        )
        return listOf(stored) + current.filterNot { it.profile.baseUrl == incoming.baseUrl }
    }

    fun rename(profiles: List<StoredServerProfile>, baseUrl: String, name: String): List<StoredServerProfile> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return profiles
        return profiles.map { stored ->
            if (stored.profile.baseUrl != baseUrl) {
                stored
            } else {
                stored.copy(profile = stored.profile.copy(name = trimmed))
            }
        }
    }

    fun setPinned(profiles: List<StoredServerProfile>, baseUrl: String, pinned: Boolean): List<StoredServerProfile> =
        profiles.map { stored ->
            if (stored.profile.baseUrl != baseUrl) {
                stored
            } else {
                stored.copy(pinned = pinned)
            }
        }
}
