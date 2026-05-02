package dev.codearena.app.controller;

import dev.codearena.app.config.UserHeader;
import dev.codearena.app.dto.UserStatsResponse;
import dev.codearena.app.service.UserStatsService;
import org.bson.types.ObjectId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserStatsController {

    private final UserStatsService userStatsService;

    public UserStatsController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
    }

    @GetMapping("/me/stats")
    public UserStatsResponse getMyStats(
        @RequestHeader(value = UserHeader.NAME, required = false) String userIdHeader
    ) {
        ObjectId userId = UserHeader.requireUserId(userIdHeader);
        return UserStatsResponse.from(userStatsService.getOrEmpty(userId));
    }
}
