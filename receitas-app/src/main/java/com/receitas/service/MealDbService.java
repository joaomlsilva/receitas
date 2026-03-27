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

@Service
public class MealDbService {

    private static final String MEALDB_URL = "https://www.themealdb.com/api/json/v1/1/search.php?s=";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Receita> pesquisar(String query) throws Exception {
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MEALDB_URL + encodedQuery))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        JsonNode meals = root.get("meals");

        List<Receita> resultados = new ArrayList<>();
        if (meals != null && meals.isArray()) {
            for (JsonNode meal : meals) {
                resultados.add(mapMealToReceita(meal));
            }
        }
        return resultados;
    }

    private Receita mapMealToReceita(JsonNode meal) {
        Receita receita = new Receita();
        receita.setId("mealdb-" + getText(meal, "idMeal"));
        receita.setTitulo(getText(meal, "strMeal"));
        receita.setOrigem(getText(meal, "strArea"));
        receita.setTipo(mapCategory(getText(meal, "strCategory")));
        receita.setNumeroPessoas(2);
        receita.setDificuldade("intermedio");

        String thumb = getText(meal, "strMealThumb");
        if (!thumb.isBlank()) receita.setFotoUrl(thumb);

        List<Ingrediente> ingredientes = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String ing = getText(meal, "strIngredient" + i).trim();
            String meas = getText(meal, "strMeasure" + i).trim();
            if (!ing.isBlank()) {
                ingredientes.add(new Ingrediente(ing, meas));
            }
        }
        receita.setIngredientes(ingredientes);

        String instructions = getText(meal, "strInstructions");
        List<String> steps = new ArrayList<>();
        for (String step : instructions.split("\r\n|\n|\r")) {
            if (!step.isBlank()) steps.add(step.trim());
        }
        receita.setPassosPreparacao(steps);

        return receita;
    }

    private String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : "";
    }

    private String mapCategory(String category) {
        return switch (category.toLowerCase()) {
            case "seafood" -> "marisco";
            case "beef", "chicken", "lamb", "pork", "goat" -> "carne";
            case "pasta" -> "massas";
            case "vegetarian", "vegan", "side" -> "saladas";
            case "starter" -> "petiscos";
            case "dessert", "breakfast" -> "doces";
            case "miscellaneous" -> "sopas";
            default -> "carne";
        };
    }
}
