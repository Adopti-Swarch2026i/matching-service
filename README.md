# 🐾 matching-service

Microservicio de matching para **Adopti** — construido con **Kotlin** + **Spring Boot 3** + **Elasticsearch 8** + **RabbitMQ**.

Cuando un usuario reporta una mascota como `lost` o `found`, este servicio indexa el reporte en Elasticsearch y automáticamente busca coincidencias con reportes del tipo opuesto (`found` ↔ `lost`), emitiendo un evento `match.found` que el `notification-service` consume para alertar al dueño.

## Arquitectura del servicio

```
┌─────────────────────────────────────────────────────────────┐
│                    matching-service (Kotlin)                  │
│                                                              │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────┐ │
│  │  RabbitMQ     │───▶│  PetReport       │───▶│ Matching   │ │
│  │  Consumer     │    │  Consumer        │    │ Engine     │ │
│  └──────────────┘    └────────┬─────────┘    └─────┬──────┘ │
│                               │                     │        │
│                      index    │              search  │        │
│                               ▼                     ▼        │
│                    ┌──────────────────────────────────┐      │
│                    │       ElasticsearchService       │      │
│                    └──────────────────────────────────┘      │
│                               │                              │
│  ┌──────────────┐            │    ┌────────────────────┐    │
│  │  REST API     │            │    │  Match Event       │    │
│  │  Controllers  │            │    │  Publisher          │    │
│  └──────────────┘            │    └────────────────────┘    │
│   /api/matches/{petId}       │     publishes match.found     │
│   /api/search                │                               │
│   /health                    │                               │
└──────────────────────────────┼───────────────────────────────┘
                               │
                               ▼
                      ┌──────────────┐
                      │Elasticsearch │
                      │  index: pets │
                      └──────────────┘
```

## Estructura del proyecto

```
matching-service/
├── build.gradle.kts                    # Dependencias (Spring Boot 3, ES, RabbitMQ)
├── settings.gradle.kts
├── Dockerfile                          # Multi-stage: Gradle build → JRE 17 Alpine
├── docker-compose.matching.yml         # ES + RabbitMQ + matching-service (dev local)
├── .env.example
└── src/main/kotlin/com/adopti/matching/
    ├── MatchingServiceApplication.kt   # Entry point
    ├── config/
    │   ├── ElasticsearchConfig.kt      # ES Java client bean
    │   └── RabbitMQConfig.kt           # Exchanges, queues, bindings, DLQ
    ├── model/
    │   ├── PetDocument.kt              # Documento ES (pet + report fields)
    │   └── MatchResult.kt              # Resultado de matching con score
    ├── event/
    │   ├── Events.kt                   # DTOs de eventos RabbitMQ
    │   ├── PetReportConsumer.kt        # Consumer de pet.report.created/updated
    │   └── MatchEventPublisher.kt      # Publisher de match.found
    ├── service/
    │   ├── ElasticsearchService.kt     # CRUD ES + queries + init de índice
    │   └── MatchingEngine.kt           # Lógica de matching con scoring
    ├── controller/
    │   ├── MatchController.kt          # GET /api/matches/{petId}
    │   ├── SearchController.kt         # GET /api/search
    │   └── HealthController.kt         # GET /health
    └── dto/
        └── Responses.kt                # DTOs de respuesta REST
```

## Endpoints REST

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/matches/{petId}?reportId=N` | Matches sugeridos para un reporte |
| `GET` | `/api/search?q=...&breed=...&city=...&type=...&status=...&page=1&pageSize=20` | Búsqueda avanzada full-text |
| `GET` | `/health` | Health check (verifica conexión a ES) |

## Eventos RabbitMQ

### Consume

| Routing key | Cola | Descripción |
|-------------|------|-------------|
| `pet.report.created` | `matching.queue` | Indexa el reporte en ES y ejecuta matching |
| `pet.report.updated` | `matching.queue` | Re-indexa el reporte con datos actualizados |

### Publica

| Routing key | Descripción |
|-------------|-------------|
| `match.found` | Cuando se encuentra una coincidencia entre lost/found |

## Requisitos previos

- **Java 17+** (Temurin/OpenJDK)
- **Docker** y **Docker Compose** (para ES y RabbitMQ)
