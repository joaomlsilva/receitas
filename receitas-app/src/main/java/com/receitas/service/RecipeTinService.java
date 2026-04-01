package com.receitas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches japan.recipetineats.com via the WordPress REST API and returns
 * a list of recipe previews (title, thumbnail, page URL).  The URL is then
 * passed to RecipeUrlService to import the full recipe.
 */
@Service
public class RecipeTinService {

    private static final String SEARCH_URL =
            "https://japan.recipetineats.com/wp-json/wp/v2/posts" +
            "?status=publish&per_page=12&_embed=1&search=";

    public record Result(String id, String titulo, String fotoUrl, String url) {}

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Result> search(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_URL + encoded))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; ReceitasApp/1.0)")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "RecipeTin Japan retornou HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<Result> results = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode post : root) {
                String id     = "rt-" + post.path("id").asText();
                String titulo = stripHtml(post.path("title").path("rendered").asText(""));
                String url    = post.path("link").asText("");
                String fotoUrl = extractFeaturedImage(post);

                if (!titulo.isBlank() && !url.isBlank()) {
                    results.add(new Result(id, titulo, fotoUrl, url));
                }
            }
        }
        return results;
    }

    private String extractFeaturedImage(JsonNode post) {
        JsonNode media = post.path("_embedded").path("wp:featuredmedia");
        if (media.isArray() && !media.isEmpty()) {
            // prefer a smaller size if available
            JsonNode sizes = media.get(0).path("media_details").path("sizes");
            for (String size : List.of("medium_large", "medium", "full")) {
                JsonNode sizeNode = sizes.path(size);
                if (!sizeNode.isMissingNode()) {
                    String src = sizeNode.path("source_url").asText("");
                    if (!src.isBlank()) return src;
                }
            }
            return media.get(0).path("source_url").asText("");
        }
        return "";
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replace("&amp;", "&")
                   .replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&#039;", "'").trim();
    }
}
