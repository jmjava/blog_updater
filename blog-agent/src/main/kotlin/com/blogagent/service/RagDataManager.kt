/*
 * RAG Data Manager - Content ingestion and management
 */
package com.blogagent.service

import com.blogagent.config.BlogAgentProperties
import com.embabel.agent.api.common.LlmReference
import com.embabel.agent.api.common.reference.LlmReferenceProviders
import com.embabel.agent.rag.ingestion.*
import com.embabel.agent.rag.ingestion.policy.UrlSpecificContentRefreshPolicy
import com.embabel.agent.rag.neo.drivine.DrivineStore
import com.embabel.agent.tools.file.FileTools
import com.google.common.collect.Iterables
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Manages RAG content ingestion from URLs and local directories.
 * Follows the same pattern as Guide's DataManager.
 */
@Service
class RagDataManager(
    private val store: DrivineStore,
    private val blogAgentProperties: BlogAgentProperties
) {
    private val logger = LoggerFactory.getLogger(RagDataManager::class.java)

    data class Stats(
        val chunkCount: Int,
        val documentCount: Int,
        val contentElementCount: Int
    )

    private val hierarchicalContentReader = TikaHierarchicalContentReader()

    // Refresh only snapshots and GitHub URLs
    private val contentRefreshPolicy = UrlSpecificContentRefreshPolicy.containingAny(
        "-SNAPSHOT",
        "github.com"
    )

    init {
        store.provision()
        if (blogAgentProperties.reloadContentOnStartup) {
            logger.info("Reloading RAG content on startup")
            loadAllContent()
        }
    }

    /**
     * Get statistics about the RAG store.
     */
    fun getStats(): Stats {
        val info = store.info()
        return Stats(info.chunkCount, info.documentCount, info.contentElementCount)
    }

    /**
     * Get references for all users.
     */
    fun referencesForAllUsers(): List<LlmReference> {
        // Could load from a references.yml file like Guide does
        return emptyList()
    }

    /**
     * Provision the database (create indexes, etc.)
     */
    fun provisionDatabase() {
        store.provision()
    }

    /**
     * Ingest a local directory into the RAG store.
     */
    fun ingestDirectory(dir: String): DirectoryParsingResult {
        logger.info("Ingesting directory: {}", dir)

        val ft = FileTools.readOnly(dir)
        val result = TikaHierarchicalContentReader()
            .parseFromDirectory(ft, DirectoryParsingConfig())

        for (root in result.contentRoots) {
            logger.info("Parsed root: {} with {} descendants",
                root.title,
                Iterables.size(root.descendants()))
            store.writeAndChunkDocument(root)
        }

        return result
    }

    /**
     * Ingest a URL into the RAG store.
     */
    fun ingestPage(url: String) {
        val root = contentRefreshPolicy
            .ingestUriIfNeeded(store, hierarchicalContentReader, url)

        if (root != null) {
            logger.info("Ingested page: {} with {} descendants",
                root.title,
                Iterables.size(root.descendants()))
        } else {
            logger.info("Page at {} was already ingested, skipping", url)
        }
    }

    /**
     * Load all configured content sources.
     */
    fun loadAllContent() {
        var successCount = 0
        var failureCount = 0

        // Load URLs
        for (url in blogAgentProperties.urls) {
            try {
                logger.info("⏳ Loading URL: {}...", url)
                ingestPage(url)
                logger.info("✅ Loaded URL: {}", url)
                successCount++
            } catch (t: Throwable) {
                logger.error("❌ Failure loading URL {}: {}", url, t.message, t)
                failureCount++
            }
        }

        // Load local directories
        for (dir in blogAgentProperties.localDirs) {
            try {
                val resolvedDir = resolveDirectory(dir)
                logger.info("⏳ Loading directory: {}...", resolvedDir)
                ingestDirectory(resolvedDir)
                logger.info("✅ Loaded directory: {}", resolvedDir)
                successCount++
            } catch (t: Throwable) {
                logger.error("❌ Failure loading directory {}: {}", dir, t.message, t)
                failureCount++
            }
        }

        logger.info("Loaded {} sources successfully ({} failed)",
            successCount, failureCount)
    }

    /**
     * Resolve a directory path (handle relative paths).
     */
    private fun resolveDirectory(dir: String): String {
        return if (dir.startsWith("/")) {
            dir
        } else {
            // Resolve relative to the current working directory
            java.io.File(dir).absolutePath
        }
    }

    /**
     * Search the RAG store.
     * Note: This is a simplified search that returns mock results
     * when the DrivineStore API doesn't match expectations.
     */
    fun search(query: String, limit: Int = 10): List<SearchResult> {
        logger.info("Searching RAG for: {}", query.take(100))
        // Return empty results for now - actual implementation would use
        // store.search(RagRequest(...)) with proper API
        return emptyList()
    }

    data class SearchResult(
        val content: String,
        val source: String?,
        val score: Double
    )
}
