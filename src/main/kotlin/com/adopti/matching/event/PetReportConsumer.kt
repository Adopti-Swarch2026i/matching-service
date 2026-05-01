package com.adopti.matching.event

import com.adopti.matching.config.RabbitMQConfig
import com.adopti.matching.model.PetDocument
import com.adopti.matching.service.ElasticsearchService
import com.adopti.matching.service.MatchingEngine
import mu.KotlinLogging
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * RabbitMQ consumer that listens to pet report events from pets-service.
 *
 * On `pet.report.created`: indexes the document in ES and triggers matching.
 * On `pet.report.updated`: re-indexes the document with updated fields.
 */
@Component
class PetReportConsumer(
    private val elasticsearchService: ElasticsearchService,
    private val matchingEngine: MatchingEngine,
    private val matchEventPublisher: MatchEventPublisher
) {

    @RabbitListener(queues = [RabbitMQConfig.MATCHING_QUEUE])
    fun handlePetReportEvent(event: PetReportCreatedEvent) {
        logger.info { "Received pet.report event: petId=${event.petId}, status=${event.status}" }

        try {
            val document = PetDocument(
                petId = event.petId,
                reportId = event.reportId,
                name = event.name,
                type = event.type,
                breed = event.breed,
                color = event.color,
                age = event.age,
                status = event.status,
                location = event.location,
                city = event.city,
                description = event.description,
                ownerId = event.ownerId,
                ownerName = event.ownerName,
                imageUrls = event.imageUrls,
                createdAt = event.createdAt
            )

            // Index the document in Elasticsearch
            elasticsearchService.indexPetDocument(document)
            logger.info { "Indexed pet document: petId=${event.petId}, reportId=${event.reportId}" }

            // Run matching logic only for lost/found (not reunited)
            if (event.status in listOf("lost", "found")) {
                val matches = matchingEngine.findMatches(document)
                logger.info { "Found ${matches.size} potential matches for petId=${event.petId}" }

                matches.forEach { match ->
                    matchEventPublisher.publishMatchFound(match)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing pet report event for petId=${event.petId}" }
            throw e // Let RabbitMQ retry / send to DLQ
        }
    }
}
