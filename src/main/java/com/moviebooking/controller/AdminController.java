package com.moviebooking.controller;

import com.moviebooking.dto.ScreenOption;
import com.moviebooking.dto.ShowCreateRequest;
import com.moviebooking.dto.ShowResponse;
import com.moviebooking.service.AdminCatalogService;
import com.moviebooking.service.SessionAuth;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminCatalogService catalogService;
    private final SessionAuth sessionAuth;

    public AdminController(AdminCatalogService catalogService, SessionAuth sessionAuth) {
        this.catalogService = catalogService;
        this.sessionAuth = sessionAuth;
    }

    @GetMapping("/screens")
    public List<ScreenOption> screens(HttpSession session) {
        sessionAuth.requireAdmin(session);
        return catalogService.screens();
    }

    @PostMapping("/shows")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowCreateRequest request,
                                                   HttpSession session) {
        sessionAuth.requireAdmin(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createShow(request));
    }
}
