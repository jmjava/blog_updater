/*
 * Blog Draft Domain Model
 */
package com.blogagent.model

import java.time.Instant

/**
 * Represents a blog draft in the content generation pipeline.
 *
 * @param id Unique identifier for the draft
 * @param title Blog post title
 * @param topic The topic or subject matter
 * @param outline The structured outline for the post
 * @param content Generated HTML content
 * @param instructions User instructions for content generation
 * @param images List of image references
 * @param labels Tags/labels for the post
 * @param blogId Target blog ID
 * @param postId Existing post ID (for updates)
 * @param state Current workflow state
 * @param revisionCount Number of revisions made
 * @param feedback Human feedback from review
 * @param researchContext Context gathered from RAG
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 */
data class BlogDraft(
    val id: String,
    val title: String,
    val topic: String,
    val outline: String? = null,
    val content: String? = null,
    val instructions: String? = null,
    val images: List<ImageRef> = emptyList(),
    val labels: List<String> = emptyList(),
    val blogId: String? = null,
    val postId: String? = null,
    val state: WorkflowState = WorkflowState.TOPIC_SELECTED,
    val revisionCount: Int = 0,
    val feedback: String? = null,
    val researchContext: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /**
     * Create a new draft with updated state
     */
    fun withState(newState: WorkflowState): BlogDraft = copy(
        state = newState,
        updatedAt = Instant.now()
    )

    /**
     * Create a new draft with content update
     */
    fun withContent(newContent: String): BlogDraft = copy(
        content = newContent,
        updatedAt = Instant.now()
    )

    /**
     * Create a new draft with outline
     */
    fun withOutline(newOutline: String): BlogDraft = copy(
        outline = newOutline,
        updatedAt = Instant.now()
    )

    /**
     * Create a new draft with research context
     */
    fun withResearchContext(context: String): BlogDraft = copy(
        researchContext = context,
        updatedAt = Instant.now()
    )

    /**
     * Create a new draft with feedback and increment revision count
     */
    fun withFeedback(newFeedback: String): BlogDraft = copy(
        feedback = newFeedback,
        revisionCount = revisionCount + 1,
        updatedAt = Instant.now()
    )

    /**
     * Create a new draft with post ID (after creation)
     */
    fun withPostId(newPostId: String): BlogDraft = copy(
        postId = newPostId,
        updatedAt = Instant.now()
    )

    companion object {
        /**
         * Create a new draft from a topic
         */
        fun fromTopic(
            id: String,
            topic: String,
            title: String? = null,
            blogId: String? = null,
            instructions: String? = null
        ): BlogDraft = BlogDraft(
            id = id,
            title = title ?: topic,
            topic = topic,
            blogId = blogId,
            instructions = instructions,
            state = WorkflowState.TOPIC_SELECTED
        )
    }
}

/**
 * Image reference for a blog post
 */
data class ImageRef(
    val url: String,
    val caption: String? = null,
    val localPath: String? = null
)
