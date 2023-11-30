package com.example.person;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "person-service.cache")
@Getter
@Setter
public class PersonServiceCacheProperties {

    private long writeBackRate;

    private String writeBackKey;
}
