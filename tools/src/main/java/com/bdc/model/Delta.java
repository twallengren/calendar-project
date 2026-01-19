package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Delta.Add.class, name = "add"),
    @JsonSubTypes.Type(value = Delta.Remove.class, name = "remove"),
    @JsonSubTypes.Type(value = Delta.Reclassify.class, name = "reclassify")
})
public sealed interface Delta permits Delta.Add, Delta.Remove, Delta.Reclassify {

    record Add(
        String key,
        String name,
        LocalDate date,
        EventType classification
    ) implements Delta {}

    record Remove(
        String key,
        LocalDate date
    ) implements Delta {}

    record Reclassify(
        String key,
        LocalDate date,
        EventType newClassification
    ) implements Delta {}
}
