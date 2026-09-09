package com.upivoicealert.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentConfigTest {

    @Test
    fun `environment selection is centralized via config`() {
        val debug = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.DEBUG)
        val release = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.RELEASE)
        assertTrue(debug.isDebug)
        assertFalse(debug.isRelease)
        assertFalse(release.isDebug)
        assertTrue(release.isRelease)
    }

    @Test
    fun `debug enables http body logging`() {
        val cfg = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.DEBUG)
        assertTrue(cfg.isHttpBodyLoggingEnabled)
    }

    @Test
    fun `release disables http body logging`() {
        val cfg = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.RELEASE)
        assertFalse(cfg.isHttpBodyLoggingEnabled)
    }

    @Test
    fun `development configuration resolves correctly`() {
        val cfg = EnvironmentConfig("http://192.168.1.10:3000/api/", AppEnvironment.DEBUG)
        assertEquals("http://192.168.1.10:3000/api/", cfg.baseUrl)
        assertEquals(AppEnvironment.DEBUG, cfg.environment)
    }

    @Test
    fun `release configuration resolves correctly`() {
        val cfg = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.RELEASE)
        assertEquals("https://api.shoutpay.in/api/", cfg.baseUrl)
        assertEquals(AppEnvironment.RELEASE, cfg.environment)
    }
}
