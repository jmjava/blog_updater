/*
 * Blog Agent REST Controller
 */
package com.blogagent.controller

import com.blogagent.agent.BlogWorkflowAgent
import com.blogagent.model.BlogDraft
import com.blogagent.model.WorkflowState
import com.blogagent.service.BloggerService
import com.blogagent.service.RagDataManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for the Blog Agent.
 *
 * Provides endpoints for:
 * - Workflow management (start, status, approve, feedback)
 * - Draft management (list, get, delete)
 * - RAG management (stats, ingest)
 * - Health checks
 */
@RestController
@RequestMapping("/api/blog-agent")
class BlogAgentController(
    private val blogWorkflowAgent: BlogWorkflowAgent,
    private val ragDataManager: RagDataManager,
    private val bloggerService: BloggerService
) {

    // --- Workflow Endpoints ---

    @PostMapping("/workflow/start")
    fun startWorkflow(
        @RequestBody request: StartWorkflowRequest
    ): ResponseEntity<BlogDraft> {
        val draft = blogWorkflowAgent.startWorkflow(
            topic = request.topic,
            title = request.title,
            blogId = request.blogId,
            instructions = request.instructions,
            sessionId = request.sessionId
        )
        return ResponseEntity.ok(draft)
    }

    @GetMapping("/workflow/status/{draftId}")
    fun getWorkflowStatus(@PathVariable draftId: String): ResponseEntity<WorkflowStatusResponse> {
        val draft = blogWorkflowAgent.getDraft(draftId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(WorkflowStatusResponse(
            draft = draft,
            statusMarkdown = blogWorkflowAgent.getWorkflowStatus(draft),
            nextAction = blogWorkflowAgent.getNextActionRecommendation(draft)
        ))
    }

    @PostMapping("/workflow/{draftId}/approve")
    fun approveDraft(@PathVariable draftId: String): ResponseEntity<BlogDraft> {
        val draft = blogWorkflowAgent.getDraft(draftId)
            ?: return ResponseEntity.notFound().build()

        if (draft.state != WorkflowState.AWAITING_REVIEW) {
            return ResponseEntity.badRequest().build()
        }

        val updated = draft.withState(WorkflowState.DRAFT_APPROVED)
        blogWorkflowAgent.updateDraft(updated)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/workflow/{draftId}/feedback")
    fun provideFeedback(
        @PathVariable draftId: String,
        @RequestBody request: FeedbackRequest
    ): ResponseEntity<BlogDraft> {
        val draft = blogWorkflowAgent.getDraft(draftId)
            ?: return ResponseEntity.notFound().build()

        if (draft.state != WorkflowState.AWAITING_REVIEW) {
            return ResponseEntity.badRequest().build()
        }

        val updated = draft
            .withFeedback(request.feedback)
            .withState(WorkflowState.FEEDBACK_RECEIVED)
        blogWorkflowAgent.updateDraft(updated)
        return ResponseEntity.ok(updated)
    }

    // --- Draft Endpoints ---

    @GetMapping("/drafts")
    fun listDrafts(
        @RequestParam(required = false) state: WorkflowState?
    ): ResponseEntity<List<BlogDraft>> {
        val drafts = if (state != null) {
            blogWorkflowAgent.getDraftsByState(state)
        } else {
            blogWorkflowAgent.getAllDrafts()
        }
        return ResponseEntity.ok(drafts)
    }

    @GetMapping("/drafts/{draftId}")
    fun getDraft(@PathVariable draftId: String): ResponseEntity<BlogDraft> {
        val draft = blogWorkflowAgent.getDraft(draftId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(draft)
    }

    @DeleteMapping("/drafts/{draftId}")
    fun deleteDraft(@PathVariable draftId: String): ResponseEntity<Void> {
        val deleted = blogWorkflowAgent.deleteDraft(draftId)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/drafts/awaiting-review")
    fun getDraftsAwaitingReview(): ResponseEntity<List<BlogDraft>> {
        return ResponseEntity.ok(blogWorkflowAgent.getDraftsAwaitingReview())
    }

    // --- RAG Endpoints ---

    @GetMapping("/rag/stats")
    fun getRagStats(): ResponseEntity<RagDataManager.Stats> {
        return ResponseEntity.ok(ragDataManager.getStats())
    }

    @PostMapping("/rag/ingest/url")
    fun ingestUrl(@RequestBody request: IngestUrlRequest): ResponseEntity<Map<String, String>> {
        ragDataManager.ingestPage(request.url)
        return ResponseEntity.ok(mapOf("status" to "ok", "url" to request.url))
    }

    @PostMapping("/rag/ingest/directory")
    fun ingestDirectory(@RequestBody request: IngestDirectoryRequest): ResponseEntity<Map<String, Any>> {
        val result = ragDataManager.ingestDirectory(request.path)
        return ResponseEntity.ok(mapOf(
            "status" to "ok",
            "path" to request.path,
            "roots" to result.contentRoots.size
        ))
    }

    @PostMapping("/rag/search")
    fun search(@RequestBody request: SearchRequest): ResponseEntity<List<RagDataManager.SearchResult>> {
        val results = ragDataManager.search(request.query, request.limit ?: 10)
        return ResponseEntity.ok(results)
    }

    // --- Blogger API Endpoints ---

    @GetMapping("/blogger/blogs")
    fun listBlogs(): ResponseEntity<List<com.blogagent.model.BlogInfo>> {
        return ResponseEntity.ok(bloggerService.listBlogs())
    }

    @GetMapping("/blogger/health")
    fun bloggerHealth(): ResponseEntity<Map<String, Any>> {
        val healthy = bloggerService.healthCheck()
        return ResponseEntity.ok(mapOf(
            "status" to if (healthy) "ok" else "unhealthy",
            "healthy" to healthy
        ))
    }

    // --- Health Check ---

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}

// Request/Response DTOs

data class StartWorkflowRequest(
    val topic: String,
    val title: String? = null,
    val blogId: String? = null,
    val instructions: String? = null,
    val sessionId: String? = null
)

data class WorkflowStatusResponse(
    val draft: BlogDraft,
    val statusMarkdown: String,
    val nextAction: String
)

data class FeedbackRequest(
    val feedback: String
)

data class IngestUrlRequest(
    val url: String
)

data class IngestDirectoryRequest(
    val path: String
)

data class SearchRequest(
    val query: String,
    val limit: Int? = 10
)
