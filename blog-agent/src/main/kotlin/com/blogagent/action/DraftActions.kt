/*
 * Draft Actions - Content generation and revision
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
 * Data class to hold generated content.
 */
data class GeneratedContent(val text: String)

/**
 * Actions for creating and revising blog draft content.
 */
@EmbabelComponent
class DraftActions(
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(DraftActions::class.java)

    /**
     * Create an outline for the blog post based on research context.
     */
    @Action(canRerun = true)
    fun createOutline(
        draft: BlogDraft,
        context: OperationContext
    ): BlogDraft {
        logger.info("Creating outline for: {}", draft.title)

        val prompt = buildString {
            append("Create a detailed outline for a blog post.\n\n")
            append("Title: ${draft.title}\n")
            append("Topic: ${draft.topic}\n")
            if (!draft.instructions.isNullOrBlank()) {
                append("Instructions: ${draft.instructions}\n")
            }
            append("\nResearch Context:\n${draft.researchContext ?: "No additional context"}\n")
            append("\nCreate a structured outline with:\n")
            append("1. Introduction hook\n")
            append("2. Main sections (3-5 sections with key points)\n")
            append("3. Conclusion and call-to-action\n")
            append("\nFormat as markdown with headers and bullet points.")
        }

        val result = context.ai()
            .withDefaultLlm()
            .createObject(prompt, GeneratedContent::class.java)

        logger.info("Created outline with {} characters", result.text.length)

        return draft
            .withOutline(result.text)
            .withState(WorkflowState.OUTLINE_CREATED)
    }

    /**
     * Generate the full draft content based on the outline.
     */
    @Action(canRerun = true)
    fun generateDraft(
        draft: BlogDraft,
        context: OperationContext
    ): BlogDraft {
        logger.info("Generating draft content for: {}", draft.title)

        val prompt = buildString {
            append("Write a complete blog post based on the following outline.\n\n")
            append("Title: ${draft.title}\n")
            append("Topic: ${draft.topic}\n")
            if (!draft.instructions.isNullOrBlank()) {
                append("Instructions: ${draft.instructions}\n")
            }
            append("\nOutline:\n${draft.outline}\n")
            append("\nResearch Context (use for accuracy and examples):\n")
            append("${draft.researchContext ?: "No additional context"}\n")
            if (!draft.feedback.isNullOrBlank()) {
                append("\nPrevious feedback to address:\n${draft.feedback}\n")
            }
            append("\nWrite the full blog post as HTML suitable for Blogger.\n")
            append("Use <p>, <h2>, <h3>, <ul>, <li>, <code>, <pre> tags as appropriate.\n")
            append("Make it engaging, informative, and well-structured.\n")
            append("Include a compelling introduction and a clear conclusion.")
        }

        val result = context.ai()
            .withDefaultLlm()
            .createObject(prompt, GeneratedContent::class.java)

        logger.info("Generated draft with {} characters", result.text.length)

        return draft
            .withContent(result.text)
            .withState(WorkflowState.DRAFT_GENERATED)
    }

    /**
     * Revise the draft based on human feedback.
     */
    @Action(canRerun = true)
    fun reviseDraft(
        draft: BlogDraft,
        context: OperationContext
    ): BlogDraft {
        logger.info("Revising draft based on feedback. Revision #{}", draft.revisionCount)

        if (draft.revisionCount >= blogAgentProperties.hitl.maxRevisions) {
            logger.warn("Maximum revisions ({}) reached for draft: {}",
                blogAgentProperties.hitl.maxRevisions, draft.id)
        }

        val prompt = buildString {
            append("Revise this blog post based on the feedback provided.\n\n")
            append("Title: ${draft.title}\n")
            append("Topic: ${draft.topic}\n")
            append("\nCurrent content:\n${draft.content}\n")
            append("\nFeedback to address:\n${draft.feedback}\n")
            append("\nRevise the content to address the feedback while maintaining:\n")
            append("- The overall structure and flow\n")
            append("- HTML formatting for Blogger\n")
            append("- Accurate information from the research context\n")
            append("\nOutput the revised HTML content.")
        }

        val result = context.ai()
            .withDefaultLlm()
            .createObject(prompt, GeneratedContent::class.java)

        logger.info("Revised draft with {} characters", result.text.length)

        return draft
            .withContent(result.text)
            .withState(WorkflowState.DRAFT_GENERATED)
    }
}
