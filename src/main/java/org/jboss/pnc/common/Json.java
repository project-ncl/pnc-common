package org.jboss.pnc.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Json {

    /**
     * Creates ObjectMapper setup with common modules that are used across PNC services.
     * 
     * @return Object Mapper setup for PNC.
     */
    public static ObjectMapper newObjectMapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
    }

    public static <T extends Number> Optional<T> getNumber(JsonNode node, String path) {
        JsonNode jsonNode = node.at(path);
        if (jsonNode.isMissingNode()) {
            return Optional.empty();
        } else {
            if (jsonNode.isNumber()) {
                return Optional.of((T) jsonNode.numberValue());
            } else {
                // number is probably quoted
                String value = jsonNode.textValue();
                return Optional.of((T) Long.valueOf(value));
            }
        }
    }

    public static Optional<String> getText(JsonNode rootNode, String path) {
        JsonNode jsonNode = rootNode.at(path);
        if (jsonNode.isMissingNode()) {
            return Optional.empty();
        } else {
            return Optional.of(jsonNode.asText());
        }
    }
}
