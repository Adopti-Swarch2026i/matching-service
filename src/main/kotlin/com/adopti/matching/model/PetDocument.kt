package com.adopti.matching.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Represents a pet report document as stored in Elasticsearch.
 * Maps directly to the fields from pets-service's Pet + Report models.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PetDocument(
    @JsonProperty("petId")
    val petId: Int,

    @JsonProperty("reportId")
    val reportId: Int,

    @JsonProperty("name")
    val name: String? = null,

    @JsonProperty("type")
    val type: String, // dog, cat, etc.

    @JsonProperty("breed")
    val breed: String? = null,

    @JsonProperty("color")
    val color: String? = null,

    @JsonProperty("age")
    val age: String? = null,

    @JsonProperty("status")
    val status: String, // lost, found, reunited

    @JsonProperty("location")
    val location: String? = null,

    @JsonProperty("city")
    val city: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("ownerId")
    val ownerId: String,

    @JsonProperty("ownerName")
    val ownerName: String? = null,

    @JsonProperty("imageUrls")
    val imageUrls: List<String> = emptyList(),

    @JsonProperty("createdAt")
    val createdAt: Instant = Instant.now(),

    @JsonProperty("updatedAt")
    val updatedAt: Instant = Instant.now()
)
