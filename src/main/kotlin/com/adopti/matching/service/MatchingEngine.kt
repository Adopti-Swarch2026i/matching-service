package com.adopti.matching.service

import com.adopti.matching.model.MatchCriteria
import com.adopti.matching.model.MatchResult
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
            maxResults = maxResults
        )

        if (candidates.isEmpty()) {
            logger.debug { "No candidates found for petId=${document.petId}" }
            return emptyList()
        }

        // Normalize scores and build match results
        val maxScore = candidates.maxOf { it.second }
        if (maxScore == 0.0) return emptyList()

        return candidates
            .map { (candidate, rawScore) ->
                val normalizedScore = rawScore / maxScore
                val criteria = buildCriteria(document, candidate)

                val (lostPetId, lostReportId, foundPetId, foundReportId) =
                    if (document.status == "lost") {
                        listOf(document.petId, document.reportId, candidate.petId, candidate.reportId)
                    } else {
                        listOf(candidate.petId, candidate.reportId, document.petId, document.reportId)
                    }

                MatchResult(
                    lostPetId = lostPetId,
                    lostReportId = lostReportId,
                    foundPetId = foundPetId,
                    foundReportId = foundReportId,
                    score = normalizedScore,
                    criteria = criteria
                )
            }
            .filter { it.score >= scoreThreshold }
            .sortedByDescending { it.score }
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
