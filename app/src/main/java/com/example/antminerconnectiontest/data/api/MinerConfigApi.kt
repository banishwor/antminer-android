package com.example.antminerconnectiontest.data.api

import com.example.antminerconnectiontest.domain.model.MinerProfile

/**
 * Future-facing configuration interface for Antminer devices.
 *
 * NOTE: Write endpoints (such as updating pools, changing network settings, or rebooting)
 * will be implemented once write requests are explicitly verified against hardware.
 */
interface MinerConfigApi {
    suspend fun updatePool(profile: MinerProfile, poolIndex: Int, url: String, worker: String, pass: String): Result<Boolean>
    suspend fun rebootMiner(profile: MinerProfile): Result<Boolean>
    suspend fun updateNetworkSettings(profile: MinerProfile, ip: String, mask: String, gateway: String): Result<Boolean>
}

/**
 * Placeholder implementation of MinerConfigApi pending verified write API payloads.
 */
class AntminerConfigApiPlaceholder : MinerConfigApi {
    override suspend fun updatePool(profile: MinerProfile, poolIndex: Int, url: String, worker: String, pass: String): Result<Boolean> {
        return Result.failure(UnsupportedOperationException("Pool editing will be enabled in a future version."))
    }

    override suspend fun rebootMiner(profile: MinerProfile): Result<Boolean> {
        return Result.failure(UnsupportedOperationException("Miner reboot will be enabled in a future version."))
    }

    override suspend fun updateNetworkSettings(profile: MinerProfile, ip: String, mask: String, gateway: String): Result<Boolean> {
        return Result.failure(UnsupportedOperationException("Network configuration will be enabled in a future version."))
    }
}
