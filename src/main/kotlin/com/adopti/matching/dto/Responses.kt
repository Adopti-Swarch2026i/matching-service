package com.adopti.matching.dto

import com.adopti.matching.model.MatchCriteria
import com.adopti.matching.model.MatchResult
import com.adopti.matching.model.PetDocument

/**
 * REST API response DTOs
 */

data class MatchResponse(
    val lostPetId: Int,
    val lostReportId: Int,
    val foundPetId: Int,
    val foundReportId: Int,
    val score: Double,
    val criteria: MatchCriteria
) {
    companion object {
        fun from(matchResult: MatchResult) = MatchResponse(
            lostPetId = matchResult.lostPetId,
            lostReportId = matchResult.lostReportId,
            foundPetId = matchResult.foundPetId,
            foundReportId = matchResult.foundReportId,
            score = matchResult.score,
            criteria = matchResult.criteria
        )
    }
}

data class MatchListResponse(
    val petId: Int,
    val reportId: Int,
    val totalMatches: Int,
    val matches: List<MatchResponse>
)

data class SearchResponse(
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val results: List<PetDocumentResponse>
)

data class PetDocumentResponse(
    val petId: Int,
    val reportId: Int,
    val name: String?,
    val type: String,
    val breed: String?,
    val color: String?,
    val age: String?,
    val status: String,
    val location: String?,
    val city: String,
    val description: String?,
    val ownerId: String,
    val ownerName: String?,
    val imageUrls: List<String>,
    val createdAt: String
) {
    companion object {
        fun from(doc: PetDocument) = PetDocumentResponse(
            petId = doc.petId,
            reportId = doc.reportId,
            name = doc.name,
            type = doc.type,
            breed = doc.breed,
            color = doc.color,
            age = doc.age,
            status = doc.status,
            location = doc.location,
            city = doc.city,
            description = doc.description,
            ownerId = doc.ownerId,
            ownerName = doc.ownerName,
            imageUrls = doc.imageUrls,
            createdAt = doc.createdAt.toString()
        )
    }
}

data class HealthResponse(
    val status: String = "UP",
    val service: String = "matching-service",
    val elasticsearch: String = "unknown"
)

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
