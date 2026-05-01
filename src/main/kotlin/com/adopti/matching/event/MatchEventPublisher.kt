package com.adopti.matching.event

import com.adopti.matching.config.RabbitMQConfig
import com.adopti.matching.model.MatchResult
import mu.KotlinLogging
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Publishes match.found events to RabbitMQ when the matching engine
 * detects a potential match between a lost and a found report.
 */
@Component
class MatchEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {

    fun publishMatchFound(matchResult: MatchResult) {
        val event = MatchFoundEvent(
            eventId = UUID.randomUUID().toString(),
            eventTimestamp = Instant.now(),
            lostPetId = matchResult.lostPetId,
            lostReportId = matchResult.lostReportId,
            foundPetId = matchResult.foundPetId,
            foundReportId = matchResult.foundReportId,
            score = matchResult.score,
            criteria = mapOf(
                "sameSpecies" to matchResult.criteria.sameSpecies,
                "similarBreed" to matchResult.criteria.similarBreed,
                "similarColor" to matchResult.criteria.similarColor,
                "sameCity" to matchResult.criteria.sameCity,
                "descriptionMatch" to matchResult.criteria.descriptionMatch
            )
        )

        logger.info {
            "Publishing match.found: lost=${event.lostPetId}, found=${event.foundPetId}, score=${event.score}"
        }

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EVENTS_EXCHANGE,
            RabbitMQConfig.MATCH_FOUND,
            event
        )
    }
}
