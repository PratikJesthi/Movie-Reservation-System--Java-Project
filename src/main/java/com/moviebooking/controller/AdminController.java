package com.moviebooking.controller;

import com.moviebooking.dto.ScreenOption;
import com.moviebooking.dto.ShowCreateRequest;
import com.moviebooking.dto.ShowResponse;
import com.moviebooking.service.AdminCatalogService;
import com.moviebooking.service.SessionAuth;
import com.moviebooking.service.ShowService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminCatalogService catalogService;
    private final SessionAuth sessionAuth;
    private final ShowService showService;

    public AdminController(AdminCatalogService catalogService, SessionAuth sessionAuth,
                           ShowService showService) {
        this.catalogService = catalogService;
        this.sessionAuth = sessionAuth;
        this.showService = showService;
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

    @GetMapping("/shows")
    public List<ShowResponse> showsForMovie(@RequestParam Long movieId, HttpSession session) {
        sessionAuth.requireAdmin(session);
        return showService.findByMovie(movieId);
    }

    @DeleteMapping("/shows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShow(@PathVariable Long id, HttpSession session) {
        sessionAuth.requireAdmin(session);
        showService.deleteShow(id);
    }
}
