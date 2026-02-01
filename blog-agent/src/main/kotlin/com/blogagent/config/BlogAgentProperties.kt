/*
 * Blog Agent Configuration Properties
 */
package com.blogagent.config

import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for the Blog Agent application.
 *
 * @param reloadContentOnStartup Whether to reload RAG content on startup
 * @param bloggerApiUrl URL of the Blogger API (existing FastAPI service)
 * @param draftsPath Path to drafts directory relative to project root
 * @param defaultBlogId Default blog ID for posting
 * @param chatLlm LLM options for chat interactions
 * @param contentLlm LLM options for content generation
 * @param urls List of URLs to ingest for RAG (documentation, repos)
 * @param localDirs List of local directories to ingest for RAG
 * @param hitl Human-in-the-Loop configuration
 */
@Validated
@ConfigurationProperties(prefix = "blog-agent")
data class BlogAgentProperties(
    @DefaultValue("false")
    val reloadContentOnStartup: Boolean = false,

    @DefaultValue("http://localhost:8080")
    val bloggerApiUrl: String = "http://localhost:8080",

    @DefaultValue("../drafts")
    val draftsPath: String = "../drafts",

    val defaultBlogId: String? = null,

    val chatLlm: LlmOptions? = null,

    val contentLlm: LlmOptions? = null,

    @DefaultValue("")
    val urls: List<String> = emptyList(),

    @DefaultValue("")
    val localDirs: List<String> = emptyList(),

    val hitl: HitlConfig = HitlConfig()
)

/**
 * Human-in-the-Loop configuration
 */
data class HitlConfig(
    /** Whether to require human approval for drafts */
    @DefaultValue("true")
    val requireApproval: Boolean = true,

    /** Auto-approve drafts above this confidence threshold (0.0-1.0) */
    @DefaultValue("0.0")
    val autoApproveThreshold: Double = 0.0,

    /** Maximum number of revision iterations before requiring human decision */
    @DefaultValue("3")
    val maxRevisions: Int = 3
)
