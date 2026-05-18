# Auditoría de Seguridad - Matching Service

Basado en la teoría de Seguridad (CIA Triad, Tácticas y Patrones Arquitectónicos):

## Cumple

### Tácticas: Resistir Ataques
*   **Encrypt Data:** Spring Boot expone únicamente el puerto **8443** con TLS mutuo. El `application.yml` define `server.ssl.enabled: true`, protocolo TLS con `enabled-protocols: TLSv1.2,TLSv1.3`, y cifrados AEAD. RabbitMQ se conecta vía **AMQPS 5671** con `ssl.enabled: true`, algoritmo `TLSv1.3`, y `verify-hostname: true`.
*   **Authenticate Actor:** 
    *   **mTLS mutua:** `client-auth: need` en el conector HTTPS. Solo clientes con certificado firmado por la CA interna (`truststore.jks`) pueden conectarse.
    *   **Elasticsearch HTTPS + auth:** `ElasticsearchConfig.kt` configura `RestClient` con esquema `https`, validación de certificado mediante CA personalizado (`ca.crt`), y autenticación básica HTTP (`UsernamePasswordCredentials`).
*   **Limit Access:** En docker-compose solo se expone el puerto **8443** internamente; el tráfico entra únicamente a través del gateway NGINX. El contenedor corre con usuario sin privilegios (`adopti`).
*   **Change Default Settings:** 
    *   No hay passwords hardcodeadas en el runtime; todas las credenciales se inyectan por variables de entorno.
    *   Las colas de RabbitMQ declaran Dead Letter Exchange (`adopti.events.dlx`) y TTL (`x-message-ttl: 86400000`), evitando acumulación infinita de mensajes.
    *   `defaultRequeueRejected: false` en el listener container factory evita loops infinitos de reintentos.

### Tácticas: Detectar Ataques / Recuperar
*   **Maintain Audit Trail:** Logs estructurados de Spring Boot (niveles DEBUG para `com.adopti`). Actuator expone `/actuator/health` e `/actuator/info`. Healthcheck en docker-compose realiza petición HTTPS con certificado de cliente.

## No Cumple / Gaps conocidos
*   **Dockerfile desincronizado:** El `Dockerfile` expone `8083` y su `HEALTHCHECK` interno apunta a `http://localhost:8083/health` (HTTP plano). Esto no representa un riesgo en producción porque docker-compose sobreescribe el healthcheck a HTTPS en el puerto 8443, pero debe alinearse para evitar confusiones y asegurar portabilidad del contenedor.
*   **Elasticsearch password vacío por defecto:** El `.env.example` deja `ELASTICSEARCH_PASSWORD=` vacío. Aunque esto es un placeholder, un despliegue sin completar esta variable resultaría en fallo de autenticación contra Elasticsearch (fail-safe, pero debe documentarse en el runbook).

## Decisiones del Laboratorio 5
*   **Aplicación del Secure Channel Pattern en este servicio:** Se implementó TLS 1.2/1.3 en el puerto 8443 con autenticación mutua (`client-auth: need`). La comunicación con Elasticsearch se migró a HTTPS con validación de CA personalizada y autenticación básica. La conexión con RabbitMQ se migró a AMQPS 5671 con verificación de hostname y truststore JKS, eliminando todo tráfico en texto plano.
