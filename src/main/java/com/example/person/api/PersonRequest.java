package com.example.person.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

@Data
@Builder
public final class PersonRequest {

    @NotBlank(message = "Person name is required")
    @Length(max = 500, message = "Person name should be at most 500 characters long")
    private String name;

    @NotNull(message = "Person age is required")
    @Range(min = 0, max = 200, message = "Person age should be between 0 and 200")
    private Integer age;
}
