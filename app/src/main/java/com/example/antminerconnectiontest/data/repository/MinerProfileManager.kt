package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.data.storage.MinerStorageManager
import com.example.antminerconnectiontest.domain.model.MinerMerge
import com.example.antminerconnectiontest.domain.model.MinerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages multiple saved miner profiles and persists them securely.
 */
class MinerProfileManager(
    private val storageManager: MinerStorageManager? = null
) {

    private val _savedProfiles = MutableStateFlow<List<MinerProfile>>(emptyList())
    val savedProfiles: StateFlow<List<MinerProfile>> = _savedProfiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    init {
        // Restore profiles from persistent storage
        if (storageManager != null) {
            val loaded = storageManager.loadSavedProfiles()
            _savedProfiles.value = loaded
            val lastActiveId = storageManager.getLastActiveProfileId()
            if (lastActiveId != null && loaded.any { it.id == lastActiveId }) {
                _activeProfileId.value = lastActiveId
            } else {
                _activeProfileId.value = loaded.firstOrNull()?.id
            }
        }
    }

    fun saveOrUpdateProfile(profile: MinerProfile) {
        var mergedId = profile.id
        _savedProfiles.update { currentList ->
            // Match by unique ID, by workerName, or — only when NEITHER
            // side knows a worker name — by (host + location).
            //
            // Identity safeguard (v0.5.3): when both profiles carry
            // non-blank worker names and they DIFFER, they are different
            // machines and must NEVER merge — not even via the common
            // host + "Default Location" pair. Private IPs repeat across
            // sites (banishwor.001 and bishambhor.001 can both live at
            // 192.168.1.2), so an address must never override a proven
            // worker identity. Worker comparisons are normalised
            // (trim + case-insensitive), so a DHCP move of the same
            // worker still merges and updates the address.
            val incomingWorker = MinerMerge.normalizeWorkerName(profile.workerName)
            val existingIndex = currentList.indexOfFirst { existing ->
                val existingWorker = MinerMerge.normalizeWorkerName(existing.workerName)
                when {
                    existing.id == profile.id -> true
                    incomingWorker.isNotBlank() && existingWorker.isNotBlank() ->
                        incomingWorker == existingWorker
                    incomingWorker.isBlank() && existingWorker.isBlank() ->
                        existing.host == profile.host && existing.location == profile.location
                    // Exactly one side knows its worker: the known
                    // identity cannot be contradicted by an address, and
                    // the anonymous side cannot vouch for the machine —
                    // no merge.
                    else -> false
                }
            }
            if (existingIndex >= 0) {
                val existing = currentList[existingIndex]
                val merged = profile.copy(
                    id = existing.id,
                    nickname = if (profile.nickname.isNotBlank()) profile.nickname else existing.nickname,
                    location = if (profile.location.isNotBlank() && profile.location != "Default Location") profile.location else existing.location,
                    workerName = if (profile.workerName.isNotBlank()) profile.workerName else existing.workerName,
                    macAddress = if (profile.macAddress.isNotBlank()) profile.macAddress else existing.macAddress,
                    password = if (profile.password.isNotBlank() && profile.password != "root") profile.password else existing.password
                )
                mergedId = merged.id
                currentList.toMutableList().apply {
                    this[existingIndex] = merged
                }
            } else {
                currentList + profile
            }
        }
        // The active id must be the MERGED profile's id. This previously stored
        // the incoming profile's id, which a merge discards — getActiveProfile()
        // then found no saved profile with that id and returned null.
        _activeProfileId.value = mergedId
        persist()
    }

    fun updateProfileHostByWorkerName(workerName: String, newHost: String): MinerProfile? {
        if (workerName.isBlank() || newHost.isBlank()) return null
        var updatedProfile: MinerProfile? = null
        _savedProfiles.update { currentList ->
            currentList.map {
                if (it.workerName == workerName) {
                    val updated = it.copy(host = newHost, lastConnected = System.currentTimeMillis())
                    updatedProfile = updated
                    updated
                } else {
                    it
                }
            }
        }
        persist()
        return updatedProfile
    }

    fun updateNicknameAndLocation(profileId: String, newNickname: String, newLocation: String) {
        _savedProfiles.update { currentList ->
            currentList.map {
                if (it.id == profileId) {
                    it.copy(nickname = newNickname, location = newLocation)
                } else {
                    it
                }
            }
        }
        persist()
    }

    fun selectProfile(profileId: String): MinerProfile? {
        val selected = _savedProfiles.value.firstOrNull { it.id == profileId }
        if (selected != null) {
            _activeProfileId.value = profileId
            storageManager?.setLastActiveProfileId(profileId)
        }
        return selected
    }

    fun removeProfile(profileId: String) {
        _savedProfiles.update { currentList ->
            currentList.filterNot { it.id == profileId }
        }
        if (_activeProfileId.value == profileId) {
            val next = _savedProfiles.value.firstOrNull()?.id
            _activeProfileId.value = next
            storageManager?.setLastActiveProfileId(next)
        }
        persist()
    }

    fun getActiveProfile(): MinerProfile? {
        val id = _activeProfileId.value ?: return null
        return _savedProfiles.value.firstOrNull { it.id == id }
    }

    private fun persist() {
        storageManager?.saveProfiles(_savedProfiles.value)
        storageManager?.setLastActiveProfileId(_activeProfileId.value)
    }
}
