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
    @JsonProperty("ownerId") val ownerId: String,
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
 */
data class MatchFoundEvent(
    val eventId: String,
    val eventTimestamp: Instant = Instant.now(),
    val lostPetId: Int,
    val lostReportId: Int,
    val foundPetId: Int,
    val foundReportId: Int,
    val score: Double,
    val criteria: Map<String, Boolean>
)
