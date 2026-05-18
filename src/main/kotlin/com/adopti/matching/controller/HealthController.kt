package com.adopti.matching.controller

import com.adopti.matching.dto.HealthResponse
import co.elastic.clients.elasticsearch.ElasticsearchClient
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

/**
 * Health check endpoint.
 */
@RestController
class HealthController(
    private val esClient: ElasticsearchClient
) {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        val esStatus = try {
            val info = esClient.info()
            "connected (version ${info.version().number()})"
        } catch (e: Exception) {
            logger.warn(e) { "Elasticsearch health check failed" }
            "disconnected: ${e.message}"
        }

        val response = HealthResponse(
            status = if (esStatus.startsWith("connected")) "UP" else "DEGRADED",
            elasticsearch = esStatus
        )

        val httpStatus = if (response.status == "UP") 200 else 503
        return ResponseEntity.status(httpStatus).body(response)
    }
}
