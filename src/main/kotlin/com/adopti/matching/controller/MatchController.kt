package com.adopti.matching.controller

import com.adopti.matching.dto.MatchListResponse
import com.adopti.matching.dto.MatchResponse
import com.adopti.matching.dto.ErrorResponse
import com.adopti.matching.service.ElasticsearchService
import com.adopti.matching.service.MatchingEngine
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * REST controller for match-related endpoints.
 *
 * GET /api/matches/{petId}?reportId=... → returns suggested matches for a pet report.
 */
@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = ["*"])
class MatchController(
    private val matchingEngine: MatchingEngine,
    private val elasticsearchService: ElasticsearchService
) {

    /**
     * Get potential matches for a specific pet report.
     *
     * The petId in the path identifies the pet, and reportId (required query param)
     * identifies which specific report to match against.
     */
    @GetMapping("/{petId}")
    fun getMatches(
        @PathVariable petId: Int,
        @RequestParam reportId: Int
    ): ResponseEntity<Any> {
        logger.info { "GET /api/matches/$petId?reportId=$reportId" }

        // Verify the document exists
        val document = elasticsearchService.getByReportId(reportId)
            ?: return ResponseEntity.status(404).body(
                ErrorResponse(
                    error = "Not Found",
                    message = "Report $reportId not found in search index. It may not have been indexed yet.",
                    status = 404
                )
            )

        val matches = matchingEngine.findMatches(document)

        val response = MatchListResponse(
            petId = petId,
            reportId = reportId,
            totalMatches = matches.size,
            matches = matches.map { MatchResponse.from(it) }
        )

        return ResponseEntity.ok(response)
    }
}
