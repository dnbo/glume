package com.android.glume.data.auth

import com.android.glume.data.api.ApiException
import com.android.glume.data.api.ImmichAuthApi

/**
 * Permissions for the TV's API key: reads, plus the two writes CLAUDE.md allows (favorites via
 * asset.update, adding to existing albums via albumAsset.create). Deletes are refused by the server.
 * Note: Immich has no favorite-only permission, so asset.update also covers metadata edits; the
 * app's code must never call those.
 */
val TV_PERMISSIONS = listOf(
    "user.read", "userProfileImage.read",
    "asset.read", "asset.view", "asset.download", "asset.update",
    "album.read", "albumAsset.create",
    "timeline.read", "person.read", "memory.read", "map.read",
    "server.about", "server.storage", "partner.read", "tag.read", "stack.read",
)

sealed interface SignInResult {
    data class Success(val account: Account) : SignInResult
    data class Failed(val reason: SignInError, val detail: String = "") : SignInResult
}

enum class SignInError(val message: String) {
    Unreachable("Couldn't reach the server. Check the address and that the TV is on the same network."),
    InvalidCredentials("Email or password is incorrect."),
    KeyNotAllowed("Signed in, but this account isn't allowed to create the TV's access key."),
    Unexpected("Something went wrong while signing in."),
}

object ServerUrl {
    /** "photos.local:2283/" or "https://x/api" → "https://photos.local:2283" style base URL. */
    fun normalize(input: String): String {
        var u = input.trim().removeSuffix("/")
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.removeSuffix("/api").removeSuffix("/")
        return u
    }

    fun host(url: String) = url.removePrefix("https://").removePrefix("http://")
}

/**
 * Signs a TV in with an email and password, then swaps the full-access session for a limited
 * API key: login → create key → logout. The password and session token are never stored.
 */
class SignInService(
    private val api: ImmichAuthApi,
    private val saveAccount: suspend (Account, apiKey: String) -> Unit,
    private val deviceName: String,
    /** The key id this TV already holds for an account id, so a re-sign-in can revoke it. */
    private val previousKeyId: suspend (accountId: String) -> String? = { null },
) {

    suspend fun signIn(serverInput: String, email: String, password: String): SignInResult {
        val candidates = ServerUrl.normalize(serverInput).let { u ->
            // Users often type a bare host; fall back to http:// for LAN servers without TLS.
            if (serverInput.trim().startsWith("http")) listOf(u) else listOf(u, u.replaceFirst("https://", "http://"))
        }
        val base = candidates.firstOrNull { api.ping(it) } ?: return SignInResult.Failed(SignInError.Unreachable)
        val login = try {
            api.login(base, email, password)
        } catch (e: ApiException) {
            return SignInResult.Failed(if (e.status == 401 || e.status == 400) SignInError.InvalidCredentials else SignInError.Unexpected, e.message.orEmpty())
        } catch (e: Exception) {
            return SignInResult.Failed(SignInError.Unreachable, e.message.orEmpty())
        }
        return try {
            val key = api.createApiKey(base, login.accessToken, "Glume TV ($deviceName)", TV_PERMISSIONS)
            val me = api.me(base, key.secret)
            val account = Account(
                id = "${ServerUrl.host(base)}/${me.id}", serverUrl = base, userId = me.id,
                name = me.name, email = me.email, avatarColor = me.avatarColor,
                hasProfileImage = me.profileImagePath.isNotEmpty(), apiKeyId = key.id,
            )
            val old = previousKeyId(account.id)
            saveAccount(account, key.secret)
            if (!old.isNullOrEmpty() && old != key.id) api.deleteApiKey(base, login.accessToken, old)
            SignInResult.Success(account)
        } catch (e: ApiException) {
            SignInResult.Failed(if (e.status == 403) SignInError.KeyNotAllowed else SignInError.Unexpected, e.message.orEmpty())
        } catch (e: Exception) {
            SignInResult.Failed(SignInError.Unexpected, e.message.orEmpty())
        } finally {
            api.logout(base, login.accessToken)
        }
    }
}
