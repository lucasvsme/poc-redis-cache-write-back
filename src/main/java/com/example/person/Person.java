package com.example.person;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "PERSON")
@Data
@NoArgsConstructor
public class Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 5090380600159441769L;

    @Id
    @Column(name = "PERSON_ID")
    private UUID id;

    @Column(name = "PERSON_NAME")
    private String name;

    @Column(name = "PERSON_AGE")
    private Integer age;
}
