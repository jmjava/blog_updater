/*
 * WebSocket Configuration for Blog Agent
 */
package com.blogagent.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * WebSocket configuration for real-time chat with the Blog Agent.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        // Enable a simple memory-based message broker
        config.enableSimpleBroker("/topic", "/queue")

        // Prefix for messages from clients to server
        config.setApplicationDestinationPrefixes("/app")

        // Prefix for user-specific messages
        config.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // WebSocket endpoint for STOMP connections
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()

        // Plain WebSocket endpoint (without SockJS)
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }
}
