package com.moviebooking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PosterSvgServiceTest {

    private PosterSvgService posterSvgService;

    @BeforeEach
    void setUp() {
        posterSvgService = new PosterSvgService();
    }

    @Test
    void render_isDeterministic_sameInputsProduceIdenticalSvg() {
        String svg1 = posterSvgService.render("Ghost Frequency", "Horror", "English", "A");
        String svg2 = posterSvgService.render("Ghost Frequency", "Horror", "English", "A");

        assertThat(svg1).isEqualTo(svg2);
    }

    @Test
    void render_containsStandardSvgHeaderAndDimensions() {
        String svg = posterSvgService.render("The Last Signal", "Science Fiction", "English", "UA");

        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        assertThat(svg).contains("viewBox=\"0 0 600 900\"");
        assertThat(svg).endsWith("</svg>");
    }

    @Test
    void render_escapesXmlCharactersInTitleAndMeta() {
        String svg = posterSvgService.render("Romeo & <Juliet> \"Special\"", "Romance", "Hindi", "UA");

        assertThat(svg).doesNotContain("<Juliet>");
        assertThat(svg).contains("&lt;Juliet&gt;");
        assertThat(svg).contains("Romeo &amp;");
        assertThat(svg).contains("&quot;Special&quot;");
    }

    @Test
    void render_rendersCertificateBadgeWhenPresent() {
        String withCert = posterSvgService.render("Kabaddi Kings", "Sports", "Hindi", "UA");
        assertThat(withCert).contains(">UA<");

        String withoutCert = posterSvgService.render("Kabaddi Kings", "Sports", "Hindi", null);
        assertThat(withoutCert).doesNotContain(">UA<");
    }

    @Test
    void render_allRequiredGenresProduceThemedArt() {
        // 1. Science Fiction
        String sciFi = posterSvgService.render("The Last Signal", "Science Fiction", "English", "UA");
        assertThat(sciFi).contains("Stars").contains("planetGrad");

        // 2. Horror
        String horror = posterSvgService.render("Ghost Frequency", "Horror", "English", "A");
        assertThat(horror).contains("moonGlow");

        // 3. Sports
        String sports = posterSvgService.render("Kabaddi Kings", "Sports", "Hindi", "UA");
        assertThat(sports).contains("#064e3b").contains("#f97316");

        // 4. Animation
        String anim = posterSvgService.render("Silver Tide", "Animation", "English", "U");
        assertThat(anim).contains("#fde2e4");

        // 5. Thriller
        String thriller = posterSvgService.render("Neon Lanes", "Thriller", "Tamil", "UA");
        assertThat(thriller).contains("Skyline");

        // 6. Drama
        String drama = posterSvgService.render("Chaya Nagar", "Drama", "Odia", "U");
        assertThat(drama).contains("sunsetSun");

        // 7. Action
        String action = posterSvgService.render("Iron Monsoon", "Action", "Telugu", "UA");
        assertThat(action).contains("#ea580c");

        // 8. Romance
        String romance = posterSvgService.render("Starlight Express", "Romance", "Hindi", "U");
        assertThat(romance).contains("#270722").contains("#f472b6");

        // 9. Adventure
        String adventure = posterSvgService.render("Midnight Runway", "Adventure", "English", "U");
        assertThat(adventure).contains("#047857").contains("#fbbf24");

        // 10. Comedy
        String comedy = posterSvgService.render("Laugh Out Loud", "Comedy", "Hindi", "U");
        assertThat(comedy).contains("#f59e0b");

        // 11. Default
        String unknown = posterSvgService.render("A Quiet Room", "Mystery/Noir", "English", null);
        assertThat(unknown).contains("Minimalist modern geometric art");
    }

    @Test
    void wrapTitle_handlesSingleAndMultiLine() {
        List<String> single = PosterSvgService.wrapTitle("Short Title", 20, 3);
        assertThat(single).containsExactly("Short Title");

        List<String> wrapped = PosterSvgService.wrapTitle("A Very Long Movie Title That Should Wrap Onto Multiple Lines", 18, 3);
        assertThat(wrapped.size()).isBetween(2, 3);
    }
}
