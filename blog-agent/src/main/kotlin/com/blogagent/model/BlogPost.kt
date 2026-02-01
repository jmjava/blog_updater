/*
 * Blog Post Domain Model (Published Post)
 */
package com.blogagent.model

import java.time.Instant

/**
 * Represents a published blog post on Blogger.
 *
 * @param id Post ID from Blogger
 * @param blogId Blog ID
 * @param title Post title
 * @param content HTML content
 * @param url Published URL
 * @param status Post status (draft, live, etc.)
 * @param labels Tags/labels
 * @param published Publication timestamp
 * @param updated Last update timestamp
 */
data class BlogPost(
    val id: String,
    val blogId: String,
    val title: String,
    val content: String,
    val url: String? = null,
    val status: PostStatus = PostStatus.DRAFT,
    val labels: List<String> = emptyList(),
    val published: Instant? = null,
    val updated: Instant? = null
)

/**
 * Post status on Blogger
 */
enum class PostStatus {
    DRAFT,
    LIVE,
    SCHEDULED,
    SOFT_TRASHED
}

/**
 * Request to create a new post
 */
data class CreatePostRequest(
    val blogId: String,
    val title: String,
    val content: String,
    val labels: List<String> = emptyList(),
    val images: List<ImageRef> = emptyList(),
    val draft: Boolean = true
)

/**
 * Request to update an existing post
 */
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val labels: List<String>? = null,
    val images: List<ImageRef>? = null
)

/**
 * Response from post creation/update
 */
data class PostResponse(
    val id: String,
    val url: String? = null,
    val status: String,
    val title: String
)

/**
 * Blog information
 */
data class BlogInfo(
    val id: String,
    val name: String?,
    val url: String?
)
