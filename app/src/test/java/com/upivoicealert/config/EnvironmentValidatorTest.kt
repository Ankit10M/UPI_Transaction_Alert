package com.upivoicealert.config

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentValidatorTest {

    @Test
    fun `debug allows localhost`() {
        val cfg = EnvironmentConfig("http://10.0.2.2:3000/api/", AppEnvironment.DEBUG)
        assertNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `debug allows LAN IP`() {
        val cfg = EnvironmentConfig("http://192.168.1.10:3000/api/", AppEnvironment.DEBUG)
        assertNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `debug allows http`() {
        val cfg = EnvironmentConfig("http://192.168.1.10:3000/api/", AppEnvironment.DEBUG)
        assertNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release valid https production url passes`() {
        val cfg = EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.RELEASE)
        assertNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release using 10_0_2_2 is rejected`() {
        val cfg = EnvironmentConfig("http://10.0.2.2:3000/api/", AppEnvironment.RELEASE)
        val error = EnvironmentValidator.validate(cfg)
        assertNotNull(error)
        assertTrue(error!!.contains("10.0.2.2"))
    }

    @Test
    fun `release using localhost is rejected`() {
        val cfg = EnvironmentConfig("http://localhost:3000/api/", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release using 127_0_0_1 is rejected`() {
        val cfg = EnvironmentConfig("http://127.0.0.1:3000/api/", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release using LAN IP 192_168 is rejected`() {
        val cfg = EnvironmentConfig("https://192.168.1.10:3000/api/", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release using 10_x_x_x is rejected`() {
        val cfg = EnvironmentConfig("https://10.10.0.5/api/", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release missing trailing slash is rejected`() {
        val cfg = EnvironmentConfig("https://api.shoutpay.in/api", AppEnvironment.RELEASE)
        val error = EnvironmentValidator.validate(cfg)
        assertNotNull(error)
        assertTrue(error!!.contains("end with '/'"))
    }

    @Test
    fun `empty base url is rejected`() {
        val cfg = EnvironmentConfig("", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `malformed url is rejected`() {
        val cfg = EnvironmentConfig("not-a-url/", AppEnvironment.RELEASE)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }

    @Test
    fun `release must use https`() {
        val cfg = EnvironmentConfig("http://api.shoutpay.in/api/", AppEnvironment.RELEASE)
        val error = EnvironmentValidator.validate(cfg)
        assertNotNull(error)
        assertTrue(error!!.contains("https"))
    }

    @Test
    fun `missing base url is rejected for debug too`() {
        val cfg = EnvironmentConfig("", AppEnvironment.DEBUG)
        assertNotNull(EnvironmentValidator.validate(cfg))
    }
}
