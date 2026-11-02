package br.com.barbershopdogui.web;

import br.com.barbershopdogui.service.AuditService;
import br.com.barbershopdogui.web.Dtos.ProductInput;
import br.com.barbershopdogui.web.Dtos.StockInput;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Estoque dos produtos da loja. Recepção e proprietário ajustam a quantidade; só o proprietário cadastra/edita produtos. */
@RestController
@RequestMapping("/api/admin/produtos")
public class EstoqueController {
    public record ProductView(Long id, String nome, String marca, BigDecimal preco, int estoque, int estoqueMinimo, String situacao,
                              boolean ativo, boolean ilustrativo) { }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public EstoqueController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping
    public List<ProductView> list() {
        return jdbc.query("SELECT id,nome,marca,preco,estoque,estoque_minimo,ativo,ilustrativo FROM produtos "
                + "ORDER BY ativo DESC,(estoque=0) DESC,(estoque<=estoque_minimo) DESC,nome", (rs, n) -> {
            int stock = rs.getInt("estoque"), min = rs.getInt("estoque_minimo");
            return new ProductView(rs.getLong("id"), rs.getString("nome"), rs.getString("marca"), rs.getBigDecimal("preco"), stock, min,
                    stock == 0 ? "ZERADO" : stock <= min ? "BAIXO" : "OK", rs.getBoolean("ativo"), rs.getBoolean("ilustrativo"));
        });
    }

    @PatchMapping("/{id}/estoque")
    public Map<String, String> setStock(@PathVariable long id, @Valid @RequestBody StockInput in, Principal principal) {
        List<Integer> old = jdbc.queryForList("SELECT estoque FROM produtos WHERE id=?", Integer.class, id);
        if (old.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        }
        jdbc.update("UPDATE produtos SET estoque=? WHERE id=?", in.quantidade(), id);
        audit.log(principal.getName(), "ESTOQUE_AJUSTAR", "produto " + id + ": " + old.get(0) + " -> " + in.quantidade());
        return Map.of("mensagem", "Estoque atualizado.");
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ProductInput in, Principal principal) {
        try {
            return insert(in, principal);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um produto com esse nome.");
        }
    }

    private Map<String, Object> insert(ProductInput in, Principal principal) {
        Long id = jdbc.queryForObject("INSERT INTO produtos(nome,marca,preco,estoque,estoque_minimo,ilustrativo,ativo) VALUES(?,?,?,?,?,FALSE,TRUE) RETURNING id",
                Long.class, in.nome().trim(), orEmpty(in.marca()), in.preco(), in.estoque(), in.estoqueMinimo());
        audit.log(principal.getName(), "PRODUTO_CRIAR", "produto " + id);
        return Map.of("id", id, "mensagem", "Produto cadastrado.");
    }

    @PutMapping("/{id}")
    public Map<String, String> update(@PathVariable long id, @Valid @RequestBody ProductInput in, Principal principal) {
        int n = jdbc.update("UPDATE produtos SET nome=?,marca=?,preco=?,estoque=?,estoque_minimo=?,ilustrativo=FALSE WHERE id=?",
                in.nome().trim(), orEmpty(in.marca()), in.preco(), in.estoque(), in.estoqueMinimo(), id);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        }
        audit.log(principal.getName(), "PRODUTO_EDITAR", "produto " + id);
        return Map.of("mensagem", "Produto atualizado.");
    }

    @PatchMapping("/{id}/ativo")
    public Map<String, String> setActive(@PathVariable long id, @RequestBody Dtos.ActiveInput in, Principal principal) {
        int n = jdbc.update("UPDATE produtos SET ativo=? WHERE id=?", in.ativo(), id);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        }
        audit.log(principal.getName(), in.ativo() ? "PRODUTO_ATIVAR" : "PRODUTO_DESATIVAR", "produto " + id);
        return Map.of("mensagem", "Produto atualizado.");
    }

    private static String orEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
