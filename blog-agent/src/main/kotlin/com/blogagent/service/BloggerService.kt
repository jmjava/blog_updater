/*
 * Blogger Service - Uses Python Agent or direct HTTP client
 *
 * When blog-agent.python-agent.url is configured, delegates to the Python agent.
 * Otherwise, calls the existing blog_updater FastAPI directly.
 */
package com.blogagent.service

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.io.File

/**
 * Service for Blogger operations.
 *
 * Uses the Python agent when configured (blog-agent.python-agent.url),
 * otherwise falls back to direct HTTP calls to the existing api.py.
 */
@Service
class BloggerService(
    private val blogAgentProperties: BlogAgentProperties,
    private val pythonAgentClient: BlogPythonAgentClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(BloggerService::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(blogAgentProperties.bloggerApiUrl)
        .defaultHeader("Content-Type", "application/json")
        .build()

    /**
     * List all blogs for the authenticated user.
     */
    fun listBlogs(): List<BlogInfo> {
        logger.info("Fetching blogs")

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val result = pythonAgentClient.execute("blog_list_blogs", emptyMap())
            if (result != null) {
                @Suppress("UNCHECKED_CAST")
                val blogs = result["blogs"] as? List<Map<String, Any?>> ?: emptyList()
                return blogs.map { blog ->
                    BlogInfo(
                        id = blog["id"] as String,
                        name = blog["name"] as? String,
                        url = blog["url"] as? String
                    )
                }
            }
        }

        // Fall back to direct API call
        return try {
            val response = webClient.get()
                .uri("/blogs")
                .retrieve()
                .bodyToMono(BlogsResponse::class.java)
                .block()

            response?.blogs ?: emptyList()
        } catch (e: WebClientResponseException) {
            logger.error("Failed to list blogs: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to list blogs: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to list blogs: {}", e.message)
            throw BloggerApiException("Failed to list blogs: ${e.message}", e)
        }
    }

    /**
     * Create a new post on Blogger.
     */
    fun createPost(request: CreatePostRequest): PostResponse {
        logger.info("Creating post '{}' on blog {}", request.title, request.blogId)

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val params = mapOf(
                "blogId" to request.blogId,
                "title" to request.title,
                "content" to request.content,
                "labels" to request.labels,
                "images" to request.images.map { mapOf("url" to it.url, "caption" to it.caption) },
                "draft" to request.draft
            )
            val result = pythonAgentClient.execute("blog_create_post", params)
            if (result != null) {
                return PostResponse(
                    id = result["id"] as String,
                    url = result["url"] as? String,
                    status = result["status"] as String,
                    title = result["title"] as String
                )
            }
        }

        // Fall back to direct API call
        val body = mapOf(
            "blog_id" to request.blogId,
            "title" to request.title,
            "content" to request.content,
            "labels" to request.labels,
            "images" to request.images.map { mapOf("url" to it.url, "caption" to it.caption) },
            "draft" to request.draft
        )

        return try {
            val response = webClient.post()
                .uri("/posts")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PostResponse::class.java)
                .block()

            response ?: throw BloggerApiException("Empty response from create post")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to create post: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to create post: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to create post: {}", e.message)
            throw BloggerApiException("Failed to create post: ${e.message}", e)
        }
    }

    /**
     * Update an existing post on Blogger.
     */
    fun updatePost(blogId: String, postId: String, request: UpdatePostRequest): PostResponse {
        logger.info("Updating post {} on blog {}", postId, blogId)

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val params = mutableMapOf<String, Any?>(
                "blogId" to blogId,
                "postId" to postId
            )
            request.title?.let { params["title"] = it }
            request.content?.let { params["content"] = it }
            request.labels?.let { params["labels"] = it }
            request.images?.let {
                params["images"] = it.map { img -> mapOf("url" to img.url, "caption" to img.caption) }
            }

            val result = pythonAgentClient.execute("blog_update_post", params)
            if (result != null) {
                return PostResponse(
                    id = result["id"] as String,
                    url = result["url"] as? String,
                    status = result["status"] as String,
                    title = result["title"] as String
                )
            }
        }

        // Fall back to direct API call
        val body = mutableMapOf<String, Any?>()
        request.title?.let { body["title"] = it }
        request.content?.let { body["content"] = it }
        request.labels?.let { body["labels"] = it }
        request.images?.let {
            body["images"] = it.map { img -> mapOf("url" to img.url, "caption" to img.caption) }
        }

        return try {
            val response = webClient.put()
                .uri("/posts/{blogId}/{postId}", blogId, postId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PostResponse::class.java)
                .block()

            response ?: throw BloggerApiException("Empty response from update post")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to update post: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to update post: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to update post: {}", e.message)
            throw BloggerApiException("Failed to update post: ${e.message}", e)
        }
    }

    /**
     * Publish a draft post.
     */
    fun publishPost(blogId: String, postId: String): PublishResponse {
        logger.info("Publishing post {} on blog {}", postId, blogId)

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val params = mapOf(
                "blogId" to blogId,
                "postId" to postId
            )
            val result = pythonAgentClient.execute("blog_publish_post", params)
            if (result != null) {
                return PublishResponse(
                    id = result["id"] as String,
                    url = result["url"] as? String,
                    status = result["status"] as String,
                    published = result["published"] as? String
                )
            }
        }

        // Fall back to direct API call
        return try {
            val response = webClient.post()
                .uri("/posts/{blogId}/{postId}/publish", blogId, postId)
                .retrieve()
                .bodyToMono(PublishResponse::class.java)
                .block()

            response ?: throw BloggerApiException("Empty response from publish post")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to publish post: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to publish post: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to publish post: {}", e.message)
            throw BloggerApiException("Failed to publish post: ${e.message}", e)
        }
    }

    /**
     * Get a post by ID.
     */
    fun getPost(blogId: String, postId: String): BlogPost {
        logger.info("Getting post {} from blog {}", postId, blogId)

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val params = mapOf(
                "blogId" to blogId,
                "postId" to postId
            )
            val result = pythonAgentClient.execute("blog_get_post", params)
            if (result != null) {
                return BlogPost(
                    id = result["id"] as String,
                    blogId = blogId,
                    title = result["title"] as? String ?: "",
                    content = result["content"] as? String ?: "",
                    url = result["url"] as? String,
                    status = PostStatus.valueOf((result["status"] as? String ?: "DRAFT").uppercase()),
                    labels = @Suppress("UNCHECKED_CAST") (result["labels"] as? List<String> ?: emptyList())
                )
            }
        }

        // Fall back to direct API call
        return try {
            val response = webClient.get()
                .uri("/posts/{blogId}/{postId}", blogId, postId)
                .retrieve()
                .bodyToMono(GetPostResponse::class.java)
                .block()

            response?.toBlogPost() ?: throw BloggerApiException("Empty response from get post")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to get post: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to get post: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to get post: {}", e.message)
            throw BloggerApiException("Failed to get post: ${e.message}", e)
        }
    }

    /**
     * Upload an image to Google Drive and return the public URL.
     */
    fun uploadImage(localPath: String): String {
        logger.info("Uploading image: {}", localPath)

        // Try Python agent first
        if (pythonAgentClient.enabled) {
            val result = pythonAgentClient.execute("blog_upload_image", mapOf("filePath" to localPath))
            if (result != null) {
                return result["url"] as String
            }
        }

        // Fall back to direct API call
        val file = File(localPath)
        if (!file.exists()) {
            throw BloggerApiException("Image file not found: $localPath")
        }

        return try {
            val bodyBuilder = MultipartBodyBuilder()
            bodyBuilder.part("file", FileSystemResource(file))
                .contentType(MediaType.parseMediaType(getContentType(file)))

            val response = webClient.post()
                .uri("/upload-image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(UploadImageResponse::class.java)
                .block()

            response?.url ?: throw BloggerApiException("Empty URL from upload image")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to upload image: {} {}", e.statusCode, e.responseBodyAsString)
            throw BloggerApiException("Failed to upload image: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to upload image: {}", e.message)
            throw BloggerApiException("Failed to upload image: ${e.message}", e)
        }
    }

    /**
     * Health check - checks both Python agent and direct API.
     */
    fun healthCheck(): Boolean {
        // Check Python agent if enabled
        if (pythonAgentClient.enabled) {
            if (pythonAgentClient.healthCheck()) {
                return true
            }
        }

        // Check direct API
        return try {
            val response = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            response?.get("status") == "ok"
        } catch (e: Exception) {
            logger.warn("Health check failed: {}", e.message)
            false
        }
    }

    /**
     * Check if Python agent is enabled and healthy.
     */
    fun isPythonAgentEnabled(): Boolean = pythonAgentClient.enabled

    /**
     * Check if Python agent is healthy.
     */
    fun isPythonAgentHealthy(): Boolean = pythonAgentClient.enabled && pythonAgentClient.healthCheck()

    private fun getContentType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

// Response DTOs

data class BlogsResponse(
    val blogs: List<BlogInfo>
)

data class PublishResponse(
    val id: String,
    val url: String?,
    val status: String,
    val published: String?
)

data class GetPostResponse(
    val id: String,
    val title: String?,
    val content: String?,
    val labels: List<String>?,
    val status: String?,
    val url: String?,
    val published: String?,
    val updated: String?
) {
    fun toBlogPost(): BlogPost = BlogPost(
        id = id,
        blogId = "", // Not returned by API
        title = title ?: "",
        content = content ?: "",
        url = url,
        status = PostStatus.valueOf(status?.uppercase() ?: "DRAFT"),
        labels = labels ?: emptyList()
    )
}

data class UploadImageResponse(
    val url: String
)

class BloggerApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
