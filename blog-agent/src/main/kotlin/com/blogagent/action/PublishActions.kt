/*
 * Publish Actions - Blogger API integration for posting
 */
package com.blogagent.action

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.*
import com.blogagent.service.BloggerService
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.OperationContext
import org.slf4j.LoggerFactory

/**
 * Actions for publishing blog posts to Blogger.
 */
@EmbabelComponent
class PublishActions(
    private val bloggerService: BloggerService,
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(PublishActions::class.java)

    /**
     * Add images to the draft (upload if needed).
     */
    @Action(canRerun = true)
    fun addImages(
        draft: BlogDraft
    ): BlogDraft {
        logger.info("Processing images for draft: {}", draft.title)

        if (draft.images.isEmpty()) {
            logger.info("No images to process")
            return draft.withState(WorkflowState.IMAGES_ADDED)
        }

        // Upload local images and get URLs
        val processedImages = draft.images.map { image ->
            if (image.localPath != null && image.url.isBlank()) {
                try {
                    val url = bloggerService.uploadImage(image.localPath)
                    image.copy(url = url)
                } catch (e: Exception) {
                    logger.error("Failed to upload image: {}", image.localPath, e)
                    image
                }
            } else {
                image
            }
        }

        logger.info("Processed {} images", processedImages.size)

        return draft.copy(
            images = processedImages,
            state = WorkflowState.IMAGES_ADDED
        )
    }

    /**
     * Create a draft post on Blogger.
     */
    @Action(canRerun = false)
    fun createPost(
        draft: BlogDraft
    ): BlogDraft {
        val blogId = draft.blogId ?: blogAgentProperties.defaultBlogId
            ?: throw IllegalStateException("No blog_id configured. Set blog-agent.default-blog-id or provide in draft.")

        logger.info("Creating post on blog {} for: {}", blogId, draft.title)

        val request = CreatePostRequest(
            blogId = blogId,
            title = draft.title,
            content = draft.content ?: "",
            labels = draft.labels,
            images = draft.images,
            draft = true
        )

        val response = bloggerService.createPost(request)

        logger.info("Post created: id={}, status={}", response.id, response.status)

        return draft
            .withPostId(response.id)
            .withState(WorkflowState.POST_CREATED)
    }

    /**
     * Publish the draft post on Blogger.
     */
    @Action(canRerun = false)
    fun publishPost(
        draft: BlogDraft
    ): BlogDraft {
        val blogId = draft.blogId ?: blogAgentProperties.defaultBlogId
            ?: throw IllegalStateException("No blog_id configured")
        val postId = draft.postId
            ?: throw IllegalStateException("No post_id available. Create the post first.")

        logger.info("Publishing post {} on blog {}", postId, blogId)

        val response = bloggerService.publishPost(blogId, postId)

        logger.info("Post published: url={}", response.url)

        return draft.withState(WorkflowState.PUBLISHED)
    }

    /**
     * Update an existing post on Blogger.
     */
    @Action(canRerun = true)
    fun updatePost(
        draft: BlogDraft
    ): BlogDraft {
        val blogId = draft.blogId ?: blogAgentProperties.defaultBlogId
            ?: throw IllegalStateException("No blog_id configured")
        val postId = draft.postId
            ?: throw IllegalStateException("No post_id available. Create the post first.")

        logger.info("Updating post {} on blog {}", postId, blogId)

        val request = UpdatePostRequest(
            title = draft.title,
            content = draft.content,
            labels = draft.labels,
            images = draft.images
        )

        val response = bloggerService.updatePost(blogId, postId, request)

        logger.info("Post updated: id={}, status={}", response.id, response.status)

        return draft
    }
}
