/*
 * Blog Python Agent Client
 *
 * HTTP client for the Blog Python Agent (remote actions).
 * Follows the same pattern as Phase1aPythonAgentClient in course-builder.
 */
package com.blogagent.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.regex.Pattern

/**
 * HTTP client for the Blog Python Agent (remote actions).
 *
 * When `blog-agent.python-agent.url` is set, blog operations delegate here
 * instead of calling the Blogger API directly.
 *
 * Expects Python agent at the configured URL, e.g. `http://localhost:8000`.
 * API: POST /api/v1/actions/execute with `{"action_name": "...", "parameters": {...}}`.
 * Parameters use snake_case; this client converts from Kotlin camelCase.
 */
@Component
class BlogPythonAgentClient(
    @Value("\${blog-agent.python-agent.url:}") baseUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(BlogPythonAgentClient::class.java)
    private val camelPattern = Pattern.compile("([a-z])([A-Z]+)")

    val baseUrl: String = baseUrl.trim().trimEnd('/')
    val enabled: Boolean = this.baseUrl.isNotBlank()

    private val webClient: WebClient? = if (enabled) {
        WebClient.builder()
            .baseUrl(this.baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build()
    } else null

    init {
        if (enabled) {
            logger.info("Blog Python Agent client configured: {}", this.baseUrl)
        } else {
            logger.debug("Blog Python Agent URL not set; blog operations will call API directly.")
        }
    }

    /**
     * Execute a blog action via the Python agent.
     *
     * @param actionName e.g. blog_create_post, blog_publish_post
     * @param params Kotlin-style params (camelCase). Converted to snake_case for Python.
     * @return result map (snake_case keys) or null on error / non-2xx
     */
    @Suppress("UNCHECKED_CAST")
    fun execute(actionName: String, params: Map<String, Any?>): Map<String, Any>? {
        if (!enabled || webClient == null) {
            return null
        }

        val snakeParams = toSnakeCaseMap(params)
        val body = mapOf(
            "action_name" to actionName,
            "parameters" to snakeParams
        )

        return try {
            val json = webClient.post()
                .uri("/api/v1/actions/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            if (json.isNullOrBlank()) {
                return null
            }

            val root = objectMapper.readTree(json)
            val status = root.path("status")
            if (status.asText() != "success") {
                logger.warn("Python agent action {} returned status {}", actionName, status.asText())
                return null
            }

            val result = root.path("result")
            if (result.isObject) {
                objectMapper.convertValue(result, Map::class.java) as Map<String, Any>
            } else {
                emptyMap()
            }
        } catch (e: WebClientResponseException) {
            logger.warn("Python agent action {} failed: {} {}", actionName, e.statusCode, e.responseBodyAsString)
            null
        } catch (e: Exception) {
            logger.warn("Python agent action {} error: {}", actionName, e.message)
            null
        }
    }

    /**
     * List available actions from the Python agent.
     */
    fun listActions(): List<Map<String, Any>>? {
        if (!enabled || webClient == null) {
            return null
        }

        return try {
            val json = webClient.get()
                .uri("/api/v1/actions")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            if (json.isNullOrBlank()) {
                return null
            }

            val root = objectMapper.readTree(json)
            if (root.isArray) {
                root.map { node ->
                    objectMapper.convertValue(node, Map::class.java) as Map<String, Any>
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to list actions: {}", e.message)
            null
        }
    }

    /**
     * Health check for the Python agent.
     */
    fun healthCheck(): Boolean {
        if (!enabled || webClient == null) {
            return false
        }

        return try {
            val json = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            json?.contains("healthy") == true
        } catch (e: Exception) {
            logger.warn("Health check failed: {}", e.message)
            false
        }
    }

    /**
     * Convert camelCase keys to snake_case.
     */
    private fun toSnakeCaseMap(input: Map<String, Any?>): Map<String, Any?> {
        return input.mapKeys { (key, _) -> toSnakeCase(key) }
    }

    private fun toSnakeCase(camel: String): String {
        val matcher = camelPattern.matcher(camel)
        return matcher.replaceAll("$1_$2").lowercase()
    }
}
