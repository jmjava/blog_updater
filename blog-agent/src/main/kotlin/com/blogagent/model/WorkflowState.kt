/*
 * Workflow State Definitions for GOAP Planning
 */
package com.blogagent.model

/**
 * Workflow states for the blog content generation pipeline.
 * These states are used for GOAP planning to determine which
 * actions are available at each stage.
 */
enum class WorkflowState {
    /**
     * Initial state: User has provided a topic for the blog post
     */
    TOPIC_SELECTED,

    /**
     * Research phase complete: Context has been gathered from RAG
     */
    RESEARCH_COMPLETE,

    /**
     * Outline created: Structured outline for the post is ready
     */
    OUTLINE_CREATED,

    /**
     * Draft generated: Full content has been generated
     */
    DRAFT_GENERATED,

    /**
     * Awaiting review: Draft is pending human approval (HITL checkpoint)
     */
    AWAITING_REVIEW,

    /**
     * Feedback received: Human has provided feedback for revision
     */
    FEEDBACK_RECEIVED,

    /**
     * Draft approved: Human has approved the draft
     */
    DRAFT_APPROVED,

    /**
     * Images added: All images have been uploaded and attached
     */
    IMAGES_ADDED,

    /**
     * Post created: Draft post has been created on Blogger
     */
    POST_CREATED,

    /**
     * Published: Post has been published
     */
    PUBLISHED;

    /**
     * Check if this state allows transitioning to another state
     */
    fun canTransitionTo(target: WorkflowState): Boolean {
        return when (this) {
            TOPIC_SELECTED -> target == RESEARCH_COMPLETE
            RESEARCH_COMPLETE -> target == OUTLINE_CREATED
            OUTLINE_CREATED -> target == DRAFT_GENERATED
            DRAFT_GENERATED -> target == AWAITING_REVIEW
            AWAITING_REVIEW -> target in listOf(DRAFT_APPROVED, FEEDBACK_RECEIVED)
            FEEDBACK_RECEIVED -> target == DRAFT_GENERATED
            DRAFT_APPROVED -> target == IMAGES_ADDED
            IMAGES_ADDED -> target == POST_CREATED
            POST_CREATED -> target == PUBLISHED
            PUBLISHED -> false
        }
    }

    /**
     * Check if this is a HITL checkpoint requiring human interaction
     */
    fun isHitlCheckpoint(): Boolean = this == AWAITING_REVIEW

    /**
     * Check if the workflow is complete
     */
    fun isComplete(): Boolean = this == PUBLISHED

    /**
     * Get the next expected state in the happy path
     */
    fun nextState(): WorkflowState? = when (this) {
        TOPIC_SELECTED -> RESEARCH_COMPLETE
        RESEARCH_COMPLETE -> OUTLINE_CREATED
        OUTLINE_CREATED -> DRAFT_GENERATED
        DRAFT_GENERATED -> AWAITING_REVIEW
        AWAITING_REVIEW -> DRAFT_APPROVED
        FEEDBACK_RECEIVED -> DRAFT_GENERATED
        DRAFT_APPROVED -> IMAGES_ADDED
        IMAGES_ADDED -> POST_CREATED
        POST_CREATED -> PUBLISHED
        PUBLISHED -> null
    }
}

/**
 * World state predicates for GOAP planning
 */
object WorldStatePredicates {
    const val TOPIC_SELECTED = "topic_selected"
    const val RESEARCH_COMPLETE = "research_complete"
    const val OUTLINE_CREATED = "outline_created"
    const val DRAFT_GENERATED = "draft_generated"
    const val AWAITING_REVIEW = "awaiting_review"
    const val FEEDBACK_RECEIVED = "feedback_received"
    const val DRAFT_APPROVED = "draft_approved"
    const val IMAGES_ADDED = "images_added"
    const val POST_CREATED = "post_created"
    const val PUBLISHED = "published"

    /**
     * Convert workflow state to set of predicates
     */
    fun fromState(state: WorkflowState): Set<String> {
        val predicates = mutableSetOf<String>()

        // Add all predicates up to and including current state
        when (state) {
            WorkflowState.PUBLISHED -> predicates.add(PUBLISHED)
            else -> {}
        }
        if (state.ordinal >= WorkflowState.POST_CREATED.ordinal) predicates.add(POST_CREATED)
        if (state.ordinal >= WorkflowState.IMAGES_ADDED.ordinal) predicates.add(IMAGES_ADDED)
        if (state.ordinal >= WorkflowState.DRAFT_APPROVED.ordinal) predicates.add(DRAFT_APPROVED)
        if (state == WorkflowState.AWAITING_REVIEW) predicates.add(AWAITING_REVIEW)
        if (state == WorkflowState.FEEDBACK_RECEIVED) predicates.add(FEEDBACK_RECEIVED)
        if (state.ordinal >= WorkflowState.DRAFT_GENERATED.ordinal) predicates.add(DRAFT_GENERATED)
        if (state.ordinal >= WorkflowState.OUTLINE_CREATED.ordinal) predicates.add(OUTLINE_CREATED)
        if (state.ordinal >= WorkflowState.RESEARCH_COMPLETE.ordinal) predicates.add(RESEARCH_COMPLETE)
        if (state.ordinal >= WorkflowState.TOPIC_SELECTED.ordinal) predicates.add(TOPIC_SELECTED)

        return predicates
    }
}
