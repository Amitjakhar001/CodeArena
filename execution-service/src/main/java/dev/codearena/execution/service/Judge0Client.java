package dev.codearena.execution.service;

import dev.codearena.execution.dto.Judge0Language;
import dev.codearena.execution.dto.Judge0Submission;
import dev.codearena.execution.dto.Judge0SubmissionRequest;
import dev.codearena.execution.dto.Judge0SubmissionToken;
import dev.codearena.execution.exception.Judge0UnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Optional;

@Component
public class Judge0Client {

    private static final Logger log = LoggerFactory.getLogger(Judge0Client.class);

    private final WebClient client;

    public Judge0Client(WebClient judge0WebClient) {
        this.client = judge0WebClient;
    }

    /**
     * Submit a job to Judge0. wait=false → token returned immediately, result delivered via webhook.
     */
    public Judge0SubmissionToken submitAsync(Judge0SubmissionRequest req) {
        try {
            return client.post()
                    .uri(uri -> uri.path("/submissions").queryParam("wait", "false").build())
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Judge0SubmissionToken.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Judge0 submit failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new Judge0UnavailableException("Judge0 submit failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new Judge0UnavailableException("Judge0 submit failed", e);
        }
    }

    public Optional<Judge0Submission> getSubmission(String token) {
        try {
            Judge0Submission sub = client.get()
                    .uri("/submissions/{token}", token)
                    .retrieve()
                    .bodyToMono(Judge0Submission.class)
                    .block();
            return Optional.ofNullable(sub);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new Judge0UnavailableException("Judge0 get failed", e);
        }
    }

    public List<Judge0Language> listLanguages() {
        try {
            List<Judge0Language> langs = client.get()
                    .uri("/languages")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Judge0Language>>() {})
                    .block();
            return langs == null ? List.of() : langs;
        } catch (Exception e) {
            throw new Judge0UnavailableException("Judge0 languages fetch failed", e);
        }
    }
}
