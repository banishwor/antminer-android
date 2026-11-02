package com.example.antminerconnectiontest.ui

import com.example.antminerconnectiontest.data.AntminerClient
import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.ErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionTestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_hasAntminerDefaultsAndSixEndpoints() {
        val viewModel = ConnectionTestViewModel()
        val state = viewModel.state.value

        assertEquals("192.168.1.2", state.ipAddress)
        assertEquals("root", state.username)
        assertEquals("", state.password)
        assertFalse(state.isPasswordVisible)
        assertFalse(state.isTesting)
        assertEquals(6, state.endpointItems.size)
        assertTrue(state.endpointItems.all { it.status == EndpointStatus.UNTESTED })
        assertEquals("/cgi-bin/get_system_info.cgi", state.endpointItems[0].path)
        assertEquals("/cgi-bin/summary.cgi", state.endpointItems[1].path)
        assertEquals("/cgi-bin/stats.cgi", state.endpointItems[2].path)
        assertEquals("/cgi-bin/pools.cgi", state.endpointItems[3].path)
        assertEquals("/cgi-bin/warning.cgi", state.endpointItems[4].path)
        assertEquals("/cgi-bin/chart.cgi", state.endpointItems[5].path)
    }

    @Test
    fun passwordVisibility_togglesCorrectly() {
        val viewModel = ConnectionTestViewModel()
        assertFalse(viewModel.state.value.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.state.value.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.state.value.isPasswordVisible)
    }

    @Test
    fun formInputs_updateState() {
        val viewModel = ConnectionTestViewModel()

        viewModel.onIpAddressChanged("192.168.1.100")
        assertEquals("192.168.1.100", viewModel.state.value.ipAddress)

        viewModel.onUsernameChanged("admin")
        assertEquals("admin", viewModel.state.value.username)

        viewModel.onPasswordChanged("secret123")
        assertEquals("secret123", viewModel.state.value.password)
    }

    @Test
    fun emptyIpAddress_showsGeneralError() = runTest {
        val viewModel = ConnectionTestViewModel()
        viewModel.onIpAddressChanged("   ")
        viewModel.testAllEndpoints()

        val state = viewModel.state.first()
        assertNotNull(state.generalError)
        assertFalse(state.isTesting)
    }

    @Test
    fun toggleEndpointExpanded_togglesTargetEndpointOnly() {
        val viewModel = ConnectionTestViewModel()
        val targetPath = "/cgi-bin/stats.cgi"

        assertFalse(viewModel.state.value.endpointItems.first { it.path == targetPath }.isExpanded)

        viewModel.toggleEndpointExpanded(targetPath)
        assertTrue(viewModel.state.value.endpointItems.first { it.path == targetPath }.isExpanded)

        viewModel.toggleEndpointExpanded(targetPath)
        assertFalse(viewModel.state.value.endpointItems.first { it.path == targetPath }.isExpanded)
    }
}
