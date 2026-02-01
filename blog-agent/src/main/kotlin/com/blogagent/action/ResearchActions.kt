/*
 * Research Actions - RAG-based context gathering for content generation
 */
package com.blogagent.action

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.BlogDraft
import com.blogagent.model.WorkflowState
import com.blogagent.service.RagDataManager
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.OperationContext
import org.slf4j.LoggerFactory

/**
 * Actions for researching and gathering context from RAG.
 */
@EmbabelComponent
class ResearchActions(
    private val ragDataManager: RagDataManager,
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(ResearchActions::class.java)

    /**
     * Gather context from RAG for the given topic.
     */
    @Action(canRerun = true)
    fun gatherContext(
        draft: BlogDraft,
        context: OperationContext
    ): BlogDraft {
        logger.info("Gathering context for topic: {}", draft.topic)

        // Create a RAG query based on the topic and instructions
        val query = buildString {
            append("${draft.topic} ")
            if (!draft.instructions.isNullOrBlank()) {
                append(draft.instructions)
            }
        }

        // Get relevant chunks from the RAG store
        val searchResults = try {
            val results = ragDataManager.search(query, 10)
            results.joinToString("\n\n---\n\n") { result ->
                buildString {
                    append("Source: ${result.source ?: "Unknown"}\n")
                    append(result.content)
                }
            }
        } catch (e: Exception) {
            logger.warn("RAG search failed, using topic as context: {}", e.message)
            "Topic: ${draft.topic}"
        }

        logger.info("Found {} characters of research context", searchResults.length)

        return draft
            .withResearchContext(searchResults)
            .withState(WorkflowState.RESEARCH_COMPLETE)
    }
}
