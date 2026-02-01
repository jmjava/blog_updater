/*
 * Blog Workflow Integration Test
 */
package com.blogagent

import com.blogagent.agent.BlogWorkflowAgent
import com.blogagent.config.BlogAgentProperties
import com.blogagent.config.HitlConfig
import com.blogagent.model.WorkflowState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for the Blog Workflow Agent.
 */
class BlogWorkflowTest {

    private lateinit var agent: BlogWorkflowAgent
    private lateinit var properties: BlogAgentProperties

    @BeforeEach
    fun setup() {
        properties = BlogAgentProperties(
            reloadContentOnStartup = false,
            bloggerApiUrl = "http://localhost:8080",
            draftsPath = "../drafts",
            defaultBlogId = "test-blog-id",
            chatLlm = null,
            contentLlm = null,
            urls = emptyList(),
            localDirs = emptyList(),
            hitl = HitlConfig(
                requireApproval = true,
                autoApproveThreshold = 0.0,
                maxRevisions = 3
            )
        )
        agent = BlogWorkflowAgent(properties)
    }

    @Test
    fun `should start workflow from topic`() {
        val draft = agent.startWorkflow(
            topic = "Test Topic",
            title = "My Test Blog Post",
            sessionId = "test-session"
        )

        assertNotNull(draft.id)
        assertEquals("Test Topic", draft.topic)
        assertEquals("My Test Blog Post", draft.title)
        assertEquals(WorkflowState.TOPIC_SELECTED, draft.state)
        assertEquals("test-blog-id", draft.blogId)
    }

    @Test
    fun `should track draft by session`() {
        val draft = agent.startWorkflow(
            topic = "Session Test",
            sessionId = "session-123"
        )

        val retrieved = agent.getDraftForSession("session-123")
        assertNotNull(retrieved)
        assertEquals(draft.id, retrieved!!.id)
    }

    @Test
    fun `should update draft state`() {
        val draft = agent.startWorkflow(topic = "State Test")

        val updated = draft.withState(WorkflowState.RESEARCH_COMPLETE)
        agent.updateDraft(updated)

        val retrieved = agent.getDraft(draft.id)
        assertEquals(WorkflowState.RESEARCH_COMPLETE, retrieved?.state)
    }

    @Test
    fun `should track revisions with feedback`() {
        var draft = agent.startWorkflow(topic = "Revision Test")

        draft = draft.withFeedback("First feedback")
        assertEquals(1, draft.revisionCount)
        assertEquals("First feedback", draft.feedback)

        draft = draft.withFeedback("Second feedback")
        assertEquals(2, draft.revisionCount)
    }

    @Test
    fun `should recommend next action based on state`() {
        val draft = agent.startWorkflow(topic = "Action Test")

        assertEquals("gather_context", agent.getNextActionRecommendation(draft))

        val afterResearch = draft.withState(WorkflowState.RESEARCH_COMPLETE)
        assertEquals("create_outline", agent.getNextActionRecommendation(afterResearch))

        val afterOutline = afterResearch.withState(WorkflowState.OUTLINE_CREATED)
        assertEquals("generate_draft", agent.getNextActionRecommendation(afterOutline))
    }

    @Test
    fun `should identify HITL checkpoints`() {
        assertFalse(WorkflowState.TOPIC_SELECTED.isHitlCheckpoint())
        assertFalse(WorkflowState.DRAFT_GENERATED.isHitlCheckpoint())
        assertTrue(WorkflowState.AWAITING_REVIEW.isHitlCheckpoint())
        assertFalse(WorkflowState.DRAFT_APPROVED.isHitlCheckpoint())
    }

    @Test
    fun `should validate state transitions`() {
        assertTrue(WorkflowState.TOPIC_SELECTED.canTransitionTo(WorkflowState.RESEARCH_COMPLETE))
        assertFalse(WorkflowState.TOPIC_SELECTED.canTransitionTo(WorkflowState.PUBLISHED))

        assertTrue(WorkflowState.AWAITING_REVIEW.canTransitionTo(WorkflowState.DRAFT_APPROVED))
        assertTrue(WorkflowState.AWAITING_REVIEW.canTransitionTo(WorkflowState.FEEDBACK_RECEIVED))
    }

    @Test
    fun `should list drafts by state`() {
        agent.startWorkflow(topic = "Draft 1").let {
            agent.updateDraft(it.withState(WorkflowState.AWAITING_REVIEW))
        }
        agent.startWorkflow(topic = "Draft 2").let {
            agent.updateDraft(it.withState(WorkflowState.AWAITING_REVIEW))
        }
        agent.startWorkflow(topic = "Draft 3") // Still TOPIC_SELECTED

        val awaitingReview = agent.getDraftsAwaitingReview()
        assertEquals(2, awaitingReview.size)
    }

    @Test
    fun `should delete draft and remove session mapping`() {
        val draft = agent.startWorkflow(topic = "Delete Test", sessionId = "delete-session")

        assertTrue(agent.deleteDraft(draft.id))
        assertNull(agent.getDraft(draft.id))
        assertNull(agent.getDraftForSession("delete-session"))
    }
}
