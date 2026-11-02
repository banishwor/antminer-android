package com.example.antminerconnectiontest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.antminerconnectiontest.data.AntminerClient
import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.ErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class EndpointStatus {
    UNTESTED,
    TESTING,
    SUCCESS,
    FAILURE
}

data class EndpointTestItem(
    val path: String,
    val status: EndpointStatus = EndpointStatus.UNTESTED,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val responseBody: String? = null,
    val formattedResponse: String? = null,
    val errorMessage: String? = null,
    val errorDetails: String? = null,
    val isExpanded: Boolean = false
)

data class ConnectionFormState(
    val ipAddress: String = "192.168.1.2",
    val username: String = "root",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isTesting: Boolean = false,
    val currentTestingPath: String? = null,
    val endpointItems: List<EndpointTestItem> = AntminerClient.STANDARD_ENDPOINTS.map { path ->
        EndpointTestItem(path = path)
    },
    val summaryMessage: String? = null,
    val generalError: String? = null
)

class ConnectionTestViewModel(
    private val client: AntminerClient = AntminerClient()
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionFormState())
    val state: StateFlow<ConnectionFormState> = _state.asStateFlow()

    fun onIpAddressChanged(newIp: String) {
        _state.update { it.copy(ipAddress = newIp, generalError = null) }
    }

    fun onUsernameChanged(newUsername: String) {
        _state.update { it.copy(username = newUsername, generalError = null) }
    }

    fun onPasswordChanged(newPassword: String) {
        _state.update { it.copy(password = newPassword, generalError = null) }
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleEndpointExpanded(path: String) {
        _state.update { currentState ->
            val updatedItems = currentState.endpointItems.map { item ->
                if (item.path == path) {
                    item.copy(isExpanded = !item.isExpanded)
                } else {
                    item
                }
            }
            currentState.copy(endpointItems = updatedItems)
        }
    }

    /**
     * Tests all six standard Antminer endpoints sequentially off the main thread.
     * Continues testing remaining endpoints even if one endpoint fails.
     */
    fun testAllEndpoints() {
        val currentState = _state.value
        val ip = currentState.ipAddress.trim()
        val username = currentState.username.trim()
        val password = currentState.password

        if (ip.isBlank()) {
            _state.update {
                it.copy(generalError = "Please enter an IP address.")
            }
            return
        }

        // Reset all endpoint items to UNTESTED and prepare testing state
        _state.update {
            it.copy(
                isTesting = true,
                generalError = null,
                summaryMessage = null,
                endpointItems = AntminerClient.STANDARD_ENDPOINTS.map { path ->
                    EndpointTestItem(path = path, status = EndpointStatus.UNTESTED)
                }
            )
        }

        viewModelScope.launch {
            val endpoints = AntminerClient.STANDARD_ENDPOINTS

            for (path in endpoints) {
                // Mark current endpoint as TESTING
                _state.update { state ->
                    val updated = state.endpointItems.map { item ->
                        if (item.path == path) item.copy(status = EndpointStatus.TESTING) else item
                    }
                    state.copy(
                        currentTestingPath = path,
                        endpointItems = updated
                    )
                }

                // Execute request with existing working Digest Auth client
                val result = client.fetchEndpoint(
                    rawIp = ip,
                    username = username,
                    password = password,
                    endpointPath = path
                )

                // Update endpoint result immediately in UI
                _state.update { state ->
                    val updated = state.endpointItems.map { item ->
                        if (item.path == path) {
                            when (result) {
                                is ConnectionResult.Success -> {
                                    val pretty = formatJsonOrRaw(result.responseBody)
                                    item.copy(
                                        status = EndpointStatus.SUCCESS,
                                        statusCode = result.statusCode,
                                        durationMs = result.executionTimeMs,
                                        responseBody = result.responseBody,
                                        formattedResponse = pretty,
                                        errorMessage = null,
                                        errorDetails = null
                                    )
                                }
                                is ConnectionResult.Failure -> {
                                    item.copy(
                                        status = EndpointStatus.FAILURE,
                                        statusCode = result.statusCode,
                                        durationMs = null,
                                        responseBody = null,
                                        formattedResponse = null,
                                        errorMessage = result.message,
                                        errorDetails = result.details
                                    )
                                }
                            }
                        } else {
                            item
                        }
                    }
                    state.copy(endpointItems = updated)
                }
            }

            // All endpoints finished; calculate summary
            val finalItems = _state.value.endpointItems
            val successCount = finalItems.count { it.status == EndpointStatus.SUCCESS }
            val totalCount = finalItems.size
            val summary = "$successCount/$totalCount endpoints successful"

            _state.update {
                it.copy(
                    isTesting = false,
                    currentTestingPath = null,
                    summaryMessage = summary
                )
            }
        }
    }

    private fun formatJsonOrRaw(rawJson: String): String {
        val trimmed = rawJson.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> rawJson
            }
        } catch (_: Exception) {
            rawJson
        }
    }
}
