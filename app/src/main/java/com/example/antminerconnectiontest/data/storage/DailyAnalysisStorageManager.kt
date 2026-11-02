package com.example.antminerconnectiontest.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.antminerconnectiontest.domain.model.DailyMinerAnalysis
import com.example.antminerconnectiontest.domain.model.DailyMinerEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists compact historical DailyMinerAnalysis records locally in app private storage.
 */
class DailyAnalysisStorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "antminer_daily_analysis_store"
        private const val PREFIX_KEY = "miner_analyses_"
    }

    /**
     * Loads all stored daily analyses for a specific miner.
     */
    fun getAllAnalyses(minerId: String): List<DailyMinerAnalysis> {
        val key = "$PREFIX_KEY$minerId"
        val rawJson = prefs.getString(key, null) ?: return emptyList()
        val list = mutableListOf<DailyMinerAnalysis>()

        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(deserializeAnalysis(obj, minerId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list.sortedByDescending { it.date }
    }

    /**
     * Retrieves the stored analysis for a specific date and miner.
     */
    fun getAnalysis(minerId: String, date: String): DailyMinerAnalysis? {
        return getAllAnalyses(minerId).firstOrNull { it.date == date }
    }

    /**
     * Saves or replaces analyses for a miner.
     */
    fun saveAnalyses(minerId: String, newAnalyses: List<DailyMinerAnalysis>) {
        if (newAnalyses.isEmpty()) return

        val existing = getAllAnalyses(minerId).toMutableList()
        val newMap = newAnalyses.associateBy { it.date }

        // Replace existing dates with new ones, or append
        val updated = existing.filterNot { newMap.containsKey(it.date) }.toMutableList()
        updated.addAll(newAnalyses)
        updated.sortByDescending { it.date }

        try {
            val jsonArray = JSONArray()
            updated.forEach { analysis ->
                jsonArray.put(serializeAnalysis(analysis))
            }
            val key = "$PREFIX_KEY$minerId"
            prefs.edit().putString(key, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes a specific date analysis for a miner.
     */
    fun deleteAnalysis(minerId: String, date: String) {
        val existing = getAllAnalyses(minerId).filterNot { it.date == date }
        try {
            val jsonArray = JSONArray()
            existing.forEach { analysis ->
                jsonArray.put(serializeAnalysis(analysis))
            }
            val key = "$PREFIX_KEY$minerId"
            prefs.edit().putString(key, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serializeAnalysis(analysis: DailyMinerAnalysis): JSONObject {
        val obj = JSONObject()
        obj.put("minerId", analysis.minerId)
        obj.put("date", analysis.date)
        obj.put("rawDateId", analysis.rawDateId)
        obj.put("analyzedAt", analysis.analyzedAt)
        obj.put("maxTemperature", analysis.maxTemperature)
        obj.put("maxTemperatureTime", analysis.maxTemperatureTime)
        obj.put("maxTemperatureChain", analysis.maxTemperatureChain)

        // Chain Max Temps
        val chainMaxObj = JSONObject()
        analysis.chainMaxTemperatures.forEach { (chain, temp) ->
            chainMaxObj.put(chain.toString(), temp)
        }
        obj.put("chainMaxTemperatures", chainMaxObj)

        // Chain Max Times
        val chainTimesObj = JSONObject()
        analysis.chainMaxTemperatureTimes.forEach { (chain, time) ->
            chainTimesObj.put(chain.toString(), time)
        }
        obj.put("chainMaxTemperatureTimes", chainTimesObj)

        obj.put("maxPcbTemperature", analysis.maxPcbTemperature)
        obj.put("maxPcbTemperatureTime", analysis.maxPcbTemperatureTime)
        obj.put("maxPcbTemperatureChain", analysis.maxPcbTemperatureChain)
        obj.put("temperatureRecordCount", analysis.temperatureRecordCount)
        obj.put("sessionCount", analysis.sessionCount)
        obj.put("restartCount", analysis.restartCount)
        obj.put("hardwareErrorCount", analysis.hardwareErrorCount)
        obj.put("avgHashrateTh", analysis.avgHashrateTh)
        obj.put("minerType", analysis.minerType)
        obj.put("minerSn", analysis.minerSn)

        // Events
        val eventsArray = JSONArray()
        analysis.events.forEach { ev ->
            val evObj = JSONObject()
            evObj.put("timestamp", ev.timestamp)
            evObj.put("type", ev.type)
            evObj.put("description", ev.description)
            eventsArray.put(evObj)
        }
        obj.put("events", eventsArray)

        return obj
    }

    private fun deserializeAnalysis(obj: JSONObject, fallbackMinerId: String): DailyMinerAnalysis {
        val chainMaxMap = mutableMapOf<Int, Int>()
        val chainMaxObj = obj.optJSONObject("chainMaxTemperatures")
        chainMaxObj?.keys()?.forEach { key ->
            key.toIntOrNull()?.let { chainId ->
                chainMaxMap[chainId] = chainMaxObj.getInt(key)
            }
        }

        val chainTimesMap = mutableMapOf<Int, String>()
        val chainTimesObj = obj.optJSONObject("chainMaxTemperatureTimes")
        chainTimesObj?.keys()?.forEach { key ->
            key.toIntOrNull()?.let { chainId ->
                chainTimesMap[chainId] = chainTimesObj.getString(key)
            }
        }

        val eventsList = mutableListOf<DailyMinerEvent>()
        val eventsArray = obj.optJSONArray("events")
        if (eventsArray != null) {
            for (j in 0 until eventsArray.length()) {
                val evObj = eventsArray.getJSONObject(j)
                eventsList.add(
                    DailyMinerEvent(
                        timestamp = evObj.optString("timestamp", ""),
                        type = evObj.optString("type", ""),
                        description = evObj.optString("description", "")
                    )
                )
            }
        }

        return DailyMinerAnalysis(
            minerId = obj.optString("minerId", fallbackMinerId),
            date = obj.optString("date", ""),
            rawDateId = obj.optString("rawDateId", ""),
            analyzedAt = obj.optLong("analyzedAt", System.currentTimeMillis()),
            maxTemperature = obj.optInt("maxTemperature", 0),
            maxTemperatureTime = obj.optString("maxTemperatureTime", ""),
            maxTemperatureChain = obj.optInt("maxTemperatureChain", 0),
            chainMaxTemperatures = chainMaxMap,
            chainMaxTemperatureTimes = chainTimesMap,
            maxPcbTemperature = obj.optInt("maxPcbTemperature", 0),
            maxPcbTemperatureTime = obj.optString("maxPcbTemperatureTime", ""),
            maxPcbTemperatureChain = obj.optInt("maxPcbTemperatureChain", 0),
            temperatureRecordCount = obj.optLong("temperatureRecordCount", 0L),
            sessionCount = obj.optInt("sessionCount", 0),
            restartCount = obj.optInt("restartCount", 0),
            hardwareErrorCount = obj.optInt("hardwareErrorCount", 0),
            avgHashrateTh = obj.optDouble("avgHashrateTh", 0.0),
            minerType = obj.optString("minerType", ""),
            minerSn = obj.optString("minerSn", ""),
            events = eventsList
        )
    }
}
