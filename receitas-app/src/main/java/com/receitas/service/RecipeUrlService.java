package com.receitas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.receitas.model.Ingrediente;
import com.receitas.model.Receita;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports a recipe from any page that embeds schema.org Recipe JSON-LD,
 * including japan.recipetineats.com and other modern recipe blogs.
 */
@Service
public class RecipeUrlService {

    private static final Pattern JSON_LD_PATTERN =
            Pattern.compile("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Receita importFromUrl(String url) throws Exception {
        URI uri = URI.create(url);
        if (!uri.getScheme().equals("https") && !uri.getScheme().equals("http")) {
            throw new IllegalArgumentException("URL inválido.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; ReceitasApp/1.0)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("A página retornou o código HTTP " + response.statusCode());
        }

        JsonNode recipeNode = extractRecipeJsonLd(response.body());
        if (recipeNode == null) {
            throw new IllegalStateException("Nenhum dado de receita (schema.org) encontrado nesta página.");
        }

        return mapJsonLdToReceita(recipeNode, uri.getHost());
    }

    private JsonNode extractRecipeJsonLd(String html) throws Exception {
        Matcher m = JSON_LD_PATTERN.matcher(html);
        while (m.find()) {
            String json = m.group(1).trim();
            try {
                JsonNode node = mapper.readTree(json);
                // JSON-LD may be a single object or an array / @graph
                JsonNode recipe = findRecipeNode(node);
                if (recipe != null) return recipe;
            } catch (Exception ignored) {
                // malformed JSON-LD block – skip
            }
        }
        return null;
    }

    private JsonNode findRecipeNode(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode r = findRecipeNode(child);
                if (r != null) return r;
            }
        } else if (node.isObject()) {
            String type = getText(node, "@type");
            if ("Recipe".equalsIgnoreCase(type)) return node;

            // Handle @graph wrapper
            JsonNode graph = node.get("@graph");
            if (graph != null && graph.isArray()) {
                for (JsonNode child : graph) {
                    JsonNode r = findRecipeNode(child);
                    if (r != null) return r;
                }
            }
        }
        return null;
    }

    private Receita mapJsonLdToReceita(JsonNode node, String host) {
        Receita receita = new Receita();
        receita.setTitulo(getText(node, "name"));
        receita.setOrigem(host);
        receita.setTipo("internacional");
        receita.setDificuldade("intermedio");
        receita.setNumeroPessoas(parseServings(getText(node, "recipeYield")));

        // Photo
        JsonNode imageNode = node.get("image");
        if (imageNode != null) {
            String imageUrl = null;
            if (imageNode.isTextual()) {
                imageUrl = imageNode.asText();
            } else if (imageNode.isArray() && imageNode.size() > 0) {
                JsonNode first = imageNode.get(0);
                imageUrl = first.isTextual() ? first.asText() : getText(first, "url");
            } else if (imageNode.isObject()) {
                imageUrl = getText(imageNode, "url");
            }
            if (imageUrl != null && !imageUrl.isBlank()) receita.setFotoUrl(imageUrl);
        }

        // Ingredients
        List<Ingrediente> ingredientes = new ArrayList<>();
        JsonNode ings = node.get("recipeIngredient");
        if (ings != null && ings.isArray()) {
            for (JsonNode ing : ings) {
                String raw = ing.asText().trim();
                if (!raw.isBlank()) {
                    // Split off quantity (first token if it starts with digit or fraction)
                    String[] parts = raw.split("\\s+", 2);
                    if (parts.length == 2 && parts[0].matches("[\\d¼½¾⅓⅔⅛⅜⅝⅞./]+")) {
                        ingredientes.add(new Ingrediente(parts[1], parts[0]));
                    } else {
                        ingredientes.add(new Ingrediente(raw, ""));
                    }
                }
            }
        }
        receita.setIngredientes(ingredientes);

        // Steps
        List<String> passos = new ArrayList<>();
        JsonNode instructions = node.get("recipeInstructions");
        if (instructions != null) {
            if (instructions.isArray()) {
                for (JsonNode step : instructions) {
                    if (step.isTextual()) {
                        addStep(passos, step.asText());
                    } else {
                        // HowToStep / HowToSection
                        String type = getText(step, "@type");
                        if ("HowToSection".equalsIgnoreCase(type)) {
                            JsonNode items = step.get("itemListElement");
                            if (items != null && items.isArray()) {
                                for (JsonNode item : items) {
                                    addStep(passos, getText(item, "text"));
                                }
                            }
                        } else {
                            addStep(passos, getText(step, "text"));
                        }
                    }
                }
            } else if (instructions.isTextual()) {
                for (String line : instructions.asText().split("\r\n|\n|\r")) {
                    addStep(passos, line);
                }
            }
        }
        receita.setPassosPreparacao(passos);

        return receita;
    }

    private void addStep(List<String> steps, String text) {
        if (text != null && !text.isBlank()) {
            // Strip basic HTML tags that sometimes appear inside text fields
            steps.add(text.replaceAll("<[^>]+>", "").trim());
        }
    }

    private int parseServings(String yield) {
        if (yield == null || yield.isBlank()) return 2;
        Matcher m = Pattern.compile("\\d+").matcher(yield);
        return m.find() ? Integer.parseInt(m.group()) : 2;
    }

    private String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : "";
    }
}
