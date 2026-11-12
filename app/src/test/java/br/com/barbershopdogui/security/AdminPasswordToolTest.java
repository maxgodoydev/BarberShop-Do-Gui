package br.com.barbershopdogui.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminPasswordToolTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    void geraHashBcryptCusto12Valido() {
        String raw = "SenhaForte@2026";
        String hash = AdminPasswordTool.hashPassword(raw);

        assertTrue(hash.startsWith("$2a$12$") || hash.startsWith("$2b$12$"), "Hash deve ter identificador BCrypt com custo 12");
        assertTrue(encoder.matches(raw, hash), "Hash gerado deve validar com a senha original");
        assertFalse(encoder.matches("SenhaIncorreta", hash), "Hash não deve validar com senha incorreta");
    }

    @Test
    void rejeitaSenhaMenorQue12Caracteres() {
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordTool.hashPassword("curta123"));
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordTool.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordTool.hashPassword("            "));
    }

    @Test
    void naoApagaEspacosDasPontasAntesDeGerarOHash() {
        // O login compara a senha exatamente como digitada; um trim aqui geraria um hash que nunca validaria.
        String raw = "  senha com espaços  ";
        String hash = AdminPasswordTool.hashPassword(raw);
        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches(raw.trim(), hash));
    }

    @Test
    void rejeitaSenhaAcimaDoLimiteDeBytesDoBcrypt() {
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordTool.hashPassword("ç".repeat(40)));
        assertThrows(IllegalArgumentException.class, () -> AdminPasswordTool.hashPassword("a".repeat(101)));
    }
}
