package com.example.person.api;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public final class PersonResponse {

    @NotNull
    private UUID id;

    @NotNull
    private String name;

    @NotNull
    private Integer age;
}
