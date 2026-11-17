package dev.interviewcopilot.bff;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the committed contract {@code openapi/bff.json} in step with the code.
 * Regenerate with {@code ./mvnw test -Dopenapi.update=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTests {

    @Autowired
    MockMvc mvc;

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        Path file = Path.of("openapi/bff.json");
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(mapper.readTree(generated)) + "\n");
        }
        assertThat(mapper.readTree(Files.readString(file))).isEqualTo(mapper.readTree(generated));
    }
}
