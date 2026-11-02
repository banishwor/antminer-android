package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.domain.model.MinerProfile
import org.junit.Assert.*
import org.junit.Test

class MinerProfileManagerTest {

    @Test
    fun saveAndSwitchProfiles_worksCorrectly() {
        val manager = MinerProfileManager()
        val p1 = MinerProfile(id = "uuid-1", displayName = "S19k Pro #1", host = "192.168.1.2")
        val p2 = MinerProfile(id = "uuid-2", displayName = "S19k Pro #2", host = "192.168.1.3")

        manager.saveOrUpdateProfile(p1)
        assertEquals(1, manager.savedProfiles.value.size)
        assertEquals(p1.id, manager.activeProfileId.value)

        manager.saveOrUpdateProfile(p2)
        assertEquals(2, manager.savedProfiles.value.size)
        assertEquals(p2.id, manager.activeProfileId.value)

        manager.selectProfile(p1.id)
        assertEquals(p1.id, manager.activeProfileId.value)
        assertEquals(p1, manager.getActiveProfile())

        manager.removeProfile(p1.id)
        assertEquals(1, manager.savedProfiles.value.size)
        assertEquals(p2.id, manager.activeProfileId.value)
    }

    @Test
    fun sameIpDifferentLocations_canCoexistSimultaneously() {
        val manager = MinerProfileManager()
        val homeMiner = MinerProfile(
            id = "uuid-home",
            nickname = "Home S19k",
            location = "Home",
            host = "192.168.1.2"
        )
        val warehouseMiner = MinerProfile(
            id = "uuid-warehouse",
            nickname = "Warehouse S19k",
            location = "Warehouse",
            host = "192.168.1.2"
        )

        manager.saveOrUpdateProfile(homeMiner)
        manager.saveOrUpdateProfile(warehouseMiner)

        // Both miners with identical IPs must coexist because their IDs/locations are distinct
        assertEquals(2, manager.savedProfiles.value.size)
        assertEquals("Home S19k", homeMiner.effectiveName)
        assertEquals("Warehouse S19k", warehouseMiner.effectiveName)

        // Update nickname
        manager.updateNicknameAndLocation("uuid-home", "Living Room S19k", "Home Farm")
        val updated = manager.savedProfiles.value.first { it.id == "uuid-home" }
        assertEquals("Living Room S19k", updated.nickname)
        assertEquals("Home Farm", updated.location)
    }

    @Test
    fun workerNameMatching_updatesIpAndPreservesNickname() {
        val manager = MinerProfileManager()
        val original = MinerProfile(
            id = "uuid-1",
            workerName = "banishwor.001",
            nickname = "Left Rack S19",
            location = "Farm A",
            host = "192.168.1.102",
            password = "custom_password"
        )
        manager.saveOrUpdateProfile(original)
        assertEquals(1, manager.savedProfiles.value.size)

        // Router reboots, new IP 192.168.1.145 is discovered for banishwor.001
        val newDiscovery = MinerProfile(
            workerName = "banishwor.001",
            host = "192.168.1.145",
            displayName = "Antminer S19 Pro"
        )
        manager.saveOrUpdateProfile(newDiscovery)

        // Must NOT create a second profile, must update IP, and must preserve nickname & password
        assertEquals(1, manager.savedProfiles.value.size)
        val updated = manager.savedProfiles.value.first()
        assertEquals("192.168.1.145", updated.host)
        assertEquals("Left Rack S19", updated.nickname)
        assertEquals("Farm A", updated.location)
        assertEquals("custom_password", updated.password)
        assertEquals("banishwor.001", updated.workerName)
    }
}
