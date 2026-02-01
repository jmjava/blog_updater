/*
 * Review Actions - Human-in-the-Loop checkpoints
 */
package com.blogagent.action

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.BlogDraft
import com.blogagent.model.WorkflowState
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.OperationContext
import org.slf4j.LoggerFactory

/**
 * Actions for human review and approval (HITL checkpoints).
 */
@EmbabelComponent
class ReviewActions(
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(ReviewActions::class.java)

    /**
     * Request human review of the draft.
     * This is a HITL checkpoint that pauses the workflow.
     */
    @Action(canRerun = false)
    fun requestReview(
        draft: BlogDraft
    ): BlogDraft {
        logger.info("Requesting human review for draft: {}", draft.title)
        return draft.withState(WorkflowState.AWAITING_REVIEW)
    }

    /**
     * Approve the draft for publishing.
     */
    @Action(canRerun = false)
    fun approveDraft(
        draft: BlogDraft
    ): BlogDraft {
        logger.info("Draft approved: {}", draft.title)
        return draft.withState(WorkflowState.DRAFT_APPROVED)
    }

    /**
     * Record feedback for revision.
     */
    @Action(canRerun = true)
    fun provideFeedback(
        draft: BlogDraft,
        feedback: String
    ): BlogDraft {
        logger.info("Feedback received for draft: {} - {}", draft.title, feedback.take(100))

        if (draft.revisionCount >= blogAgentProperties.hitl.maxRevisions) {
            logger.warn("Maximum revisions ({}) reached for draft: {}",
                blogAgentProperties.hitl.maxRevisions, draft.id)
            return draft
        }

        return draft
            .withFeedback(feedback)
            .withState(WorkflowState.FEEDBACK_RECEIVED)
    }
}
