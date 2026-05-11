package com.adopti.matching.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.ssl.SSLContextBuilder
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory

@Configuration
class ElasticsearchConfig(
    @Value("\${elasticsearch.host}") private val host: String,
    @Value("\${elasticsearch.port}") private val port: Int,
    @Value("\${elasticsearch.scheme}") private val scheme: String,
    @Value("\${elasticsearch.username}") private val username: String,
    @Value("\${elasticsearch.password}") private val password: String,
    @Value("\${elasticsearch.ca-path:/app/certs/ca.crt}") private val caPath: String
) {

    @Bean
    fun restClient(): RestClient {
        val builder = RestClient.builder(HttpHost(host, port, scheme))
        if (scheme == "https") {
            builder.setHttpClientConfigCallback { httpClientBuilder: HttpAsyncClientBuilder ->
                val cf = CertificateFactory.getInstance("X.509")
                val caCert = FileInputStream(caPath).use { fis ->
                    cf.generateCertificate(fis)
                }
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null, null)
                keyStore.setCertificateEntry("ca", caCert)

                val sslContext = SSLContextBuilder()
                    .loadTrustMaterial(keyStore, null)
                    .build()
                httpClientBuilder.setSSLContext(sslContext)

                val credentialsProvider: CredentialsProvider = BasicCredentialsProvider()
                credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    UsernamePasswordCredentials(username, password)
                )
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            }
        }
        return builder.build()
    }

    @Bean
    fun elasticsearchClient(restClient: RestClient): ElasticsearchClient {
        val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        val transport = RestClientTransport(restClient, JacksonJsonpMapper(objectMapper))
        return ElasticsearchClient(transport)
    }
}
