package com.adopti.matching.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQConfig {

    companion object {
        // Exchange names
        const val EVENTS_EXCHANGE = "adopti.events"
        const val DLX_EXCHANGE = "adopti.events.dlx"

        // Queue names
        const val MATCHING_QUEUE = "matching.queue"
        // events.md §1 define una DLQ unificada `adopti.dlq` con bind `#`
        // sobre el DLX. Este servicio la declara para que el contrato del
        // sistema (no solo de matching) tenga un destino observable cuando
        // un mensaje muere en cualquier consumer.
        const val UNIFIED_DLQ = "adopti.dlq"

        // Routing keys this service listens to
        const val PET_REPORT_CREATED = "pet.report.created"
        const val PET_REPORT_UPDATED = "pet.report.updated"
        const val PET_REPORT_DELETED = "pet.report.deleted"

        // Routing key this service publishes
        const val MATCH_FOUND = "match.found"

        // events.md §1: x-message-ttl=86400000 (24h) en queues principales.
        const val QUEUE_TTL_MS: Long = 86_400_000L
    }

    // ── Exchanges ──────────────────────────────────────────

    @Bean
    fun eventsExchange(): TopicExchange {
        return ExchangeBuilder
            .topicExchange(EVENTS_EXCHANGE)
            .durable(true)
            .build()
    }

    @Bean
    fun dlxExchange(): TopicExchange {
        return ExchangeBuilder
            .topicExchange(DLX_EXCHANGE)
            .durable(true)
            .build()
    }

    // ── Queues ─────────────────────────────────────────────

    @Bean
    fun matchingQueue(): Queue {
        return QueueBuilder
            .durable(MATCHING_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-message-ttl", QUEUE_TTL_MS)
            .build()
    }

    /**
     * DLQ unificada (events.md §1). Cualquier mensaje muerto que enrute al
     * DLX cae aquí gracias al bind `#`.
     */
    @Bean
    fun unifiedDlq(): Queue {
        return QueueBuilder
            .durable(UNIFIED_DLQ)
            .build()
    }

    // ── Bindings ───────────────────────────────────────────

    @Bean
    fun bindCreated(matchingQueue: Queue, eventsExchange: TopicExchange): Binding {
        return BindingBuilder
            .bind(matchingQueue)
            .to(eventsExchange)
            .with(PET_REPORT_CREATED)
    }

    @Bean
    fun bindUpdated(matchingQueue: Queue, eventsExchange: TopicExchange): Binding {
        return BindingBuilder
            .bind(matchingQueue)
            .to(eventsExchange)
            .with(PET_REPORT_UPDATED)
    }

    @Bean
    fun bindDeleted(matchingQueue: Queue, eventsExchange: TopicExchange): Binding {
        return BindingBuilder
            .bind(matchingQueue)
            .to(eventsExchange)
            .with(PET_REPORT_DELETED)
    }

    @Bean
    fun bindUnifiedDlq(unifiedDlq: Queue, dlxExchange: TopicExchange): Binding {
        return BindingBuilder
            .bind(unifiedDlq)
            .to(dlxExchange)
            .with("#")
    }

    // ── Converter & Template ──────────────────────────────

    @Bean
    fun jsonMessageConverter(objectMapper: com.fasterxml.jackson.databind.ObjectMapper): MessageConverter {
        return Jackson2JsonMessageConverter(objectMapper)
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter
    ): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter
        return template
    }

    /**
     * events.md §5.1 prohíbe `basic_nack(requeue=true)` (loop). Spring AMQP
     * por defecto re-encola los mensajes que lanzan excepción; aquí lo
     * desactivamos para que el broker enrute al DLX.
     */
    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(messageConverter)
        factory.setDefaultRequeueRejected(false)
        return factory
    }
}
