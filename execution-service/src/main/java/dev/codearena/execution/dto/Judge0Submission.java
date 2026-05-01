package dev.codearena.execution.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Judge0's submission payload — used for both polling responses and webhook callbacks.
 * Numeric fields (time, memory) are wide enough to absorb the variants Judge0 returns.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0Submission(
        String token,
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compileOutput,
        String message,
        String time,
        Integer memory,
        @JsonProperty("exit_code") Integer exitCode,
        @JsonProperty("exit_signal") Integer exitSignal,
        Status status
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(int id, String description) {}
}
