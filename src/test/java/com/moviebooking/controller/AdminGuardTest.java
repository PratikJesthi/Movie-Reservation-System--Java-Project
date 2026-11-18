package com.moviebooking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.moviebooking.dto.UserResponse;
import com.moviebooking.exception.ForbiddenException;
import com.moviebooking.service.SessionAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;

import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
class AdminGuardTest {

    /** Minimal exception handler so standaloneSetup handles ForbiddenException → 403 */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(ForbiddenException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        String forbidden() { return "forbidden"; }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SessionAuth sessionAuth = new SessionAuth();
        PageController controller = new PageController(sessionAuth);
        // InternalResourceViewResolver prevents circular-dispatch errors in standaloneSetup
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestExceptionHandler())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void getAdmin_anonymous_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void getAdmin_sessionWithNoAttribute_redirectsToAdminLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void getAdmin_loggedInAsUser_returnsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuth.USER_SESSION_KEY,
                new UserResponse(2L, "Bob", "bob@test.com", "USER"));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAdmin_loggedInAsAdmin_servesAdminView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuth.USER_SESSION_KEY,
                new UserResponse(1L, "Admin", "admin@test.com", "ADMIN"));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
    }

    @Test
    void getAdminLogin_alwaysServesThatPage() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-login"));
    }
}
