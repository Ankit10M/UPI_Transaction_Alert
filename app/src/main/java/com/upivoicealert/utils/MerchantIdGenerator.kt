package com.upivoicealert.utils

import java.security.SecureRandom

/**
 * Generates a permanent local merchant identity in the format `SP-XXXXXX`
 * (e.g. `SP-A82F91`). The ID is created once per merchant and never changes.
 *
 * The suffix is 6 uppercase hexadecimal characters (3 random bytes), giving
 * ~16.7M distinct IDs — ample for a local, non-collision-checked identifier.
 */
object MerchantIdGenerator {

    const val PREFIX = "SP-"

    /** Format contract used by tests and the profile UI: SP- + 6 hex digits. */
    val FORMAT = Regex("^SP-[0-9A-F]{6}$")

    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(3)
        secureRandom.nextBytes(bytes)
        return PREFIX + bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}