package com.adopti.matching.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Event consumed from RabbitMQ when pets-service creates a new report.
 * Matches the schema defined in the event contract (plan.markdown §7.2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PetReportCreatedEvent(
    @JsonProperty("petId") val petId: Int,
    @JsonProperty("reportId") val reportId: Int,
    @JsonProperty("ownerId") val ownerId: String,
    @JsonProperty("type") val type: String,       // dog, cat, etc.
    @JsonProperty("breed") val breed: String? = null,
    @JsonProperty("color") val color: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("age") val age: String? = null,
    @JsonProperty("status") val status: String,    // lost or found
    @JsonProperty("location") val location: String? = null,
    @JsonProperty("city") val city: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("ownerName") val ownerName: String? = null,
    @JsonProperty("imageUrls") val imageUrls: List<String> = emptyList(),
    @JsonProperty("createdAt") val createdAt: Instant = Instant.now()
)

/**
 * Event consumed from RabbitMQ when pets-service updates a report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PetReportUpdatedEvent(
    @JsonProperty("petId") val petId: Int,
    @JsonProperty("reportId") val reportId: Int,
    @JsonProperty("ownerId") val ownerId: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("breed") val breed: String? = null,
    @JsonProperty("color") val color: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("age") val age: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("location") val location: String? = null,
    @JsonProperty("city") val city: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("ownerName") val ownerName: String? = null,
    @JsonProperty("imageUrls") val imageUrls: List<String>? = null,
    @JsonProperty("changedFields") val changedFields: List<String> = emptyList(),
    @JsonProperty("updatedAt") val updatedAt: Instant = Instant.now()
)

/**
 * Event published to RabbitMQ when a potential match is found.
 *
 * Body == data del schema (events.md §7.2). Los metadatos `eventId` y
 * `eventTimestamp` viajan SOLO en headers AMQP, no en el body.
 */
data class MatchFoundEvent(
    val lostPetId: Int,
    val lostReportId: Int,
    val lostOwnerId: String,
    val foundPetId: Int,
    val foundReportId: Int,
    val foundOwnerId: String,
    val score: Double,
    val criteria: MatchCriteriaPayload,
    val matchedAt: Instant
)

/**
 * Mirror del schema §4.6 `criteria`: species/breed/color/city (string|null).
 * Los flags booleanos del MatchCriteria interno NO van aquí — los consumers
 * que necesiten ese detalle pueden recalcularlo a partir de los valores.
 */
data class MatchCriteriaPayload(
    val species: String? = null,
    val breed: String? = null,
    val color: String? = null,
    val city: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PetReportDeletedEvent(
    val petId: Int,
    val reportId: Int,
    val ownerId: String? = null,
    val deletedAt: Instant? = null
)
