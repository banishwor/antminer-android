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
    fun sameIpSameDefaultLocation_differentWorkers_neverMerge() {
        // v0.5.3 regression, the exact cross-site collision: banishwor.001
        // (Sabaltongba) and bishambhor.001 (Bishambhor's site) are
        // different machines at the SAME private IP, both under the
        // default location. The old host + location match merged the
        // second save into the first profile and overwrote its worker
        // identity. They must coexist as two profiles, untouched.
        val manager = MinerProfileManager()
        val sabaltongba = MinerProfile(
            id = "uuid-banishwor",
            workerName = "banishwor.001",
            displayName = "Antminer S19k Pro",
            location = "Default Location",
            host = "192.168.1.2"
        )
        val bishambhor = MinerProfile(
            id = "uuid-bishambhor",
            workerName = "bishambhor.001",
            displayName = "Antminer S19j Pro",
            location = "Default Location",
            host = "192.168.1.2"
        )

        manager.saveOrUpdateProfile(sabaltongba)
        manager.saveOrUpdateProfile(bishambhor)

        assertEquals(2, manager.savedProfiles.value.size)
        val savedBanishwor = manager.savedProfiles.value.first { it.id == "uuid-banishwor" }
        val savedBishambhor = manager.savedProfiles.value.first { it.id == "uuid-bishambhor" }
        assertEquals("banishwor.001", savedBanishwor.workerName)
        assertEquals("Antminer S19k Pro", savedBanishwor.displayName)
        assertEquals("bishambhor.001", savedBishambhor.workerName)
        assertEquals("Antminer S19j Pro", savedBishambhor.displayName)
        assertEquals("192.168.1.2", savedBanishwor.host)
        assertEquals("192.168.1.2", savedBishambhor.host)
    }

    @Test
    fun workerNameMatching_isCaseInsensitive_andStillUpdatesIp() {
        // Same worker under a different case must still be recognised
        // as the same machine (DHCP move), not a second profile.
        val manager = MinerProfileManager()
        manager.saveOrUpdateProfile(
            MinerProfile(
                id = "uuid-1",
                workerName = "banishwor.001",
                location = "Farm A",
                host = "192.168.1.102"
            )
        )
        manager.saveOrUpdateProfile(
            MinerProfile(workerName = "  Banishwor.001 ", host = "192.168.1.145")
        )

        assertEquals(1, manager.savedProfiles.value.size)
        assertEquals("192.168.1.145", manager.savedProfiles.value.first().host)
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
