package com.bdc.loader;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ModuleSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class YamlLoader {

    private final ObjectMapper mapper;

    public YamlLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public CalendarSpec loadCalendar(Path path) throws IOException {
        return mapper.readValue(path.toFile(), CalendarSpec.class);
    }

    public ModuleSpec loadModule(Path path) throws IOException {
        return mapper.readValue(path.toFile(), ModuleSpec.class);
    }

    @SuppressWarnings("unchecked")
    public String detectKind(Path path) throws IOException {
        Map<String, Object> raw = mapper.readValue(path.toFile(), Map.class);
        return (String) raw.get("kind");
    }

    public ObjectMapper getMapper() {
        return mapper;
    }
}
