package dev.codearena.app.controller;

import dev.codearena.app.config.UserHeader;
import dev.codearena.app.domain.Submission;
import dev.codearena.app.dto.CreateSubmissionRequest;
import dev.codearena.app.dto.CreateSubmissionResponse;
import dev.codearena.app.dto.PageResponse;
import dev.codearena.app.dto.SubmissionDetailResponse;
import dev.codearena.app.dto.SubmissionSummaryResponse;
import dev.codearena.app.exception.SubmissionNotFoundException;
import dev.codearena.app.service.SubmissionService;
import dev.codearena.app.service.SubmissionService.ResolvedSubmission;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<CreateSubmissionResponse> create(
        @RequestHeader(value = UserHeader.NAME, required = false) String userIdHeader,
        @Valid @RequestBody CreateSubmissionRequest body
    ) {
        ObjectId userId = UserHeader.requireUserId(userIdHeader);
        Submission submission = submissionService.create(userId, body);
        return ResponseEntity.accepted().body(
            new CreateSubmissionResponse(submission.getId().toHexString(), submission.getLatestStatus()));
    }

    @GetMapping("/{id}")
    public SubmissionDetailResponse getOne(
        @RequestHeader(value = UserHeader.NAME, required = false) String userIdHeader,
        @PathVariable String id
    ) {
        ObjectId userId = UserHeader.requireUserId(userIdHeader);
        ObjectId submissionId;
        try {
            submissionId = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            throw new SubmissionNotFoundException("Invalid submission id: " + id);
        }
        ResolvedSubmission resolved = submissionService.getAndRefresh(userId, submissionId);
        return SubmissionDetailResponse.from(resolved.submission(), resolved.executions());
    }

    @GetMapping
    public PageResponse<SubmissionSummaryResponse> list(
        @RequestHeader(value = UserHeader.NAME, required = false) String userIdHeader,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String problemId
    ) {
        ObjectId userId = UserHeader.requireUserId(userIdHeader);
        ObjectId problemObjectId = null;
        if (problemId != null && !problemId.isBlank()) {
            try {
                problemObjectId = new ObjectId(problemId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid problemId: " + problemId);
            }
        }
        Page<Submission> result = submissionService.listForUser(
            userId, problemObjectId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, SubmissionSummaryResponse::from);
    }
}
