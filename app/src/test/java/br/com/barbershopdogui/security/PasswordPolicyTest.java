package br.com.barbershopdogui.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void aceitaSenhaNormalEOsLimitesDeTamanho() {
        assertNull(PasswordPolicy.violation("SenhaForte@2026"));
        assertNull(PasswordPolicy.violation("a".repeat(12)));
        assertNull(PasswordPolicy.violation("a".repeat(72)));
        assertTrue(PasswordPolicy.isValid("SenhaForte@2026"));
    }

    @Test
    void rejeitaNulaVaziaBrancaECurta() {
        assertNotNull(PasswordPolicy.violation(null));
        assertNotNull(PasswordPolicy.violation(""));
        assertNotNull(PasswordPolicy.violation(" ".repeat(20)));
        assertNotNull(PasswordPolicy.violation("a".repeat(11)));
    }

    @Test
    void rejeitaMaisDe100Caracteres() {
        assertNotNull(PasswordPolicy.violation("a".repeat(101)));
    }

    @Test
    void limiteDoBcryptContaBytesEmUtf8NaoCaracteres() {
        // 36 caracteres de 2 bytes = 72 bytes (limite exato); 37 = 74 bytes.
        assertNull(PasswordPolicy.violation("é".repeat(36)));
        assertNotNull(PasswordPolicy.violation("é".repeat(37)));
        // Emoji ocupa 4 bytes e 2 unidades de char: 19 emojis = 76 bytes.
        assertFalse(PasswordPolicy.isValid("😀".repeat(19)));
    }

    @Test
    void naoApagaEspacosNasPontas() {
        // A política avalia a senha exatamente como digitada; espaços nas pontas contam como parte dela.
        assertNull(PasswordPolicy.violation("  senha com espaços  "));
    }
}
