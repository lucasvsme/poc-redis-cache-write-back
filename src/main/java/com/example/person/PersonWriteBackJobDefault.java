package com.example.person;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PersonWriteBackJobDefault implements PersonWriteBackJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonWriteBackJobDefault.class);

    private final PersonServiceCacheProperties cacheProperties;
    private final PersonRepository personRepository;
    private final RedisTemplate<String, Person> personRedisTemplate;

    public PersonWriteBackJobDefault(PersonServiceCacheProperties cacheProperties,
                                     PersonRepository personRepository,
                                     RedisTemplate<String, Person> personRedisTemplate) {
        this.cacheProperties = cacheProperties;
        this.personRepository = personRepository;
        this.personRedisTemplate = personRedisTemplate;
    }

    @Override
    @Scheduled(fixedRateString = "${person-service.cache.write-back-rate}")
    public void writeBack() {
        final var amountOfPeopleToPersist = personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey()).size();
        if (amountOfPeopleToPersist == null || amountOfPeopleToPersist == 0) {
            LOGGER.info("None people to write back from cache to database");
            return;
        }

        LOGGER.info("Found {} people to write back from cache to database", amountOfPeopleToPersist);
        final var setOperations = personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey());
        final var scanOptions = ScanOptions.scanOptions().build();

        try (final var cursor = setOperations.scan(scanOptions)) {
            assert cursor != null;
            while (cursor.hasNext()) {
                final var person = cursor.next();
                personRepository.save(person);
                LOGGER.info("Person saved (person={})", person);

                personRedisTemplate.boundSetOps(cacheProperties.getWriteBackKey()).remove(person);
                LOGGER.info("Person removed from {} set (person={})", cacheProperties.getWriteBackKey(), person);
            }
            LOGGER.info("Persisted {} people in the database", amountOfPeopleToPersist);
        } catch (RuntimeException exception) {
            LOGGER.error("Error reading {} set from Redis", cacheProperties.getWriteBackKey(), exception);
        }
    }
}
