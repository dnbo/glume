package com.android.glume.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The handful of Immich endpoints used to sign a TV in. Verified against Immich 3.3 (OpenAPI spec
 * and a live run on demo.immich.app). Everything after sign-in uses the limited API key only.
 */

@Serializable data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val userId: String,
    val userEmail: String,
    val name: String,
    val profileImagePath: String = "",
)

@Serializable data class ApiKeyCreateRequest(val name: String, val permissions: List<String>)
@Serializable data class ApiKeyCreateResponse(val id: String, val secret: String, val name: String = "")

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val profileImagePath: String = "",
    val avatarColor: String = "primary",
)

@Serializable data class PingResponse(val res: String = "")

class ApiException(val status: Int, message: String) : Exception(message)

class ImmichAuthApi(private val http: HttpClient = defaultClient()) {

    suspend fun ping(baseUrl: String): Boolean =
        runCatching { http.get("$baseUrl/api/server/ping").body<PingResponse>().res == "pong" }.getOrDefault(false)

    suspend fun login(baseUrl: String, email: String, password: String): LoginResponse =
        http.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email.trim(), password))
        }.orThrow().body()

    suspend fun createApiKey(baseUrl: String, accessToken: String, name: String, permissions: List<String>): ApiKeyCreateResponse =
        http.post("$baseUrl/api/api-keys") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(ApiKeyCreateRequest(name, permissions))
        }.orThrow().body()

    /** Revokes one of this TV's own earlier keys (never touches photos). Best effort. */
    suspend fun deleteApiKey(baseUrl: String, accessToken: String, id: String) {
        runCatching { http.delete("$baseUrl/api/api-keys/$id") { bearerAuth(accessToken) } }
    }

    suspend fun logout(baseUrl: String, accessToken: String) {
        runCatching { http.post("$baseUrl/api/auth/logout") { bearerAuth(accessToken) } }
    }

    suspend fun me(baseUrl: String, apiKey: String): UserResponse =
        http.get("$baseUrl/api/users/me") { header("x-api-key", apiKey) }.orThrow().body()

    private suspend fun HttpResponse.orThrow(): HttpResponse {
        if (status.isSuccess()) return this
        val msg = runCatching { Json.parseToJsonElement(bodyAsText()).let { (it as? kotlinx.serialization.json.JsonObject)?.get("message")?.toString()?.trim('"') } }.getOrNull()
        throw ApiException(status.value, msg ?: status.description)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun defaultClient() = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 20_000 }
            expectSuccess = false
        }
    }
}
