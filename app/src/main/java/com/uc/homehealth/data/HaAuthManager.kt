package com.uc.homehealth.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

const val HA_CLIENT_ID = "http://homehealth.local/"
const val HA_REDIRECT_URI = "http://homehealth.local/auth-callback"

/**
 * Strip characters that can never appear in an HA token but routinely sneak into a
 * copy-paste: whitespace (including interior line breaks from wrapped text), zero-width
 * spaces/joiners, BOM, soft hyphens, and other control/format characters. HA access and
 * refresh tokens are JWT/base64url strings, so none of these are ever legitimate — and a
 * single invisible character makes HA reject the token and log a failed login attempt.
 */
fun sanitizeHaToken(raw: String): String = raw.filterNot {
    it.isWhitespace() || it.category == CharCategory.FORMAT || it.category == CharCategory.CONTROL
}

/**
 * `/auth/token` answered with an HTTP error. 4xx means HA definitively rejected the
 * grant (revoked/expired refresh token, bad code, wrong client_id) — retrying would just
 * add failed-login entries to HA's log and tick its ip_ban counter. Anything else is
 * transient (proxy hiccup, HA restarting) and safe to retry.
 */
class HaAuthHttpException(val code: Int) : Exception("auth/token returned HTTP $code") {
    val isDefinitiveRejection: Boolean get() = code in 400..499
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

@Singleton
class HaAuthManager @Inject constructor(private val okHttpClient: OkHttpClient) {

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun buildAuthUrl(haUrl: String, codeVerifier: String): String {
        val base = haUrl.trimEnd('/')
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        return "$base/auth/authorize" +
            "?response_type=code" +
            "&client_id=${enc(HA_CLIENT_ID)}" +
            "&redirect_uri=${enc(HA_REDIRECT_URI)}" +
            "&state=${enc(haUrl)}" +
            "&code_challenge=${enc(codeChallenge(codeVerifier))}" +
            "&code_challenge_method=S256"
    }

    suspend fun exchangeCode(haUrl: String, code: String, codeVerifier: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", HA_CLIENT_ID)
                .add("redirect_uri", HA_REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()
            val req = Request.Builder()
                .url("${haUrl.trimEnd('/')}/auth/token")
                .post(body)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw HaAuthHttpException(resp.code)
                val json = JSONObject(resp.body?.string().orEmpty())
                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresIn = json.getInt("expires_in"),
                )
            }
        }

    suspend fun refreshToken(haUrl: String, refreshToken: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", HA_CLIENT_ID)
                .build()
            val req = Request.Builder()
                .url("${haUrl.trimEnd('/')}/auth/token")
                .post(body)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw HaAuthHttpException(resp.code)
                val json = JSONObject(resp.body?.string().orEmpty())
                TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else refreshToken,
                    expiresIn = json.getInt("expires_in"),
                )
            }
        }
}
