package com.adopti.matching.model

/**
 * Represents a potential match between two pet reports.
 */
data class MatchResult(
    val lostPetId: Int,
    val lostReportId: Int,
    val foundPetId: Int,
    val foundReportId: Int,
    val score: Double,
    val criteria: MatchCriteria
)

/**
 * Details about which criteria contributed to the match score.
 */
data class MatchCriteria(
    val sameSpecies: Boolean = false,
    val similarBreed: Boolean = false,
    val similarColor: Boolean = false,
    val sameCity: Boolean = false,
    val descriptionMatch: Boolean = false
)
