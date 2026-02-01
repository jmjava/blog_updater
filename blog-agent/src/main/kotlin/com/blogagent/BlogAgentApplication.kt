/*
 * Blog Agent - Embabel Guide-based agent for iterative blog content generation
 */
package com.blogagent

import org.drivine.autoconfigure.EnableDrivine
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.GraphObjectManagerFactory
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Blog Agent Application
 *
 * An Embabel Guide-based agent that enables iterative blog content generation
 * with Human-in-the-Loop (HITL) workflow. Uses Neo4j for RAG to ingest
 * documentation and repositories for context-aware content generation.
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.embabel", "com.blogagent"])
@ConfigurationPropertiesScan
@EnableScheduling
@EnableDrivine
@EnableDrivinePropertiesConfig
class BlogAgentApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SpringApplication(BlogAgentApplication::class.java)
            app.webApplicationType = WebApplicationType.SERVLET
            app.run(*args)
        }
    }

    @Bean("neo")
    fun neoManager(factory: PersistenceManagerFactory): PersistenceManager {
        return factory.get("neo")
    }

    @Bean("neoGraphObjectManager")
    fun neoGraphObjectManager(factory: GraphObjectManagerFactory): GraphObjectManager {
        return factory.get("neo")
    }
}
