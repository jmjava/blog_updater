/*
 * Workflow State Tests
 */
package com.blogagent

import com.blogagent.model.WorkflowState
import com.blogagent.model.WorldStatePredicates
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for WorkflowState and WorldStatePredicates.
 */
class WorkflowStateTest {

    @Test
    fun `should convert state to predicates`() {
        val topicPredicates = WorldStatePredicates.fromState(WorkflowState.TOPIC_SELECTED)
        assertTrue(topicPredicates.contains(WorldStatePredicates.TOPIC_SELECTED))
        assertFalse(topicPredicates.contains(WorldStatePredicates.RESEARCH_COMPLETE))

        val researchPredicates = WorldStatePredicates.fromState(WorkflowState.RESEARCH_COMPLETE)
        assertTrue(researchPredicates.contains(WorldStatePredicates.TOPIC_SELECTED))
        assertTrue(researchPredicates.contains(WorldStatePredicates.RESEARCH_COMPLETE))
        assertFalse(researchPredicates.contains(WorldStatePredicates.OUTLINE_CREATED))
    }

    @Test
    fun `should track workflow completion`() {
        assertFalse(WorkflowState.TOPIC_SELECTED.isComplete())
        assertFalse(WorkflowState.AWAITING_REVIEW.isComplete())
        assertFalse(WorkflowState.POST_CREATED.isComplete())
        assertTrue(WorkflowState.PUBLISHED.isComplete())
    }

    @Test
    fun `should get next state in happy path`() {
        assertEquals(WorkflowState.RESEARCH_COMPLETE, WorkflowState.TOPIC_SELECTED.nextState())
        assertEquals(WorkflowState.OUTLINE_CREATED, WorkflowState.RESEARCH_COMPLETE.nextState())
        assertEquals(WorkflowState.DRAFT_GENERATED, WorkflowState.OUTLINE_CREATED.nextState())
        assertEquals(WorkflowState.AWAITING_REVIEW, WorkflowState.DRAFT_GENERATED.nextState())
        assertEquals(WorkflowState.DRAFT_APPROVED, WorkflowState.AWAITING_REVIEW.nextState())
        assertEquals(WorkflowState.IMAGES_ADDED, WorkflowState.DRAFT_APPROVED.nextState())
        assertEquals(WorkflowState.POST_CREATED, WorkflowState.IMAGES_ADDED.nextState())
        assertEquals(WorkflowState.PUBLISHED, WorkflowState.POST_CREATED.nextState())
        assertNull(WorkflowState.PUBLISHED.nextState())
    }

    @Test
    fun `feedback state should loop back to draft generated`() {
        assertEquals(WorkflowState.DRAFT_GENERATED, WorkflowState.FEEDBACK_RECEIVED.nextState())
        assertTrue(WorkflowState.FEEDBACK_RECEIVED.canTransitionTo(WorkflowState.DRAFT_GENERATED))
    }
}
