package com.example;

import com.example.person.Person;
import com.example.person.PersonRepository;
import com.example.person.PersonServiceCacheProperties;
import com.example.person.api.PersonRequest;
import com.example.person.api.PersonResponse;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApplicationTest.TestConfig.class)
@AutoConfigureWebTestClient
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationTest {

    private static PersonRequest personRequest;
    private static UUID personId;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PersonServiceCacheProperties cacheProperties;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RedisTemplate<String, Person> personRedisTemplate;

    @RepeatedTest(10)
    @Order(1)
    void creatingPerson() {
        personRequest = PersonRequest.builder()
                .name("John Smith")
                .age(45)
                .build();
        final var exchangeResult = webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody().isEmpty();

        personId = getPersonIdFromLocationHeader(exchangeResult);
    }

    @RepeatedTest(10)
    @Order(2)
    void findingPersonCreatedById() {
        final var exchangeResult = webTestClient.get()
                .uri("/people/{personId}", personId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(PersonResponse.class)
                .returnResult();

        final var personResponse = exchangeResult.getResponseBody();

        assertNotNull(personResponse);
        assertEquals(personId, personResponse.getId());
        assertEquals(personRequest.getName(), personResponse.getName());
        assertEquals(personRequest.getAge(), personResponse.getAge());
    }

    @Test
    @Order(3)
    void writeBackJobPersistedPeopleInTheDatabaseAndDeletedThemFromCache() {
        final var twoSeconds = Duration.ofMillis(cacheProperties.getWriteBackRate() * 2);

        // Checking all people were persisted in the database by the write back job
        Awaitility.await()
                .atMost(twoSeconds)
                .untilAsserted(() -> assertEquals(10, personRepository.count()));

        // Checking all people persisted were removed from the cache database
        final var cacheEntries = personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey());
        Awaitility.await()
                .atMost(twoSeconds)
                .untilAsserted(() -> assertEquals(0, cacheEntries.size()));
    }

    @Test
    void findingUnknownPersonById() {
        final var randomPersonId = UUID.randomUUID();

        webTestClient.get()
                .uri("/people/{personId}", randomPersonId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.type").value(Matchers.equalTo("about:blank"), String.class)
                .jsonPath("$.title").value(Matchers.equalTo("Person not found by ID"), String.class)
                .jsonPath("$.status").value(Matchers.equalTo(404), Integer.class)
                .jsonPath("$.detail").value(Matchers.equalTo("No person with ID " + randomPersonId + " exists"), String.class)
                .jsonPath("$.instance").value(Matchers.equalTo("/people/" + randomPersonId), String.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void creatingPersonWithoutName(String name) {
        testCreatePersonWithInvalidName(name);
    }

    @Test
    void creatingPersonWithNameTooLong() {
        testCreatePersonWithInvalidName("John".repeat(126));
    }

    @Test
    void creatingPersonWithoutAge() {
        testCreatePersonWithInvalidAge(null);
    }

    @Test
    void creatingPersonTooYoung() {
        testCreatePersonWithInvalidAge(-1);
    }

    @Test
    void creatingPersonTooOld() {
        testCreatePersonWithInvalidAge(201);
    }

    private void testCreatePersonWithInvalidName(String name) {
        final var personRequest = PersonRequest.builder()
                .name(name)
                .age(45)
                .build();

        webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void testCreatePersonWithInvalidAge(Integer age) {
        final var personRequest = PersonRequest.builder()
                .name("John Smith")
                .age(age)
                .build();

        webTestClient.post()
                .uri("/people")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(personRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private UUID getPersonIdFromLocationHeader(EntityExchangeResult<Void> exchangeResult) {
        final var responseHeaders = exchangeResult.getResponseHeaders();
        final var location = responseHeaders.getLocation();
        assert location != null;

        final var segments = location.toString().split("/");
        final var lastSegment = segments[segments.length - 1];

        return UUID.fromString(lastSegment);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @ServiceConnection(type = JdbcConnectionDetails.class)
        PostgreSQLContainer<?> POSTGRES_CONTAINER() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres"));
        }

        @Bean
        @ServiceConnection(name = "redis", type = RedisConnectionDetails.class)
        GenericContainer<?> REDIS_CONTAINER() {
            return new GenericContainer<>(DockerImageName.parse("redis"))
                    .withExposedPorts(6379);
        }

        @Bean
        @Primary
        PersonServiceCacheProperties personServiceCacheProperties() {
            final var properties = new PersonServiceCacheProperties();

            properties.setWriteBackKey("person:write_back_test");
            properties.setWriteBackRate(1000 /* milliseconds */);

            return properties;
        }
    }
}
