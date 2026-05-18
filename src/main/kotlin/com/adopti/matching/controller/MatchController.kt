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
class MatchController(
    private val matchingEngine: MatchingEngine,
    private val elasticsearchService: ElasticsearchService
) {

    /**
     * Get potential matches for a specific pet.
     *
     * Si se pasa `reportId` se busca exactamente ese reporte; si no, se
     * resuelve el reporte más reciente del `petId`. Esto evita acoplar al
     * cliente con la convención interna de IDs cuando solo conoce la
     * mascota.
     */
    @GetMapping("/{petId}")
    fun getMatches(
        @PathVariable petId: Int,
        @RequestParam(required = false) reportId: Int?
    ): ResponseEntity<Any> {
        logger.info { "GET /api/matches/$petId reportId=$reportId" }

        val document = if (reportId != null) {
            elasticsearchService.getByReportId(reportId)
        } else {
            elasticsearchService.getLatestByPetId(petId)
        } ?: return ResponseEntity.status(404).body(
            ErrorResponse(
                error = "Not Found",
                message = "No indexed report found for petId=$petId" +
                    (reportId?.let { " (reportId=$it)" } ?: ""),
                status = 404
            )
        )

        val matches = matchingEngine.findMatches(document)

        val response = MatchListResponse(
            petId = petId,
            reportId = document.reportId,
            totalMatches = matches.size,
            matches = matches.map { MatchResponse.from(it) }
        )

        return ResponseEntity.ok(response)
    }
}
