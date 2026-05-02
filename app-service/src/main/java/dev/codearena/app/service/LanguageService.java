package dev.codearena.app.service;

import dev.codearena.app.dto.LanguageResponse;
import dev.codearena.app.grpc.ExecutionServiceClient;
import dev.codearena.proto.execution.v1.Language;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LanguageService {

    private final ExecutionServiceClient executionClient;

    public LanguageService(ExecutionServiceClient executionClient) {
        this.executionClient = executionClient;
    }

    public List<LanguageResponse> listActive() {
        return executionClient.listLanguages().stream()
            .filter(Language::getIsActive)
            .map(l -> new LanguageResponse(l.getId(), l.getName()))
            .toList();
    }
}
