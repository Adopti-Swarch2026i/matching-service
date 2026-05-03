package com.adopti.matching.event

import com.adopti.matching.config.RabbitMQConfig
import com.adopti.matching.model.PetDocument
import com.adopti.matching.service.ElasticsearchService
import com.adopti.matching.service.MatchingEngine
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.util.Collections

private val logger = KotlinLogging.logger {}

private const val IDEMPOTENCY_CACHE_SIZE = 1024

/**
 * RabbitMQ consumer for pet report events from pets-service.
 *
 * Una sola queue (`matching.queue`) recibe `pet.report.created` y
 * `pet.report.updated`; deserializamos según la routing key porque cada
 * evento tiene un schema diferente (events.md §4.1 vs §4.2).
 */
@Component
class PetReportConsumer(
    private val elasticsearchService: ElasticsearchService,
    private val matchingEngine: MatchingEngine,
    private val matchEventPublisher: MatchEventPublisher,
    private val objectMapper: ObjectMapper
) {

    // events.md §2.1 exige idempotencia respecto a headers.eventId. La
    // indexación en ES ya es upsert por reportId y la publicación de
    // match.found es determinística por UUID v5; este cache solo evita el
    // trabajo repetido cuando llegan reentregas del broker dentro de la
    // misma instancia. Limitado para no crecer sin tope.
    private val processedEventIds: MutableSet<String> = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(IDEMPOTENCY_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean =
                size > IDEMPOTENCY_CACHE_SIZE
        }.let { Collections.synchronizedMap(it) }
    )

    @RabbitListener(queues = [RabbitMQConfig.MATCHING_QUEUE])
    fun handle(message: Message) {
        val routingKey = message.messageProperties.receivedRoutingKey
        val eventId = message.messageProperties.headers["eventId"]?.toString()
            ?: message.messageProperties.messageId
        val body = message.body
        logger.info { "Received message routingKey=$routingKey eventId=$eventId" }

        if (!eventId.isNullOrBlank() && !processedEventIds.add(eventId)) {
            logger.info { "Skipping duplicate event eventId=$eventId" }
            return
        }

        try {
            when (routingKey) {
                RabbitMQConfig.PET_REPORT_CREATED -> {
                    val event = objectMapper.readValue(body, PetReportCreatedEvent::class.java)
                    handleCreated(event)
                }
                RabbitMQConfig.PET_REPORT_UPDATED -> {
                    val event = objectMapper.readValue(body, PetReportUpdatedEvent::class.java)
                    handleUpdated(event)
                }
                RabbitMQConfig.PET_REPORT_DELETED -> {
                    val event = objectMapper.readValue(body, PetReportDeletedEvent::class.java)
                    handleDeleted(event)
                }
                else -> {
                    logger.warn { "Unexpected routingKey=$routingKey, ignoring" }
                }
            }
        } catch (e: Exception) {
            // Si falla, sacamos el eventId del cache para permitir reproceso
            // tras un retry/redelivery posterior.
            if (!eventId.isNullOrBlank()) {
                processedEventIds.remove(eventId)
            }
            logger.error(e) { "Error processing event routingKey=$routingKey" }
            // Sin requeue (RabbitMQConfig.rabbitListenerContainerFactory) → DLX.
            throw e
        }
    }

    private fun handleCreated(event: PetReportCreatedEvent) {
        logger.info { "pet.report.created: petId=${event.petId}, status=${event.status}" }

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

        elasticsearchService.indexPetDocument(document)

        if (event.status in listOf("lost", "found")) {
            val matches = matchingEngine.findMatches(document)
            logger.info { "Found ${matches.size} potential matches for petId=${event.petId}" }
            matches.forEach { matchEventPublisher.publishMatchFound(it) }
        }
    }

    private fun handleUpdated(event: PetReportUpdatedEvent) {
        logger.info {
            "pet.report.updated: petId=${event.petId}, reportId=${event.reportId}, fields=${event.changedFields}"
        }

        // Cargamos el documento existente y aplicamos los campos modificados.
        // Si no existe (update llegó antes que el created por carrera de
        // mensajes), reindexamos con lo que vino en el evento.
        val existing = elasticsearchService.getByReportId(event.reportId)
        val merged = if (existing != null) {
            existing.copy(
                type = event.type ?: existing.type,
                breed = event.breed ?: existing.breed,
                color = event.color ?: existing.color,
                name = event.name ?: existing.name,
                age = event.age ?: existing.age,
                status = event.status ?: existing.status,
                location = event.location ?: existing.location,
                city = event.city ?: existing.city,
                description = event.description ?: existing.description,
                ownerName = event.ownerName ?: existing.ownerName,
                imageUrls = event.imageUrls ?: existing.imageUrls
            )
        } else {
            // Update llegó antes que el created (carrera de mensajes) y el
            // schema §4.2 de pet.report.updated NO incluye ownerId. Sin
            // ownerId no podemos construir un PetDocument completo: dejamos
            // de procesar este update y esperamos al siguiente created/update
            // que sí traiga la información mínima.
            if (event.ownerId.isNullOrBlank()) {
                logger.warn {
                    "Skipping update for reportId=${event.reportId}: no document indexed yet and event lacks ownerId"
                }
                return
            }
            PetDocument(
                petId = event.petId,
                reportId = event.reportId,
                name = event.name,
                type = event.type ?: "unknown",
                breed = event.breed,
                color = event.color,
                age = event.age,
                status = event.status ?: "lost",
                location = event.location,
                city = event.city ?: "",
                description = event.description,
                ownerId = event.ownerId,
                ownerName = event.ownerName,
                imageUrls = event.imageUrls ?: emptyList(),
                createdAt = event.updatedAt
            )
        }

        elasticsearchService.indexPetDocument(merged)

        // Si el status sigue siendo lost/found, re-evaluar matches con la
        // información actualizada (e.g. nuevo color/breed).
        if (merged.status in listOf("lost", "found")) {
            val matches = matchingEngine.findMatches(merged)
            matches.forEach { matchEventPublisher.publishMatchFound(it) }
        }
    }

    private fun handleDeleted(event: PetReportDeletedEvent) {
        logger.info { "pet.report.deleted: reportId=${event.reportId}" }
        elasticsearchService.deleteByReportId(event.reportId)
    }
}
