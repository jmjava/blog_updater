/*
 * Blog Chat Actions - Main entry point for chat interactions
 */
package com.blogagent.agent

import com.blogagent.config.BlogAgentProperties
import com.blogagent.model.WorkflowState
import com.blogagent.service.RagDataManager
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.ActionContext
import com.embabel.agent.rag.neo.drivine.DrivineStore
import com.embabel.agent.rag.tools.ToolishRag
import com.embabel.agent.rag.tools.TryHyDE
import com.embabel.chat.AssistantMessage
import com.embabel.chat.Conversation
import com.embabel.chat.UserMessage
import org.slf4j.LoggerFactory

/**
 * Chat actions for the Blog Agent.
 * Handles user messages and orchestrates the workflow.
 */
@EmbabelComponent
class BlogChatActions(
    private val blogWorkflowAgent: BlogWorkflowAgent,
    private val ragDataManager: RagDataManager,
    private val drivineStore: DrivineStore,
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(BlogChatActions::class.java)

    /**
     * Main action to respond to user messages.
     */
    @Action(canRerun = true, trigger = UserMessage::class)
    fun respond(
        conversation: Conversation,
        context: ActionContext
    ) {
        val userMessage = conversation.messages.lastOrNull { it is UserMessage }?.content ?: ""
        val sessionId = "default" // Session management would need to be implemented

        logger.info("Processing message: {}", userMessage.take(100))

        // Check for existing draft in session
        val currentDraft = blogWorkflowAgent.getDraftForSession(sessionId)

        // Route based on intent and current state
        val responseText = when {
            // Start new workflow
            userMessage.lowercase().startsWith("write about ") ||
            userMessage.lowercase().startsWith("create post about ") ||
            userMessage.lowercase().startsWith("blog about ") -> {
                handleNewTopic(userMessage, sessionId)
            }

            // Handle approval
            currentDraft?.state == WorkflowState.AWAITING_REVIEW &&
            (userMessage.lowercase().contains("approve") ||
             userMessage.lowercase().contains("looks good") ||
             userMessage.lowercase().contains("lgtm")) -> {
                handleApproval(currentDraft)
            }

            // Handle feedback (during review)
            currentDraft?.state == WorkflowState.AWAITING_REVIEW -> {
                handleFeedback(currentDraft, userMessage)
            }

            // Status check
            userMessage.lowercase().contains("status") ||
            userMessage.lowercase().contains("progress") -> {
                handleStatusCheck(currentDraft)
            }

            // Show full draft
            userMessage.lowercase().contains("show draft") ||
            userMessage.lowercase().contains("full draft") -> {
                handleShowDraft(currentDraft)
            }

            // List drafts
            userMessage.lowercase().contains("list draft") ||
            userMessage.lowercase().contains("my drafts") -> {
                handleListDrafts()
            }

            // Help
            userMessage.lowercase().contains("help") -> {
                handleHelp()
            }

            // General question - use RAG
            else -> {
                handleGeneralQuestion(userMessage, conversation, context)
            }
        }

        val assistantMessage = AssistantMessage(responseText)
        conversation.addMessage(assistantMessage)
        context.sendMessage(assistantMessage)
    }

    private fun handleNewTopic(userMessage: String, sessionId: String): String {
        val topic = userMessage
            .replace(Regex("^(write about |create post about |blog about )", RegexOption.IGNORE_CASE), "")
            .trim()

        if (topic.isBlank()) {
            return "Please provide a topic. Example: 'Write about Embabel agents'"
        }

        val draft = blogWorkflowAgent.startWorkflow(
            topic = topic,
            sessionId = sessionId
        )

        return buildString {
            append("## Starting Blog Workflow\n\n")
            append("**Topic:** $topic\n\n")
            append("I'll now:\n")
            append("1. Research relevant content from documentation and repos\n")
            append("2. Create an outline\n")
            append("3. Generate a draft\n")
            append("4. Ask for your review\n\n")
            append("Starting research...\n\n")
            append("_Draft ID: ${draft.id}_")
        }
    }

    private fun handleApproval(draft: com.blogagent.model.BlogDraft): String {
        val updatedDraft = draft.withState(WorkflowState.DRAFT_APPROVED)
        blogWorkflowAgent.updateDraft(updatedDraft)

        return buildString {
            append("## Draft Approved!\n\n")
            append("Proceeding to publish workflow:\n")
            append("1. Processing images (if any)\n")
            append("2. Creating post on Blogger\n")
            append("3. Ready for publishing\n\n")
            append("Say 'publish' when ready to make it live.")
        }
    }

    private fun handleFeedback(draft: com.blogagent.model.BlogDraft, feedback: String): String {
        if (draft.revisionCount >= blogAgentProperties.hitl.maxRevisions) {
            return buildString {
                append("## Maximum Revisions Reached\n\n")
                append("This draft has been revised ${draft.revisionCount} times.\n")
                append("Please either:\n")
                append("- **Approve** the current draft\n")
                append("- **Edit manually** in the draft file\n")
            }
        }

        val updatedDraft = draft
            .withFeedback(feedback)
            .withState(WorkflowState.FEEDBACK_RECEIVED)
        blogWorkflowAgent.updateDraft(updatedDraft)

        return buildString {
            append("## Feedback Received\n\n")
            append("I'll revise the draft based on your feedback:\n")
            append("> ${feedback.take(200)}${if (feedback.length > 200) "..." else ""}\n\n")
            append("Revision ${updatedDraft.revisionCount} in progress...")
        }
    }

    private fun handleStatusCheck(draft: com.blogagent.model.BlogDraft?): String {
        return if (draft != null) {
            blogWorkflowAgent.getWorkflowStatus(draft)
        } else {
            "No active draft in this session. Start with: 'Write about [topic]'"
        }
    }

    private fun handleShowDraft(draft: com.blogagent.model.BlogDraft?): String {
        return if (draft?.content != null) {
            buildString {
                append("## Draft: ${draft.title}\n\n")
                append("---\n\n")
                append(draft.content)
                append("\n\n---\n\n")
                append("_State: ${draft.state} | Revisions: ${draft.revisionCount}_")
            }
        } else {
            "No draft content available yet."
        }
    }

    private fun handleListDrafts(): String {
        val drafts = blogWorkflowAgent.getAllDrafts()
        return if (drafts.isEmpty()) {
            "No drafts found. Start with: 'Write about [topic]'"
        } else {
            buildString {
                append("## Your Drafts\n\n")
                drafts.forEach { draft ->
                    append("- **${draft.title}** (${draft.state})\n")
                    append("  ID: ${draft.id}\n")
                }
            }
        }
    }

    private fun handleHelp(): String {
        return buildString {
            append("## Blog Agent Help\n\n")
            append("### Commands\n\n")
            append("- **Write about [topic]** - Start a new blog post\n")
            append("- **Status** - Check current workflow status\n")
            append("- **Show draft** - View the full draft content\n")
            append("- **List drafts** - See all your drafts\n")
            append("- **Approve** - Approve draft (during review)\n")
            append("- **[feedback]** - Provide revision feedback (during review)\n")
            append("- **Publish** - Publish approved draft\n\n")
            append("### Workflow\n\n")
            append("1. Topic -> Research -> Outline -> Draft -> Review -> Publish\n")
            append("2. During review, approve or provide feedback\n")
            append("3. Maximum ${blogAgentProperties.hitl.maxRevisions} revisions per draft\n")
        }
    }

    private fun handleGeneralQuestion(
        question: String,
        conversation: Conversation,
        context: ActionContext
    ): String {
        logger.info("Handling general question with RAG: {}", question.take(100))

        // Use RAG to answer general questions
        val ragTool = ToolishRag(
            "docs",
            "Blog Agent documentation and ingested content",
            drivineStore
        ).withHint(TryHyDE.usingConversationContext())

        val templateModel = mapOf(
            "persona" to "helpful blog assistant"
        )

        return context.ai()
            .withDefaultLlm()
            .withReferences(ragDataManager.referencesForAllUsers())
            .withReference(ragTool)
            .withTemplate("blog_agent_system")
            .respondWithSystemPrompt(conversation, templateModel).content
    }
}
