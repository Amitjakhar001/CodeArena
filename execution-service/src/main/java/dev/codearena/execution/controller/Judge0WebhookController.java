package dev.codearena.execution.controller;

import dev.codearena.execution.dto.Judge0Submission;
import dev.codearena.execution.exception.ExecutionNotFoundException;
import dev.codearena.execution.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Judge0 callbacks. Judge0 issues PUT to the configured callback_url
 * with the completed submission body. POST is accepted too — some Judge0
 * deployments / proxies rewrite the verb.
 *
 * This endpoint is intentionally unauthenticated. In Phase 8 it'll only be
 * reachable from VM 2/3 via VPC-internal firewall rules.
 */
@RestController
@RequestMapping("/webhook/judge0")
public class Judge0WebhookController {

    private static final Logger log = LoggerFactory.getLogger(Judge0WebhookController.class);

    private final ExecutionService executions;

    public Judge0WebhookController(ExecutionService executions) {
        this.executions = executions;
    }

    @PutMapping("/{token}")
    public ResponseEntity<Void> putCallback(@PathVariable String token, @RequestBody Judge0Submission payload) {
        return apply(token, payload);
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> postCallback(@PathVariable String token, @RequestBody Judge0Submission payload) {
        return apply(token, payload);
    }

    private ResponseEntity<Void> apply(String token, Judge0Submission payload) {
        try {
            executions.applyJudge0Result(token, payload);
            return ResponseEntity.noContent().build();
        } catch (ExecutionNotFoundException e) {
            log.warn("Webhook for unknown token {}", token);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
