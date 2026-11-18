package com.moviebooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Generates original, deterministic, genre-themed SVG posters for movies.
 *
 * <p>Requirements:
 * <ul>
 *   <li>SVG viewBox 0 0 600 900 (standard 2:3 poster aspect ratio).</li>
 *   <li>Pure Java vector generation — zero external fonts, images, or network dependencies.</li>
 *   <li>Deterministic: stable hash from title & genre ensures identical outputs for identical inputs.</li>
 *   <li>Custom genre motifs: Science Fiction, Horror, Sports, Animation, Thriller,
 *       Drama, Action, Romance, Adventure, Comedy, and a sleek slate default.</li>
 *   <li>Title positioned at lower third, word-wrapped cleanly with protective text vignette.</li>
 *   <li>Certificate badge (U / UA / A) and genre/language metadata.</li>
 * </ul>
 */
@Service
public class PosterSvgService {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 900;

    public String render(String title, String genre, String language, String certificate) {
        String safeTitle = (title != null && !title.isBlank()) ? title.trim() : "Untitled";
        String safeGenre = (genre != null && !genre.isBlank()) ? genre.trim() : "Cinema";
        String safeLang = (language != null && !language.isBlank()) ? language.trim() : "";
        String safeCert = (certificate != null && !certificate.isBlank()) ? certificate.trim().toUpperCase() : null;

        long seed = (long) safeTitle.hashCode() ^ ((long) safeGenre.toLowerCase().hashCode() << 16);
        Random rnd = new Random(seed);

        StringBuilder svg = new StringBuilder(4096);
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"100%%\" height=\"100%%\">\n",
                WIDTH, HEIGHT));
        svg.append("<defs>\n");

        // Shared protective vignette for text contrast at the bottom third
        svg.append("  <linearGradient id=\"textVignette\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n");
        svg.append("    <stop offset=\"0%\" stop-color=\"#000000\" stop-opacity=\"0\"/>\n");
        svg.append("    <stop offset=\"40%\" stop-color=\"#030712\" stop-opacity=\"0.65\"/>\n");
        svg.append("    <stop offset=\"100%\" stop-color=\"#030712\" stop-opacity=\"0.96\"/>\n");
        svg.append("  </linearGradient>\n");

        // Genre-specific defs and artwork
        String normalizedGenre = safeGenre.toLowerCase();
        renderGenreDefsAndBackground(svg, normalizedGenre, rnd);
        svg.append("</defs>\n");

        // Base background rect
        svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"url(#genreBg)\"/>\n");

        // Genre-specific motif artwork
        renderGenreMotif(svg, normalizedGenre, rnd);

        // Bottom dark vignette to guarantee text legibility
        svg.append("  <rect x=\"0\" y=\"460\" width=\"600\" height=\"440\" fill=\"url(#textVignette)\"/>\n");

        // Certificate badge (top-right corner)
        if (safeCert != null) {
            renderCertificateBadge(svg, safeCert);
        }

        // Top-left subtle branding
        svg.append("  <g transform=\"translate(40, 50)\">\n");
        svg.append("    <circle cx=\"6\" cy=\"0\" r=\"3\" fill=\"#f59e0b\" opacity=\"0.9\"/>\n");
        svg.append("    <text x=\"16\" y=\"4\" fill=\"#e2e8f0\" font-family=\"system-ui, -apple-system, sans-serif\" ")
                .append("font-size=\"11\" font-weight=\"700\" letter-spacing=\"2\" opacity=\"0.85\">CHALCHITRA</text>\n");
        svg.append("  </g>\n");

        // Bottom text: Genre/Language meta, Title, and decor
        renderTypography(svg, safeTitle, safeGenre, safeLang);

        svg.append("</svg>");
        return svg.toString();
    }

    private void renderGenreDefsAndBackground(StringBuilder svg, String genre, Random rnd) {
        if (genre.contains("sci") || genre.contains("space")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0.3\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#03071e\"/>\n")
                    .append("    <stop offset=\"50%\" stop-color=\"#11092a\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#2e1065\"/>\n")
                    .append("  </linearGradient>\n");
            svg.append("  <radialGradient id=\"planetGrad\" cx=\"35%\" cy=\"35%\" r=\"65%\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#a855f7\"/>\n")
                    .append("    <stop offset=\"50%\" stop-color=\"#6b21a8\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#2e1065\"/>\n")
                    .append("  </radialGradient>\n");
        } else if (genre.contains("horror")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#050203\"/>\n")
                    .append("    <stop offset=\"55%\" stop-color=\"#160407\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#3f0708\"/>\n")
                    .append("  </linearGradient>\n");
            svg.append("  <radialGradient id=\"moonGlow\" cx=\"50%\" cy=\"50%\" r=\"50%\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#fff1f2\" stop-opacity=\"1\"/>\n")
                    .append("    <stop offset=\"75%\" stop-color=\"#fecdd3\" stop-opacity=\"0.85\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#e11d48\" stop-opacity=\"0\"/>\n")
                    .append("  </radialGradient>\n");
        } else if (genre.contains("sport")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#064e3b\"/>\n")
                    .append("    <stop offset=\"60%\" stop-color=\"#047857\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#065f46\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("anim")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#fde2e4\"/>\n")
                    .append("    <stop offset=\"35%\" stop-color=\"#e2ece9\"/>\n")
                    .append("    <stop offset=\"70%\" stop-color=\"#bee1e6\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#cddafd\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("thrill")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0.5\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#02161e\"/>\n")
                    .append("    <stop offset=\"50%\" stop-color=\"#083344\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#155e75\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("drama")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#1e0a24\"/>\n")
                    .append("    <stop offset=\"40%\" stop-color=\"#6b1548\"/>\n")
                    .append("    <stop offset=\"75%\" stop-color=\"#b93836\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#f97316\"/>\n")
                    .append("  </linearGradient>\n");
            svg.append("  <radialGradient id=\"sunsetSun\" cx=\"50%\" cy=\"50%\" r=\"50%\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#fef08a\"/>\n")
                    .append("    <stop offset=\"60%\" stop-color=\"#f97316\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#e11d48\" stop-opacity=\"0\"/>\n")
                    .append("  </radialGradient>\n");
        } else if (genre.contains("action")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#09090b\"/>\n")
                    .append("    <stop offset=\"60%\" stop-color=\"#1c1917\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#431407\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("romance")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0.4\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#270722\"/>\n")
                    .append("    <stop offset=\"45%\" stop-color=\"#701a75\"/>\n")
                    .append("    <stop offset=\"85%\" stop-color=\"#be185d\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#f472b6\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("advent")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#082f49\"/>\n")
                    .append("    <stop offset=\"45%\" stop-color=\"#0e7490\"/>\n")
                    .append("    <stop offset=\"85%\" stop-color=\"#047857\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#064e3b\"/>\n")
                    .append("  </linearGradient>\n");
        } else if (genre.contains("comedy")) {
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#fef08a\"/>\n")
                    .append("    <stop offset=\"50%\" stop-color=\"#f59e0b\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#f43f5e\"/>\n")
                    .append("  </linearGradient>\n");
        } else {
            // Default slate gradient with minimalist accents
            svg.append("  <linearGradient id=\"genreBg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
                    .append("    <stop offset=\"0%\" stop-color=\"#0f172a\"/>\n")
                    .append("    <stop offset=\"50%\" stop-color=\"#1e293b\"/>\n")
                    .append("    <stop offset=\"100%\" stop-color=\"#334155\"/>\n")
                    .append("  </linearGradient>\n");
        }
    }

    private void renderGenreMotif(StringBuilder svg, String genre, Random rnd) {
        if (genre.contains("sci") || genre.contains("space")) {
            renderScienceFiction(svg, rnd);
        } else if (genre.contains("horror")) {
            renderHorror(svg, rnd);
        } else if (genre.contains("sport")) {
            renderSports(svg, rnd);
        } else if (genre.contains("anim")) {
            renderAnimation(svg, rnd);
        } else if (genre.contains("thrill")) {
            renderThriller(svg, rnd);
        } else if (genre.contains("drama")) {
            renderDrama(svg, rnd);
        } else if (genre.contains("action")) {
            renderAction(svg, rnd);
        } else if (genre.contains("romance")) {
            renderRomance(svg, rnd);
        } else if (genre.contains("advent")) {
            renderAdventure(svg, rnd);
        } else if (genre.contains("comedy")) {
            renderComedy(svg, rnd);
        } else {
            renderDefault(svg, rnd);
        }
    }

    // 1. Science Fiction = deep navy/violet gradient, stars and a ringed planet
    private void renderScienceFiction(StringBuilder svg, Random rnd) {
        // Starfield (deterministic based on seed)
        svg.append("  <!-- Stars -->\n  <g fill=\"#ffffff\">\n");
        for (int i = 0; i < 60; i++) {
            int sx = rnd.nextInt(560) + 20;
            int sy = rnd.nextInt(520) + 20;
            float r = 0.8f + rnd.nextFloat() * 1.6f;
            float op = 0.25f + rnd.nextFloat() * 0.75f;
            svg.append(String.format("    <circle cx=\"%d\" cy=\"%d\" r=\"%.1f\" opacity=\"%.2f\"/>\n", sx, sy, r, op));
        }
        svg.append("  </g>\n");

        int px = 300 + (rnd.nextInt(60) - 30);
        int py = 280 + (rnd.nextInt(40) - 20);
        int pr = 105;

        // Distant small glowing moon
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"16\" fill=\"#38bdf8\" opacity=\"0.75\"/>\n",
                px - 160, py - 90));

        // Back half of planet ring
        svg.append(String.format(
                "  <ellipse cx=\"%d\" cy=\"%d\" rx=\"210\" ry=\"42\" fill=\"none\" stroke=\"#c084fc\" stroke-width=\"9\" "
                        + "opacity=\"0.4\" transform=\"rotate(-18 %d %d)\"/>\n",
                px, py, px, py));

        // Planet body
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"url(#planetGrad)\"/>\n", px, py, pr));

        // Front half of planet ring
        svg.append(String.format(
                "  <path d=\"M %d %d A 210 42 0 0 0 %d %d\" fill=\"none\" stroke=\"#e9d5ff\" stroke-width=\"10\" "
                        + "opacity=\"0.85\" transform=\"rotate(-18 %d %d)\"/>\n",
                px - 210, py, px + 210, py, px, py));
    }

    // 2. Horror = near-black with blood-red, big pale moon and jagged silhouettes
    private void renderHorror(StringBuilder svg, Random rnd) {
        int mx = 300 + (rnd.nextInt(40) - 20);
        int my = 260 + (rnd.nextInt(30) - 15);

        // Blood moon glow
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"180\" fill=\"url(#moonGlow)\"/>\n", mx, my));
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"115\" fill=\"#fff1f2\"/>\n", mx, my));

        // Eerie mist bands across moon
        svg.append(String.format(
                "  <path d=\"M %d %d Q %d %d %d %d\" stroke=\"#160407\" stroke-width=\"14\" fill=\"none\" opacity=\"0.55\"/>\n",
                mx - 140, my - 20, mx, my - 45, mx + 140, my - 15));
        svg.append(String.format(
                "  <path d=\"M %d %d Q %d %d %d %d\" stroke=\"#160407\" stroke-width=\"10\" fill=\"none\" opacity=\"0.45\"/>\n",
                mx - 130, my + 30, mx, my + 15, mx + 130, my + 35));

        // Jagged mountain and dead tree silhouettes
        svg.append("  <polygon points=\"0,620 70,440 150,560 220,400 310,540 400,380 490,530 560,420 600,580 600,900 0,900\" "
                + "fill=\"#090204\"/>\n");
        svg.append("  <polygon points=\"0,680 110,480 200,600 280,450 380,590 480,470 600,640 600,900 0,900\" "
                + "fill=\"#020001\" opacity=\"0.9\"/>\n");
    }

    // 3. Sports = bold green/orange, diagonal stripes and a ball circle
    private void renderSports(StringBuilder svg, Random rnd) {
        // High-energy diagonal stripes
        svg.append("  <g fill=\"#f97316\" opacity=\"0.85\">\n")
                .append("    <polygon points=\"-50,220 280,-80 340,-80 10,220\"/>\n")
                .append("    <polygon points=\"-30,360 380,-50 460,-50 50,360\" fill=\"#ea580c\"/>\n")
                .append("    <polygon points=\"20,500 500,20 560,20 80,500\" fill=\"#fb923c\" opacity=\"0.75\"/>\n")
                .append("  </g>\n");

        int bx = 380;
        int by = 300;
        // Glowing sports ball circle
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"110\" fill=\"#ffedd5\" stroke=\"#f97316\" stroke-width=\"12\"/>\n", bx, by));
        svg.append(String.format("  <path d=\"M %d %d A 110 110 0 0 1 %d %d\" stroke=\"#ea580c\" stroke-width=\"8\" fill=\"none\"/>\n",
                bx - 85, by - 65, bx + 85, by + 65));
        svg.append(String.format("  <path d=\"M %d %d A 110 110 0 0 0 %d %d\" stroke=\"#ea580c\" stroke-width=\"8\" fill=\"none\"/>\n",
                bx + 85, by - 65, bx - 85, by + 65));

        // Speed lines
        svg.append("  <line x1=\"80\" y1=\"260\" x2=\"240\" y2=\"260\" stroke=\"#ffffff\" stroke-width=\"4\" opacity=\"0.6\" stroke-dasharray=\"16 8\"/>\n");
        svg.append("  <line x1=\"60\" y1=\"300\" x2=\"210\" y2=\"300\" stroke=\"#ffffff\" stroke-width=\"4\" opacity=\"0.6\" stroke-dasharray=\"24 8\"/>\n");
    }

    // 4. Animation = bright pastel gradient, soft overlapping circles and waves
    private void renderAnimation(StringBuilder svg, Random rnd) {
        // Overlapping pastel bubbles/orbs
        String[] colors = {"#f472b6", "#a78bfa", "#38bdf8", "#fbbf24", "#34d399"};
        for (int i = 0; i < 9; i++) {
            int cx = rnd.nextInt(520) + 40;
            int cy = rnd.nextInt(380) + 60;
            int r = 35 + rnd.nextInt(75);
            String col = colors[i % colors.length];
            float op = 0.25f + rnd.nextFloat() * 0.35f;
            svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" opacity=\"%.2f\"/>\n", cx, cy, r, col, op));
        }

        // Soft whimsical waves
        svg.append("  <path d=\"M 0,380 Q 150,330 300,380 T 600,370 L 600,900 L 0,900 Z\" fill=\"#fbcfe8\" opacity=\"0.45\"/>\n");
        svg.append("  <path d=\"M 0,440 Q 180,480 360,430 T 600,450 L 600,900 L 0,900 Z\" fill=\"#bae6fd\" opacity=\"0.5\"/>\n");
        svg.append("  <path d=\"M 0,510 Q 140,460 300,500 T 600,490 L 600,900 L 0,900 Z\" fill=\"#e9d5ff\" opacity=\"0.6\"/>\n");
    }

    // 5. Thriller = dark teal/steel, city skyline silhouette and thin grid lines
    private void renderThriller(StringBuilder svg, Random rnd) {
        // Perspective grid lines
        svg.append("  <g stroke=\"#06b6d4\" stroke-width=\"1\" opacity=\"0.22\">\n");
        for (int y = 140; y <= 480; y += 45) {
            svg.append(String.format("    <line x1=\"0\" y1=\"%d\" x2=\"600\" y2=\"%d\"/>\n", y, y));
        }
        for (int x = 60; x <= 540; x += 60) {
            svg.append(String.format("    <line x1=\"%d\" y1=\"100\" x2=\"%d\" y2=\"540\"/>\n", x, x));
        }
        svg.append("  </g>\n");

        // City skyline blocks
        svg.append("  <!-- Skyline -->\n  <g fill=\"#021118\">\n")
                .append("    <rect x=\"30\" y=\"320\" width=\"65\" height=\"320\"/>\n")
                .append("    <rect x=\"105\" y=\"240\" width=\"80\" height=\"400\"/>\n")
                .append("    <rect x=\"195\" y=\"360\" width=\"55\" height=\"280\"/>\n")
                .append("    <rect x=\"260\" y=\"190\" width=\"95\" height=\"450\"/>\n")
                .append("    <polygon points=\"295,190 307,130 319,190\"/>\n") // radio tower
                .append("    <rect x=\"365\" y=\"290\" width=\"75\" height=\"350\"/>\n")
                .append("    <rect x=\"450\" y=\"230\" width=\"60\" height=\"410\"/>\n")
                .append("    <rect x=\"520\" y=\"340\" width=\"60\" height=\"300\"/>\n")
                .append("  </g>\n");

        // Scattered lit windows
        svg.append("  <g fill=\"#38bdf8\" opacity=\"0.85\">\n")
                .append("    <rect x=\"125\" y=\"270\" width=\"6\" height=\"9\"/>\n")
                .append("    <rect x=\"145\" y=\"310\" width=\"6\" height=\"9\"/>\n")
                .append("    <rect x=\"285\" y=\"220\" width=\"8\" height=\"12\" fill=\"#fef08a\"/>\n")
                .append("    <rect x=\"315\" y=\"250\" width=\"8\" height=\"12\"/>\n")
                .append("    <rect x=\"385\" y=\"320\" width=\"6\" height=\"8\"/>\n")
                .append("    <rect x=\"470\" y=\"260\" width=\"7\" height=\"10\" fill=\"#fef08a\"/>\n")
                .append("  </g>\n");

        // Searchlight beam
        svg.append("  <polygon points=\"307,130 0,0 80,0\" fill=\"#67e8f9\" opacity=\"0.12\"/>\n");
    }

    // 6. Drama = warm sunset gradient, low horizon and a lone figure-free landscape
    private void renderDrama(StringBuilder svg, Random rnd) {
        // Radiant sunset sun on horizon
        int sunY = 460;
        svg.append(String.format("  <circle cx=\"300\" cy=\"%d\" r=\"140\" fill=\"url(#sunsetSun)\"/>\n", sunY));
        svg.append(String.format("  <circle cx=\"300\" cy=\"%d\" r=\"70\" fill=\"#fef08a\" opacity=\"0.95\"/>\n", sunY));

        // Atmospheric glowing horizon layers
        svg.append("  <ellipse cx=\"300\" cy=\"490\" rx=\"350\" ry=\"35\" fill=\"#f97316\" opacity=\"0.6\"/>\n");

        // Rolling landscape hills silhouette (figure-free)
        svg.append("  <path d=\"M 0,510 Q 160,470 320,520 T 600,480 L 600,900 L 0,900 Z\" fill=\"#1e0a24\" opacity=\"0.85\"/>\n");
        svg.append("  <path d=\"M 0,550 Q 220,510 420,560 T 600,530 L 600,900 L 0,900 Z\" fill=\"#0d020f\"/>\n");
    }

    // 7. Action = black/orange, diagonal slashes and a burst
    private void renderAction(StringBuilder svg, Random rnd) {
        // Dramatic energy burst polygon
        svg.append("  <g transform=\"translate(300, 310)\">\n")
                .append("    <polygon points=\"0,-160 35,-45 155,-70 65,15 170,85 45,75 55,185 -25,85 -115,145 -65,35 -165,-25 -45,-45\" "
                        + "fill=\"#ea580c\" opacity=\"0.85\"/>\n")
                .append("    <circle cx=\"0\" cy=\"0\" r=\"65\" fill=\"#f97316\"/>\n")
                .append("    <circle cx=\"0\" cy=\"0\" r=\"35\" fill=\"#fef08a\"/>\n")
                .append("  </g>\n");

        // Sharp diagonal impact slashes
        svg.append("  <polygon points=\"-40,180 580,480 620,440 0,140\" fill=\"#f97316\" opacity=\"0.65\"/>\n");
        svg.append("  <polygon points=\"-20,420 540,100 580,130 20,450\" fill=\"#ffffff\" opacity=\"0.4\"/>\n");
        svg.append("  <polygon points=\"120,60 520,520 480,550 80,90\" fill=\"#c2410c\" opacity=\"0.55\"/>\n");
    }

    // 8. Romance = rose/purple gradient with soft circles
    private void renderRomance(StringBuilder svg, Random rnd) {
        // Layered romantic bokeh orbs
        String[] colors = {"#f472b6", "#fda4af", "#e879f9", "#c084fc", "#ffffff"};
        for (int i = 0; i < 14; i++) {
            int bx = rnd.nextInt(520) + 40;
            int by = rnd.nextInt(440) + 60;
            int r = 25 + rnd.nextInt(75);
            String col = colors[i % colors.length];
            float op = 0.18f + rnd.nextFloat() * 0.35f;
            svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" opacity=\"%.2f\"/>\n", bx, by, r, col, op));
        }

        // Elegant curved ribbon glow
        svg.append("  <path d=\"M 40,540 C 180,320 420,380 560,200\" stroke=\"#fbcfe8\" stroke-width=\"4\" fill=\"none\" opacity=\"0.4\"/>\n");
        svg.append("  <path d=\"M 60,560 C 200,340 440,400 580,220\" stroke=\"#f472b6\" stroke-width=\"8\" fill=\"none\" opacity=\"0.25\"/>\n");
    }

    // 9. Adventure = green/blue, layered mountains and a sun
    private void renderAdventure(StringBuilder svg, Random rnd) {
        // Golden sun rising
        svg.append("  <circle cx=\"300\" cy=\"300\" r=\"115\" fill=\"#fbbf24\" opacity=\"0.9\"/>\n");
        svg.append("  <circle cx=\"300\" cy=\"300\" r=\"140\" fill=\"#fef08a\" opacity=\"0.35\"/>\n");

        // Back mountain ridge (lighter teal/blue)
        svg.append("  <polygon points=\"0,530 110,360 250,470 380,320 510,440 600,340 600,900 0,900\" fill=\"#0e7490\" opacity=\"0.7\"/>\n");
        // Middle mountain ridge (forest green)
        svg.append("  <polygon points=\"0,580 160,390 290,510 440,370 570,490 600,470 600,900 0,900\" fill=\"#047857\" opacity=\"0.85\"/>\n");
        // Foreground mountain ridge (deep evergreen)
        svg.append("  <polygon points=\"0,640 120,490 220,590 350,450 490,570 600,500 600,900 0,900\" fill=\"#064e3b\"/>\n");
    }

    // 10. Comedy = yellow/coral, playful circles
    private void renderComedy(StringBuilder svg, Random rnd) {
        String[] colors = {"#f43f5e", "#fb923c", "#38bdf8", "#a855f7", "#ffffff"};
        for (int i = 0; i < 20; i++) {
            int cx = rnd.nextInt(540) + 30;
            int cy = rnd.nextInt(460) + 50;
            int r = 16 + rnd.nextInt(48);
            String col = colors[i % colors.length];
            float op = 0.4f + rnd.nextFloat() * 0.5f;
            svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" opacity=\"%.2f\"/>\n", cx, cy, r, col, op));
            if (rnd.nextBoolean()) {
                svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"none\" stroke=\"%s\" stroke-width=\"4\" opacity=\"0.6\"/>\n",
                        cx, cy, r + 10, col));
            }
        }
    }

    // 11. Default = slate gradient with simple shapes
    private void renderDefault(StringBuilder svg, Random rnd) {
        svg.append("  <!-- Minimalist modern geometric art -->\n  <g fill=\"none\" stroke=\"#94a3b8\" stroke-width=\"2\" opacity=\"0.35\">\n")
                .append("    <circle cx=\"300\" cy=\"300\" r=\"150\"/>\n")
                .append("    <circle cx=\"300\" cy=\"300\" r=\"90\" stroke=\"#38bdf8\" stroke-width=\"3\" opacity=\"0.6\"/>\n")
                .append("    <rect x=\"170\" y=\"170\" width=\"260\" height=\"260\" transform=\"rotate(45 300 300)\"/>\n")
                .append("    <line x1=\"60\" y1=\"300\" x2=\"540\" y2=\"300\" stroke-dasharray=\"8 8\"/>\n")
                .append("  </g>\n");
        svg.append("  <circle cx=\"300\" cy=\"300\" r=\"40\" fill=\"#38bdf8\" opacity=\"0.85\"/>\n");
    }

    private void renderCertificateBadge(StringBuilder svg, String cert) {
        String escapedCert = escapeXml(cert);
        int badgeW = 46 + (escapedCert.length() > 2 ? (escapedCert.length() - 2) * 12 : 0);
        int x = WIDTH - 40 - badgeW;
        int y = 40;
        svg.append(String.format("  <g transform=\"translate(%d, %d)\">\n", x, y));
        svg.append(String.format("    <rect width=\"%d\" height=\"28\" rx=\"6\" fill=\"#0f172a\" fill-opacity=\"0.75\" "
                + "stroke=\"#ffffff\" stroke-opacity=\"0.6\" stroke-width=\"1.5\"/>\n", badgeW));
        svg.append(String.format("    <text x=\"%d\" y=\"19\" fill=\"#ffffff\" font-family=\"system-ui, -apple-system, sans-serif\" "
                + "font-size=\"13\" font-weight=\"800\" text-anchor=\"middle\">%s</text>\n", badgeW / 2, escapedCert));
        svg.append("  </g>\n");
    }

    private void renderTypography(StringBuilder svg, String title, String genre, String language) {
        svg.append("  <g transform=\"translate(45, 670)\">\n");

        // Genre & Language eyebrow (uppercase, spaced)
        StringBuilder meta = new StringBuilder();
        meta.append(genre.toUpperCase());
        if (language != null && !language.isBlank()) {
            meta.append("  •  ").append(language.toUpperCase());
        }
        svg.append(String.format("    <text x=\"0\" y=\"0\" fill=\"#f59e0b\" font-family=\"system-ui, -apple-system, sans-serif\" "
                + "font-size=\"13\" font-weight=\"700\" letter-spacing=\"2.5\">%s</text>\n", escapeXml(meta.toString())));

        // Title word wrapping (max 3 lines)
        List<String> lines = wrapTitle(title, 18, 3);
        int fontSize = lines.size() > 2 ? 36 : (lines.size() == 2 ? 42 : 46);
        int lineHeight = fontSize + 8;
        int startY = 44;

        svg.append(String.format("    <text x=\"0\" y=\"%d\" fill=\"#ffffff\" font-family=\"system-ui, -apple-system, sans-serif\" "
                + "font-size=\"%d\" font-weight=\"800\" letter-spacing=\"-0.5\">\n", startY, fontSize));
        for (int i = 0; i < lines.size(); i++) {
            int dy = i == 0 ? 0 : lineHeight;
            svg.append(String.format("      <tspan x=\"0\" dy=\"%d\">%s</tspan>\n", dy, escapeXml(lines.get(i))));
        }
        svg.append("    </text>\n");

        // Subtle accent line under title
        int lineY = startY + (lines.size() - 1) * lineHeight + 28;
        svg.append(String.format("    <line x1=\"0\" y1=\"%d\" x2=\"60\" y2=\"%d\" stroke=\"#f59e0b\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n", lineY, lineY));

        svg.append("  </g>\n");
    }

    /**
     * Splits a title into lines of at most maxCharsPerLine, up to maxLines.
     */
    static List<String> wrapTitle(String text, int maxCharsPerLine, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("Untitled");
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxCharsPerLine) {
                current.append(" ").append(word);
            } else {
                lines.add(current.toString());
                if (lines.size() == maxLines - 1) {
                    // On the last allowed line, append the rest
                    StringBuilder remainder = new StringBuilder(word);
                    current = remainder;
                } else if (lines.size() >= maxLines) {
                    break;
                } else {
                    current = new StringBuilder(word);
                }
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }

        return lines;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
