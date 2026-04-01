package com.receitas.controller;

import com.receitas.model.Receita;
import com.receitas.service.MealDbService;
import com.receitas.service.ReceitaService;
import com.receitas.service.RecipeUrlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ReceitaController {

    private static final Set<String> TIPOS_VALIDOS = Set.of(
        "doces", "carne", "peixe", "marisco", "massas",
        "pizzas", "sopas", "saladas", "petiscos", "molhos", "temperos", "bebidas", "internacional"
    );
    private static final Set<String> SUBTIPOS_VALIDOS = Set.of("com alcool", "sem alcool");
    private static final Set<String> DIFICULDADES_VALIDAS = Set.of("principiante", "intermedio", "avançado");

    private final ReceitaService receitaService;
    private final MealDbService mealDbService;
    private final RecipeUrlService recipeUrlService;

    public ReceitaController(ReceitaService receitaService, MealDbService mealDbService, RecipeUrlService recipeUrlService) {
        this.receitaService = receitaService;
        this.mealDbService = mealDbService;
        this.recipeUrlService = recipeUrlService;
    }

    @GetMapping("/receitas")
    public ResponseEntity<List<Receita>> listarReceitas() {
        return ResponseEntity.ok(receitaService.listarReceitas());
    }

    @GetMapping("/receitas/{id}")
    public ResponseEntity<Receita> obterReceita(@PathVariable String id) {
        return receitaService.obterReceita(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/receitas/guardar-api")
    public ResponseEntity<?> guardarDeApi(@RequestBody Receita receita) {
        try {
            receita.setId(null);
            Receita criada = receitaService.criarReceita(receita);
            return ResponseEntity.ok(criada);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao guardar receita.");
        }
    }

    @PostMapping("/receitas")
    public ResponseEntity<?> criarReceita(@RequestBody Receita receita) {
        String erro = validar(receita);
        if (erro != null) return ResponseEntity.badRequest().body(erro);
        try {
            Receita criada = receitaService.criarReceita(receita);
            return ResponseEntity.ok(criada);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao guardar receita.");
        }
    }

    @DeleteMapping("/receitas/{id}")
    public ResponseEntity<Void> eliminarReceita(@PathVariable String id) {
        try {
            boolean removed = receitaService.eliminarReceita(id);
            return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/import-url")
    public ResponseEntity<?> importFromUrl(@RequestParam String url) {
        if (url == null || url.isBlank() || url.length() > 2000) {
            return ResponseEntity.badRequest().body("URL inválido.");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ResponseEntity.badRequest().body("Apenas URLs http/https são suportados.");
        }
        try {
            Receita receita = recipeUrlService.importFromUrl(url.trim());
            return ResponseEntity.ok(receita);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao importar receita da URL.");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> pesquisar(@RequestParam String q) {
        if (q == null || q.isBlank() || q.length() > 100) {
            return ResponseEntity.badRequest().body("Parâmetro de pesquisa inválido.");
        }
        try {
            List<Receita> resultados = mealDbService.pesquisar(q.trim());
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao pesquisar na API externa.");
        }
    }

    private String validar(Receita r) {
        if (r.getTitulo() == null || r.getTitulo().isBlank())
            return "O título é obrigatório.";
        if (r.getTitulo().length() > 200)
            return "O título não pode ter mais de 200 caracteres.";
        if (r.getOrigem() == null || r.getOrigem().isBlank())
            return "A origem é obrigatória.";
        if (r.getTipo() == null || !TIPOS_VALIDOS.contains(r.getTipo()))
            return "Tipo de receita inválido.";
        if ("bebidas".equals(r.getTipo()) &&
                (r.getSubtipo() == null || !SUBTIPOS_VALIDOS.contains(r.getSubtipo())))
            return "Subtipo de bebida inválido (use 'com alcool' ou 'sem alcool').";
        if (r.getDificuldade() == null || !DIFICULDADES_VALIDAS.contains(r.getDificuldade()))
            return "Nível de dificuldade inválido.";
        if (r.getNumeroPessoas() <= 0 || r.getNumeroPessoas() > 100)
            return "Número de pessoas deve ser entre 1 e 100.";
        if (r.getIngredientes() == null || r.getIngredientes().isEmpty())
            return "A lista de ingredientes é obrigatória.";
        if (r.getPassosPreparacao() == null || r.getPassosPreparacao().isEmpty())
            return "Os passos de preparação são obrigatórios.";
        return null;
    }
}
