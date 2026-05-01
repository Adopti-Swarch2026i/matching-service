package com.adopti.matching.config

import org.springframework.amqp.core.*
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
        const val MATCHING_DLQ = "matching.queue.dlq"

        // Routing keys this service listens to
        const val PET_REPORT_CREATED = "pet.report.created"
        const val PET_REPORT_UPDATED = "pet.report.updated"

        // Routing key this service publishes
        const val MATCH_FOUND = "match.found"
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
            .withArgument("x-dead-letter-routing-key", "matching.dlq")
            .build()
    }

    @Bean
    fun matchingDlq(): Queue {
        return QueueBuilder
            .durable(MATCHING_DLQ)
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
    fun bindDlq(matchingDlq: Queue, dlxExchange: TopicExchange): Binding {
        return BindingBuilder
            .bind(matchingDlq)
            .to(dlxExchange)
            .with("matching.dlq")
    }

    // ── Converter & Template ──────────────────────────────

    @Bean
    fun jsonMessageConverter(): MessageConverter {
        return Jackson2JsonMessageConverter()
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
}
