package com.example.data.remote.moviebox.crypto

import android.util.Base64
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MovieBoxSigner {

    const val SECRET_KEY_DEFAULT = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
    const val SECRET_KEY_ALT = "Xqn2nnO41/L92o1iuXhSLHTbXvY4Z5ZZ62m8mSLA"

    @Volatile
    var cachedRuntimeToken: String? = null

    fun generateXClientToken(timestampMs: Long): String {
        val ts = timestampMs.toString()
        val reversed = ts.reversed()
        val hash = md5(reversed.toByteArray(Charsets.UTF_8))
        return "$ts,$hash"
    }

    private fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildCanonicalString(
        method: String,
        accept: String,
        contentType: String,
        url: String,
        body: String?,
        timestampMs: Long
    ): String {
        val parsedUrl = URL(url)
        val path = parsedUrl.path
        val query = parsedUrl.query

        val canonicalUrl = if (!query.isNullOrEmpty()) {
            val params = query.split("&").map {
                val parts = it.split("=", limit = 2)
                (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
            }
            val sortedQuery = params.sortedBy { it.first }
                .joinToString("&") { "${it.first}=${it.second}" }
            "$path?$sortedQuery"
        } else {
            path
        }

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val hasBody = bodyBytes != null && bodyBytes.isNotEmpty()
        
        val bodyHash = if (hasBody) {
            md5(bodyBytes!!.take(102400).toByteArray())
        } else {
            ""
        }
        
        val bodyLength = if (hasBody) {
            bodyBytes!!.size.toString()
        } else {
            "0"
        }

        val effectiveContentType = if (method == "GET") "" else contentType

        return "$method\n$accept\n$effectiveContentType\n$bodyLength\n$timestampMs\n$bodyHash\n$canonicalUrl"
    }

    fun generateXTrSignature(
        method: String,
        accept: String,
        contentType: String,
        url: String,
        body: String?,
        ts: Long
    ): String {
        val canonical = buildCanonicalString(method, accept, contentType, url, body, ts)
        val secretBytes = Base64.decode(SECRET_KEY_DEFAULT, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val sig = Base64.encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$ts|2|$sig"
    }

    fun buildSignedHeaders(
        method: String,
        url: String,
        accept: String,
        contentType: String,
        body: String?,
        authToken: String?,
        debugOut: MutableMap<String, String>? = null
    ): Map<String, String> {
        val ts = System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, ts)
        val sig = generateXTrSignature(method, accept, contentType, url, body, ts)
        val token = generateXClientToken(ts)

        debugOut?.put("ts", ts.toString())
        debugOut?.put("canonical", canonical)
        debugOut?.put("sig", sig)
        debugOut?.put("token", token)
        debugOut?.put("method", method)
        debugOut?.put("url", url)
        debugOut?.put("bodyLength", body?.toByteArray(Charsets.UTF_8)?.size?.toString() ?: "0")

        val headers = mutableMapOf(
            "User-Agent" to MovieBoxDeviceInfo.USER_AGENT,
            "Accept" to accept,
            "Content-Type" to contentType,
            "Connection" to "keep-alive",
            "X-Client-Token" to token,
            "x-tr-signature" to sig,
            "X-Client-Info" to MovieBoxDeviceInfo.CLIENT_INFO,
            "X-Client-Status" to "0",
            "app-version" to MovieBoxDeviceInfo.APP_VERSION,
            "device-id" to MovieBoxDeviceInfo.DEVICE_ID
        )
        
        if (authToken != null) {
            headers["Authorization"] = "Bearer $authToken"
        }
        
        return headers
    }
}
