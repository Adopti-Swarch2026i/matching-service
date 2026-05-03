package com.adopti.matching.service

import com.adopti.matching.model.MatchCriteria
import com.adopti.matching.model.MatchResult
import com.adopti.matching.model.MatchSnapshot
import com.adopti.matching.model.PetDocument
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Core matching engine that finds potential matches between lost and found reports.
 *
 * When a "lost" report comes in, it searches for "found" reports with similar
 * characteristics (species, breed, color, city) and vice versa.
 *
 * The scoring system uses Elasticsearch's built-in relevance scoring (BM25)
 * with custom boosts, then normalizes and applies a threshold filter.
 */
@Service
class MatchingEngine(
    private val elasticsearchService: ElasticsearchService,
    @Value("\${matching.score-threshold}") private val scoreThreshold: Double,
    @Value("\${matching.max-days}") private val maxDays: Int,
    @Value("\${matching.max-results}") private val maxResults: Int
) {

    /**
     * Finds potential matches for a given pet document.
     *
     * @param document The newly created/updated pet report
     * @return List of match results sorted by score (highest first)
     */
    fun findMatches(document: PetDocument): List<MatchResult> {
        // Determine which status to search for
        val oppositeStatus = when (document.status) {
            "lost" -> "found"
            "found" -> "lost"
            else -> return emptyList()
        }

        logger.debug {
            "Searching for matches: ${document.status} ${document.type} " +
            "breed=${document.breed} color=${document.color} city=${document.city}"
        }

        // Query Elasticsearch for potential matches
        val candidates = elasticsearchService.searchForMatches(
            oppositeStatus = oppositeStatus,
            type = document.type,
            breed = document.breed,
            color = document.color,
            city = document.city,
            description = document.description,
            maxDays = maxDays,
            maxResults = maxResults,
            excludeReportId = document.reportId
        )

        if (candidates.isEmpty()) {
            logger.debug { "No candidates found for petId=${document.petId}" }
            return emptyList()
        }

        // events.md §4.6 exige score ∈ [0,1] con semántica ABSOLUTA. Por eso
        // calculamos el score a partir de los criterios efectivamente
        // satisfechos (weights fijos que suman 1.0), no normalizando contra
        // el `_score` máximo del lote — que produciría 1.0 al mejor candidato
        // siempre, neutralizando el umbral.
        return candidates
            .map { (candidate, _) ->
                val criteria = buildCriteria(document, candidate)
                val absoluteScore = computeAbsoluteScore(criteria)

                val lostPetId: Int
                val lostReportId: Int
                val foundPetId: Int
                val foundReportId: Int
                val lostOwnerId: String
                val foundOwnerId: String

                if (document.status == "lost") {
                    lostPetId = document.petId
                    lostReportId = document.reportId
                    lostOwnerId = document.ownerId
                    foundPetId = candidate.petId
                    foundReportId = candidate.reportId
                    foundOwnerId = candidate.ownerId
                } else {
                    lostPetId = candidate.petId
                    lostReportId = candidate.reportId
                    lostOwnerId = candidate.ownerId
                    foundPetId = document.petId
                    foundReportId = document.reportId
                    foundOwnerId = document.ownerId
                }

                MatchResult(
                    lostPetId = lostPetId,
                    lostReportId = lostReportId,
                    lostOwnerId = lostOwnerId,
                    foundPetId = foundPetId,
                    foundReportId = foundReportId,
                    foundOwnerId = foundOwnerId,
                    score = absoluteScore,
                    criteria = criteria,
                    snapshot = MatchSnapshot(
                        species = document.type.takeIf { it.isNotBlank() }
                            ?: candidate.type.takeIf { it.isNotBlank() },
                        breed = document.breed ?: candidate.breed,
                        color = document.color ?: candidate.color,
                        city = document.city.takeIf { it.isNotBlank() }
                            ?: candidate.city.takeIf { it.isNotBlank() }
                    )
                )
            }
            .filter { it.score >= scoreThreshold }
            .sortedByDescending { it.score }
    }

    private fun computeAbsoluteScore(criteria: MatchCriteria): Double {
        // Weights suman 1.0 (events.md §4.6 score ∈ [0,1]).
        var score = 0.0
        if (criteria.sameSpecies) score += 0.30
        if (criteria.sameCity) score += 0.25
        if (criteria.similarBreed) score += 0.25
        if (criteria.similarColor) score += 0.15
        if (criteria.descriptionMatch) score += 0.05
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Finds matches for an already-indexed pet document, identified by its pet ID.
     * Used by the REST API endpoint.
     */
    fun findMatchesForPetId(petId: Int, reportId: Int): List<MatchResult> {
        val document = elasticsearchService.getByReportId(reportId) ?: return emptyList()
        return findMatches(document)
    }

    /**
     * Builds a criteria object describing which fields contributed to the match.
     */
    private fun buildCriteria(source: PetDocument, candidate: PetDocument): MatchCriteria {
        return MatchCriteria(
            sameSpecies = source.type.equals(candidate.type, ignoreCase = true),
            similarBreed = !source.breed.isNullOrBlank() &&
                    !candidate.breed.isNullOrBlank() &&
                    (source.breed.contains(candidate.breed, ignoreCase = true) ||
                     candidate.breed.contains(source.breed, ignoreCase = true) ||
                     source.breed.equals(candidate.breed, ignoreCase = true)),
            similarColor = !source.color.isNullOrBlank() &&
                    !candidate.color.isNullOrBlank() &&
                    (source.color.contains(candidate.color, ignoreCase = true) ||
                     candidate.color.contains(source.color, ignoreCase = true) ||
                     source.color.equals(candidate.color, ignoreCase = true)),
            sameCity = source.city.equals(candidate.city, ignoreCase = true),
            descriptionMatch = !source.description.isNullOrBlank() &&
                    !candidate.description.isNullOrBlank()
        )
    }
}
