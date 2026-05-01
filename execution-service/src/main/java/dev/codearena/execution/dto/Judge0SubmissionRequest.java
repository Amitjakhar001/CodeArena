package dev.codearena.execution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Judge0SubmissionRequest(
        @JsonProperty("source_code") String sourceCode,
        @JsonProperty("language_id") int languageId,
        @JsonProperty("stdin") String stdin,
        @JsonProperty("expected_output") String expectedOutput,
        @JsonProperty("cpu_time_limit") Double cpuTimeLimit,
        @JsonProperty("memory_limit") Integer memoryLimit,
        @JsonProperty("callback_url") String callbackUrl
) {}
