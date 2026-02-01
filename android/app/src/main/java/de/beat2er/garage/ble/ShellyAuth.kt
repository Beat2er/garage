package de.beat2er.garage.ble

import java.security.MessageDigest

object ShellyAuth {

    fun calculateResponse(
        password: String,
        realm: String,
        nonce: Long,
        cnonce: Long = System.currentTimeMillis() / 1000,
        nc: Int = 1
    ): Map<String, Any> {
        val ha1 = sha256("admin:$realm:$password")
        val ha2 = sha256("dummy_method:dummy_uri")
        val response = sha256("$ha1:$nonce:$nc:$cnonce:auth:$ha2")

        return mapOf(
            "realm" to realm,
            "username" to "admin",
            "nonce" to nonce,
            "cnonce" to cnonce,
            "response" to response,
            "nc" to nc,
            "algorithm" to "SHA-256"
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
