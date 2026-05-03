package com.adopti.matching.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.mapping.*
import co.elastic.clients.elasticsearch.core.DeleteRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.search.Hit
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import com.adopti.matching.model.PetDocument
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service responsible for all Elasticsearch operations:
 * - Index creation with proper mappings
 * - Document indexing
 * - Search queries
 */
@Service
class ElasticsearchService(
    private val esClient: ElasticsearchClient,
    private val objectMapper: ObjectMapper,
    @Value("\${elasticsearch.index-name}") private val indexName: String
) {

    /**
     * Creates the 'pets' index with proper mappings on application startup
     * if it doesn't already exist.
     */
    @PostConstruct
    fun initIndex() {
        try {
            val exists = esClient.indices().exists(
                ExistsRequest.Builder().index(indexName).build()
            ).value()

            if (!exists) {
                logger.info { "Creating Elasticsearch index: $indexName" }

                esClient.indices().create(CreateIndexRequest.Builder()
                    .index(indexName)
                    .mappings(TypeMapping.Builder()
                        .properties("petId", Property.Builder().integer(IntegerNumberProperty.Builder().build()).build())
                        .properties("reportId", Property.Builder().integer(IntegerNumberProperty.Builder().build()).build())
                        .properties("name", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .fields("keyword", Property.Builder().keyword(KeywordProperty.Builder().ignoreAbove(256).build()).build())
                            .build()).build())
                        .properties("type", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("breed", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .fields("keyword", Property.Builder().keyword(KeywordProperty.Builder().ignoreAbove(256).build()).build())
                            .build()).build())
                        .properties("color", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .fields("keyword", Property.Builder().keyword(KeywordProperty.Builder().ignoreAbove(256).build()).build())
                            .build()).build())
                        .properties("age", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("status", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("location", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .build()).build())
                        .properties("city", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .fields("keyword", Property.Builder().keyword(KeywordProperty.Builder().ignoreAbove(256).build()).build())
                            .build()).build())
                        .properties("description", Property.Builder().text(TextProperty.Builder()
                            .analyzer("standard")
                            .build()).build())
                        .properties("ownerId", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("ownerName", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("imageUrls", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .properties("createdAt", Property.Builder().date(DateProperty.Builder().build()).build())
                        .properties("updatedAt", Property.Builder().date(DateProperty.Builder().build()).build())
                        .build()
                    )
                    .build()
                )

                logger.info { "Index '$indexName' created successfully with mappings" }
            } else {
                logger.info { "Index '$indexName' already exists, skipping creation" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Elasticsearch index '$indexName'" }
            // Don't crash the app — ES might not be ready yet on first boot
        }
    }

    /**
     * Indexes a pet document in Elasticsearch.
     * Uses "report_{reportId}" as the document ID to allow updates via re-indexing.
     */
    fun indexPetDocument(document: PetDocument) {
        val docId = "report_${document.reportId}"

        // refresh=wait_for: garantiza que el siguiente search ve este doc.
        // Sin esto el matching engine puede no encontrar al recién indexado
        // si dos eventos llegan en sucesión rápida (events.md riesgo §6).
        esClient.index(IndexRequest.Builder<PetDocument>()
            .index(indexName)
            .id(docId)
            .document(document)
            .refresh(Refresh.WaitFor)
            .build()
        )

        logger.debug { "Indexed document $docId in index $indexName" }
    }

    fun deleteByReportId(reportId: Int) {
        val docId = "report_$reportId"
        esClient.delete(DeleteRequest.Builder()
            .index(indexName)
            .id(docId)
            .refresh(Refresh.WaitFor)
            .build()
        )
        logger.info { "Deleted document $docId from index $indexName" }
    }

    /**
     * Searches for pet documents matching the given criteria.
     * Used by the matching engine to find potential matches.
     *
     * @param oppositeStatus "found" if the incoming report is "lost", and vice versa
     * @param type species filter (exact match)
     * @param breed breed to fuzzy-match against
     * @param color color to fuzzy-match against
     * @param city city to match
     * @param description description to match against
     * @param maxDays only consider reports from the last N days
     * @param maxResults maximum results to return
     */
    fun searchForMatches(
        oppositeStatus: String,
        type: String,
        breed: String?,
        color: String?,
        city: String,
        description: String?,
        maxDays: Int,
        maxResults: Int,
        excludeReportId: Int? = null
    ): List<Pair<PetDocument, Double>> {
        val searchRequest = SearchRequest.Builder()
            .index(indexName)
            .size(maxResults)
            .query { q ->
                q.bool { bool ->
                    // MUST: opposite status (if incoming is "lost", search "found" and vice versa)
                    bool.must { must ->
                        must.term { t ->
                            t.field("status").value(oppositeStatus)
                        }
                    }

                    // MUST: same species (plan §6.4)
                    bool.must { must ->
                        must.term { t ->
                            t.field("type").value(type)
                        }
                    }

                    // MUST: same city (plan §6.4 — antes era SHOULD; permitía
                    // matches falsos por solo coincidir parcialmente en otros
                    // campos en ciudades distintas).
                    if (city.isNotBlank()) {
                        bool.must { must ->
                            must.match { m ->
                                m.field("city").query(city)
                            }
                        }
                    }

                    // MUST: within the last N days
                    bool.must { must ->
                        must.range { r ->
                            r.field("createdAt")
                                .gte(co.elastic.clients.json.JsonData.of("now-${maxDays}d"))
                        }
                    }

                    // MUST_NOT: el propio reporte (defensa profunda;
                    // el filtro de status opuesto ya impide self-match).
                    // El cliente Elasticsearch Java no acepta Int en .value();
                    // convertimos a Long para que matchee la sobrecarga.
                    if (excludeReportId != null) {
                        bool.mustNot { mn ->
                            mn.term { t ->
                                t.field("reportId").value(excludeReportId.toLong())
                            }
                        }
                    }

                    // SHOULD: fuzzy breed match (boosts score)
                    if (!breed.isNullOrBlank()) {
                        bool.should { should ->
                            should.match { m ->
                                m.field("breed").query(breed).fuzziness("AUTO").boost(3.0f)
                            }
                        }
                    }

                    // SHOULD: fuzzy color match (boosts score)
                    if (!color.isNullOrBlank()) {
                        bool.should { should ->
                            should.match { m ->
                                m.field("color").query(color).fuzziness("AUTO").boost(2.0f)
                            }
                        }
                    }

                    // SHOULD: description similarity
                    if (!description.isNullOrBlank()) {
                        bool.should { should ->
                            should.match { m ->
                                m.field("description").query(description).boost(1.0f)
                            }
                        }
                    }

                    bool
                }
            }
            .build()

        val response = esClient.search(searchRequest, PetDocument::class.java)

        return response.hits().hits().mapNotNull { hit: Hit<PetDocument> ->
            val doc = hit.source()
            val score = hit.score() ?: 0.0
            if (doc != null) Pair(doc, score) else null
        }
    }

    /**
     * Full-text search endpoint for the REST API.
     * Supports free-text query + optional filters.
     */
    fun search(
        query: String?,
        breed: String?,
        city: String?,
        type: String?,
        status: String?,
        page: Int = 1,
        pageSize: Int = 20
    ): Pair<List<PetDocument>, Long> {
        val from = (page - 1) * pageSize

        val searchRequest = SearchRequest.Builder()
            .index(indexName)
            .from(from)
            .size(pageSize)
            .query { q ->
                q.bool { bool ->
                    // Free text search across multiple fields
                    if (!query.isNullOrBlank()) {
                        bool.must { must ->
                            must.multiMatch { mm ->
                                mm.query(query)
                                    .fields("name^2", "breed^3", "color^2", "description", "location", "city^2")
                                    .fuzziness("AUTO")
                            }
                        }
                    }

                    // Filters
                    if (!type.isNullOrBlank()) {
                        bool.filter { filter ->
                            filter.term { t ->
                                t.field("type").value(type)
                            }
                        }
                    }

                    if (!breed.isNullOrBlank()) {
                        bool.filter { filter ->
                            filter.match { m ->
                                m.field("breed").query(breed).fuzziness("AUTO")
                            }
                        }
                    }

                    if (!city.isNullOrBlank()) {
                        bool.filter { filter ->
                            filter.match { m ->
                                m.field("city").query(city)
                            }
                        }
                    }

                    if (!status.isNullOrBlank()) {
                        bool.filter { filter ->
                            filter.term { t ->
                                t.field("status").value(status)
                            }
                        }
                    }

                    bool
                }
            }
            .build()

        val response = esClient.search(searchRequest, PetDocument::class.java)

        val documents = response.hits().hits().mapNotNull { it.source() }
        val total = response.hits().total()?.value() ?: 0L

        return Pair(documents, total)
    }

    /**
     * Retrieves a single pet document by its report ID.
     */
    fun getByReportId(reportId: Int): PetDocument? {
        return try {
            val response = esClient.get(
                { g -> g.index(indexName).id("report_$reportId") },
                PetDocument::class.java
            )
            response.source()
        } catch (e: Exception) {
            logger.warn { "Document report_$reportId not found in index $indexName" }
            null
        }
    }

    /**
     * Retrieves the most recent pet document for a given petId. Used by
     * the REST endpoint when the caller does not know the reportId.
     */
    fun getLatestByPetId(petId: Int): PetDocument? {
        return try {
            val req = SearchRequest.Builder()
                .index(indexName)
                .size(1)
                .query { q ->
                    q.term { t -> t.field("petId").value(petId.toLong()) }
                }
                .sort { s -> s.field { f -> f.field("createdAt").order(SortOrder.Desc) } }
                .build()
            val response = esClient.search(req, PetDocument::class.java)
            response.hits().hits().firstOrNull()?.source()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to lookup latest report for petId=$petId" }
            null
        }
    }
}
