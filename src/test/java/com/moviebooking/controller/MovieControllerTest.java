package com.moviebooking.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.service.MovieService;
import com.moviebooking.service.SessionAuth;
import com.moviebooking.service.ShowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MovieService movieService;

    @Mock
    private ShowService showService;

    @Mock
    private SessionAuth sessionAuth;

    @InjectMocks
    private MovieController movieController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(movieController).build();
    }

    @Test
    void getPoster_returnsSvgWithCorrectContentTypeAndCacheHeader() throws Exception {
        String mockSvg = "<svg viewBox=\"0 0 600 900\"><text>Test</text></svg>";
        when(movieService.getPosterSvg(4L)).thenReturn(mockSvg);

        mockMvc.perform(get("/api/movies/4/poster"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"))
                .andExpect(content().string(mockSvg));
    }
}
