package com.adopti.matching.model

/**
 * Represents a potential match between two pet reports.
 */
data class MatchResult(
    val lostPetId: Int,
    val lostReportId: Int,
    val lostOwnerId: String,
    val foundPetId: Int,
    val foundReportId: Int,
    val foundOwnerId: String,
    val score: Double,
    val criteria: MatchCriteria,
    val snapshot: MatchSnapshot
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

/**
 * Snapshot of the matched values, aligned with the schema §4.6
 * (`criteria.{species, breed, color, city}` con valores efectivos).
 */
data class MatchSnapshot(
    val species: String? = null,
    val breed: String? = null,
    val color: String? = null,
    val city: String? = null
)
