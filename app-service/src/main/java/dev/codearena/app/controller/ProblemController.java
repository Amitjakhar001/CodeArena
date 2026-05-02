package dev.codearena.app.controller;

import dev.codearena.app.dto.ProblemDetailResponse;
import dev.codearena.app.dto.ProblemSummaryResponse;
import dev.codearena.app.service.ProblemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public List<ProblemSummaryResponse> listProblems() {
        return problemService.listAll().stream().map(ProblemSummaryResponse::from).toList();
    }

    @GetMapping("/{slug}")
    public ProblemDetailResponse getProblem(@PathVariable String slug) {
        return ProblemDetailResponse.from(problemService.getBySlug(slug));
    }
}
