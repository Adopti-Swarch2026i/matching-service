package com.adopti.matching.controller

import com.adopti.matching.dto.PetDocumentResponse
import com.adopti.matching.dto.SearchResponse
import com.adopti.matching.service.ElasticsearchService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * REST controller for advanced search endpoints.
 *
 * GET /api/search?q=...&breed=...&city=...&type=...&status=...&page=...&pageSize=...
 *
 * This replaces the basic LIKE-based search in pets-service with full-text
 * Elasticsearch search supporting fuzzy matching and relevance scoring.
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = ["*"])
class SearchController(
    private val elasticsearchService: ElasticsearchService
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) breed: String?,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<SearchResponse> {
        logger.info { "GET /api/search?q=$q&breed=$breed&city=$city&type=$type&status=$status&page=$page" }

        val (documents, total) = elasticsearchService.search(
            query = q,
            breed = breed,
            city = city,
            type = type,
            status = status,
            page = page,
            pageSize = pageSize
        )

        val response = SearchResponse(
            total = total,
            page = page,
            pageSize = pageSize,
            results = documents.map { PetDocumentResponse.from(it) }
        )

        return ResponseEntity.ok(response)
    }
}
