/*
 * Blog Workflow Agent - Main GOAP agent for blog content generation
 */
package com.blogagent.agent

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.BlogDraft
import com.blogagent.model.WorkflowState
import com.embabel.agent.api.annotation.EmbabelComponent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Blog Workflow Agent manages the GOAP-based content generation pipeline.
 *
 * This agent orchestrates the workflow from topic selection through publishing,
 * with Human-in-the-Loop (HITL) checkpoints for review and approval.
 */
@Service
@EmbabelComponent
class BlogWorkflowAgent(
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(BlogWorkflowAgent::class.java)

    // In-memory draft storage (could be replaced with persistent storage)
    private val drafts = ConcurrentHashMap<String, BlogDraft>()

    // Session to draft mapping
    private val sessionDrafts = ConcurrentHashMap<String, String>()

    /**
     * Start a new blog workflow from a topic.
     */
    fun startWorkflow(
        topic: String,
        title: String? = null,
        blogId: String? = null,
        instructions: String? = null,
        sessionId: String? = null
    ): BlogDraft {
        val draftId = UUID.randomUUID().toString()
        val draft = BlogDraft.fromTopic(
            id = draftId,
            topic = topic,
            title = title,
            blogId = blogId ?: blogAgentProperties.defaultBlogId,
            instructions = instructions
        )

        drafts[draftId] = draft
        sessionId?.let { sessionDrafts[it] = draftId }

        logger.info("Started workflow for topic '{}' with draft ID: {}", topic, draftId)
        return draft
    }

    /**
     * Get the current draft for a session.
     */
    fun getDraftForSession(sessionId: String): BlogDraft? {
        val draftId = sessionDrafts[sessionId] ?: return null
        return drafts[draftId]
    }

    /**
     * Get a draft by ID.
     */
    fun getDraft(draftId: String): BlogDraft? = drafts[draftId]

    /**
     * Update a draft in storage.
     */
    fun updateDraft(draft: BlogDraft): BlogDraft {
        drafts[draft.id] = draft
        logger.debug("Updated draft: {} -> state: {}", draft.id, draft.state)
        return draft
    }

    /**
     * Get all drafts.
     */
    fun getAllDrafts(): List<BlogDraft> = drafts.values.toList()

    /**
     * Get drafts by state.
     */
    fun getDraftsByState(state: WorkflowState): List<BlogDraft> =
        drafts.values.filter { it.state == state }

    /**
     * Get drafts awaiting review (HITL checkpoint).
     */
    fun getDraftsAwaitingReview(): List<BlogDraft> =
        getDraftsByState(WorkflowState.AWAITING_REVIEW)

    /**
     * Delete a draft.
     */
    fun deleteDraft(draftId: String): Boolean {
        val removed = drafts.remove(draftId)
        // Also remove session mapping
        sessionDrafts.entries.removeIf { it.value == draftId }
        logger.info("Deleted draft: {} (found: {})", draftId, removed != null)
        return removed != null
    }

    /**
     * Associate a session with a draft.
     */
    fun associateSession(sessionId: String, draftId: String) {
        sessionDrafts[sessionId] = draftId
    }

    /**
     * Get the next action recommendation based on current state.
     */
    fun getNextActionRecommendation(draft: BlogDraft): String {
        return when (draft.state) {
            WorkflowState.TOPIC_SELECTED -> "gather_context"
            WorkflowState.RESEARCH_COMPLETE -> "create_outline"
            WorkflowState.OUTLINE_CREATED -> "generate_draft"
            WorkflowState.DRAFT_GENERATED -> "request_review"
            WorkflowState.AWAITING_REVIEW -> "(waiting for human: approve or provide feedback)"
            WorkflowState.FEEDBACK_RECEIVED -> "revise_draft"
            WorkflowState.DRAFT_APPROVED -> "add_images"
            WorkflowState.IMAGES_ADDED -> "create_post"
            WorkflowState.POST_CREATED -> "publish_post"
            WorkflowState.PUBLISHED -> "(workflow complete)"
        }
    }

    /**
     * Get workflow status summary for a draft.
     */
    fun getWorkflowStatus(draft: BlogDraft): String {
        return buildString {
            append("## Workflow Status\n\n")
            append("- **Draft ID:** ${draft.id}\n")
            append("- **Title:** ${draft.title}\n")
            append("- **Topic:** ${draft.topic}\n")
            append("- **Current State:** ${draft.state}\n")
            append("- **Revisions:** ${draft.revisionCount}\n")
            append("- **Next Action:** ${getNextActionRecommendation(draft)}\n")

            if (draft.state.isHitlCheckpoint()) {
                append("\n**⏸️ Waiting for human input**\n")
                append("- Say 'approve' to approve the draft\n")
                append("- Provide feedback to request changes\n")
            }

            if (draft.state.isComplete()) {
                append("\n**✅ Workflow Complete**\n")
                draft.postId?.let { append("- Post ID: $it\n") }
            }
        }
    }
}
