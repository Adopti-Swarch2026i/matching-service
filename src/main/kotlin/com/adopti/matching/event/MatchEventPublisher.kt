package com.adopti.matching.event

import com.adopti.matching.config.RabbitMQConfig
import com.adopti.matching.model.MatchResult
import mu.KotlinLogging
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Publishes match.found events to RabbitMQ when the matching engine
 * detects a potential match between a lost and a found report.
 *
 * Idempotencia: el `eventId` se deriva determinísticamente del par
 * (lostReportId, foundReportId) usando UUID v5 sobre un namespace fijo,
 * de modo que reemisiones del mismo match — ya sea por reinicio del
 * servicio, por reentregas de RabbitMQ o por re-disparo desde el otro lado
 * del par (lost↔found) — produzcan el MISMO eventId. Los consumers que
 * deduplican por `headers.eventId` (events.md §2.1) descartarán duplicados.
 */
@Component
class MatchEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {

    fun publishMatchFound(matchResult: MatchResult) {
        val eventId = deterministicEventId(matchResult.lostReportId, matchResult.foundReportId)
        val matchedAt = Instant.now()

        val event = MatchFoundEvent(
            lostPetId = matchResult.lostPetId,
            lostReportId = matchResult.lostReportId,
            lostOwnerId = matchResult.lostOwnerId,
            foundPetId = matchResult.foundPetId,
            foundReportId = matchResult.foundReportId,
            foundOwnerId = matchResult.foundOwnerId,
            score = matchResult.score,
            criteria = MatchCriteriaPayload(
                species = matchResult.snapshot.species,
                breed = matchResult.snapshot.breed,
                color = matchResult.snapshot.color,
                city = matchResult.snapshot.city
            ),
            matchedAt = matchedAt
        )

        logger.info {
            "Publishing match.found: lost=${event.lostPetId}, found=${event.foundPetId}, " +
                "score=${event.score}, eventId=$eventId"
        }

        // events.md §2: delivery_mode=2 (PERSISTENT), message_id, timestamp y
        // headers eventId / eventTimestamp obligatorios.
        val postProcessor = MessagePostProcessor { amqpMessage ->
            val props = amqpMessage.messageProperties
            props.deliveryMode = MessageDeliveryMode.PERSISTENT
            props.messageId = eventId
            props.timestamp = Date.from(matchedAt)
            props.contentType = "application/json"
            props.contentEncoding = "UTF-8"
            props.setHeader("eventId", eventId)
            props.setHeader("eventTimestamp", matchedAt.toString())
            amqpMessage
        }

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EVENTS_EXCHANGE,
            RabbitMQConfig.MATCH_FOUND,
            event,
            postProcessor
        )
    }

    /**
     * UUID v5 (RFC 4122) sobre namespace fijo + "lostReportId:foundReportId".
     * Resultado estable: el mismo par siempre produce el mismo UUID.
     */
    private fun deterministicEventId(lostReportId: Int, foundReportId: Int): String {
        val name = "$lostReportId:$foundReportId".toByteArray(Charsets.UTF_8)
        val namespaceBytes = uuidToBytes(MATCH_NAMESPACE)
        val hash = MessageDigest.getInstance("SHA-1").apply {
            update(namespaceBytes)
            update(name)
        }.digest()

        // Set version (5) y variant (RFC 4122).
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()

        val msb = ByteBuffer.wrap(hash, 0, 8).long
        val lsb = ByteBuffer.wrap(hash, 8, 8).long
        return UUID(msb, lsb).toString()
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val buf = ByteBuffer.allocate(16)
        buf.putLong(uuid.mostSignificantBits)
        buf.putLong(uuid.leastSignificantBits)
        return buf.array()
    }

    companion object {
        // Namespace UUID arbitrario pero fijo para Adopti matches.
        private val MATCH_NAMESPACE: UUID = UUID.fromString("6f1c1c5a-2b9d-4f5e-9c2a-ad0ff12026e1")
    }
}
