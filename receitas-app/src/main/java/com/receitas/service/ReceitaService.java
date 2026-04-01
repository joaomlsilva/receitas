package com.receitas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.receitas.model.Ingrediente;
import com.receitas.model.Receita;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ReceitaService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${receitas.data.path:./data}")
    private String dataPath;

    private Path dataDir;
    private List<Receita> receitas = new ArrayList<>();

    @PostConstruct
    public void init() throws IOException {
        dataDir = Paths.get(dataPath).toAbsolutePath().normalize();
        Files.createDirectories(dataDir);
        receitas = carregarDeFicheiro();
        if (receitas.isEmpty()) {
            receitas = criarReceitasExemplo();
            for (Receita r : receitas) {
                salvarReceita(r);
            }
        }
    }

    public List<Receita> listarReceitas() {
        return Collections.unmodifiableList(receitas);
    }

    public Optional<Receita> obterReceita(String id) {
        if (!isValidUUID(id)) return Optional.empty();
        return receitas.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public Receita criarReceita(Receita receita) throws IOException {
        receita.setId(UUID.randomUUID().toString());
        receitas.add(receita);
        salvarReceita(receita);
        return receita;
    }

    public Optional<Receita> atualizarReceita(String id, Receita updated) throws IOException {
        if (!isValidUUID(id)) return Optional.empty();
        Optional<Receita> existing = receitas.stream().filter(r -> r.getId().equals(id)).findFirst();
        if (existing.isEmpty()) return Optional.empty();
        Receita r = existing.get();
        r.setTitulo(updated.getTitulo());
        r.setOrigem(updated.getOrigem());
        r.setTipo(updated.getTipo());
        r.setSubtipo(updated.getSubtipo());
        r.setNumeroPessoas(updated.getNumeroPessoas());
        r.setDificuldade(updated.getDificuldade());
        r.setIngredientes(updated.getIngredientes());
        r.setPassosPreparacao(updated.getPassosPreparacao());
        r.setFotoUrl(updated.getFotoUrl());
        salvarReceita(r);
        return Optional.of(r);
    }

    public boolean eliminarReceita(String id) throws IOException {
        if (!isValidUUID(id)) return false;
        boolean removed = receitas.removeIf(r -> r.getId().equals(id));
        if (removed) {
            encontrarFicheiroPorId(id).ifPresent(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
        return removed;
    }

    private void salvarReceita(Receita receita) throws IOException {
        // Check if the file already exists for this ID and reuse its name
        Optional<Path> existing = encontrarFicheiroPorId(receita.getId());
        Path file = existing.orElseGet(() -> dataDir.resolve(toSlug(receita.getTitulo()) + ".json"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), receita);
    }

    private Optional<Path> encontrarFicheiroPorId(String id) throws IOException {
        try (Stream<Path> paths = Files.list(dataDir)) {
            return paths.filter(f -> f.toString().endsWith(".json")).filter(f -> {
                try {
                    Receita r = mapper.readValue(f.toFile(), Receita.class);
                    return id.equals(r.getId());
                } catch (IOException e) {
                    return false;
                }
            }).findFirst();
        }
    }

    private static String toSlug(String title) {
        if (title == null) return "receita";
        return title.toLowerCase()
            .replaceAll("[àáâãä]", "a")
            .replaceAll("[èéêë]", "e")
            .replaceAll("[ìíîï]", "i")
            .replaceAll("[òóôõö]", "o")
            .replaceAll("[ùúûü]", "u")
            .replaceAll("[ç]", "c")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    private List<Receita> carregarDeFicheiro() throws IOException {
        List<Receita> lista = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) paths.filter(f -> f.toString().endsWith(".json"))::iterator) {
                lista.add(mapper.readValue(p.toFile(), Receita.class));
            }
        }
        return lista;
    }

    private boolean isValidUUID(String id) {
        if (id == null) return false;
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<Receita> criarReceitasExemplo() {
        List<Receita> exemplos = new ArrayList<>();

        Receita caldoVerde = new Receita();
        caldoVerde.setId(UUID.randomUUID().toString());
        caldoVerde.setTitulo("Caldo Verde");
        caldoVerde.setOrigem("Tradição Minhota");
        caldoVerde.setTipo("sopas");
        caldoVerde.setNumeroPessoas(4);
        caldoVerde.setDificuldade("principiante");
        caldoVerde.setIngredientes(Arrays.asList(
            new Ingrediente("Batata", "500g"),
            new Ingrediente("Couve galega", "200g"),
            new Ingrediente("Chouriço", "1 unidade"),
            new Ingrediente("Azeite", "3 colheres de sopa"),
            new Ingrediente("Cebola", "1 unidade"),
            new Ingrediente("Alho", "2 dentes"),
            new Ingrediente("Sal", "q.b."),
            new Ingrediente("Água", "1,5L")
        ));
        caldoVerde.setPassosPreparacao(Arrays.asList(
            "Descascar e cortar as batatas em pedaços. Picar a cebola e o alho.",
            "Levar ao lume uma panela com o azeite, a cebola e o alho. Refogar até ficarem translúcidos.",
            "Adicionar as batatas e a água. Cozinhar em lume médio até as batatas estarem macias (cerca de 20 min).",
            "Triturar com a varinha mágica até obter um caldo homogéneo.",
            "Cortar a couve galega em juliana muito fina e adicionar ao caldo. Cozinhar 5 minutos.",
            "Fritar ligeiramente as rodelas de chouriço e adicionar à sopa.",
            "Retificar o sal e servir bem quente."
        ));
        exemplos.add(caldoVerde);

        Receita bacalhauBras = new Receita();
        bacalhauBras.setId(UUID.randomUUID().toString());
        bacalhauBras.setTitulo("Bacalhau à Brás");
        bacalhauBras.setOrigem("Lisboa");
        bacalhauBras.setTipo("peixe");
        bacalhauBras.setNumeroPessoas(4);
        bacalhauBras.setDificuldade("intermedio");
        bacalhauBras.setIngredientes(Arrays.asList(
            new Ingrediente("Bacalhau demolhado", "600g"),
            new Ingrediente("Batata palha", "300g"),
            new Ingrediente("Ovos", "6 unidades"),
            new Ingrediente("Cebola", "2 unidades"),
            new Ingrediente("Alho", "3 dentes"),
            new Ingrediente("Azeite", "6 colheres de sopa"),
            new Ingrediente("Salsa fresca", "q.b."),
            new Ingrediente("Azeitonas pretas", "q.b."),
            new Ingrediente("Sal e pimenta", "q.b.")
        ));
        bacalhauBras.setPassosPreparacao(Arrays.asList(
            "Cozer o bacalhau em água, desfiar em lascas pequenas e reservar.",
            "Refogar a cebola e o alho picados no azeite em lume médio até ficarem dourados.",
            "Adicionar o bacalhau desfiado e saltear durante 2 minutos.",
            "Juntar a batata palha e envolver bem com o bacalhau.",
            "Bater os ovos com sal e pimenta e adicionar à mistura, mexendo em lume brando.",
            "Retirar do lume quando os ovos estiverem cremosos mas não completamente secos.",
            "Decorar com salsa picada e azeitonas pretas. Servir de imediato."
        ));
        exemplos.add(bacalhauBras);

        Receita pastelNata = new Receita();
        pastelNata.setId(UUID.randomUUID().toString());
        pastelNata.setTitulo("Pastéis de Nata");
        pastelNata.setOrigem("Mosteiro dos Jerónimos, Lisboa");
        pastelNata.setTipo("doces");
        pastelNata.setNumeroPessoas(12);
        pastelNata.setDificuldade("avançado");
        pastelNata.setFotoUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/3/33/Pastel_de_nata.jpg/640px-Pastel_de_nata.jpg");
        pastelNata.setIngredientes(Arrays.asList(
            new Ingrediente("Massa folhada", "300g"),
            new Ingrediente("Gemas de ovo", "6 unidades"),
            new Ingrediente("Natas", "300ml"),
            new Ingrediente("Açúcar", "200g"),
            new Ingrediente("Farinha", "2 colheres de sopa"),
            new Ingrediente("Água", "100ml"),
            new Ingrediente("Casca de limão", "1 tira"),
            new Ingrediente("Pau de canela", "1 unidade"),
            new Ingrediente("Canela em pó", "q.b.")
        ));
        pastelNata.setPassosPreparacao(Arrays.asList(
            "Pré-aquecer o forno a 250°C. Untar as forminhas de pastel de nata.",
            "Forrar as forminhas com a massa folhada, pressionando bem para as bordas.",
            "Levar ao forno a 200°C durante 5 minutos para pré-cozer ligeiramente.",
            "Preparar o creme: num tachinho, misturar o açúcar com a água, a casca de limão e o pau de canela. Ferver durante 3 minutos.",
            "À parte, misturar as gemas com a farinha e as natas até ficar homogéneo.",
            "Retirar a casca de limão e o pau de canela da calda e adicioná-la aos poucos à mistura de ovos, mexendo sempre.",
            "Cozinhar o creme em lume brando, mexendo, até engrossar ligeiramente.",
            "Encher as forminhas com o creme até 3/4 da altura e levar ao forno a 250°C por 15-20 minutos, até ficarem dourados.",
            "Servir mornos, polvilhados com canela em pó e açúcar em pó a gosto."
        ));
        exemplos.add(pastelNata);

        return exemplos;
    }
}
