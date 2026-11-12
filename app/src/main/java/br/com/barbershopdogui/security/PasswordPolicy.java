package br.com.barbershopdogui.security;

import java.nio.charset.StandardCharsets;

/**
 * Política única de senhas administrativas, compartilhada pelo serviço e pela ferramenta de redefinição manual.
 *
 * <ul>
 *   <li>12 a 100 caracteres (mesmo critério das anotações {@code @Size} dos DTOs);</li>
 *   <li>não pode ser vazia nem conter só espaços;</li>
 *   <li>no máximo 72 <b>bytes</b> em UTF-8: é o limite real do BCrypt. Senhas com acentos ou emojis
 *       ocupam mais de um byte por caractere, então 72 caracteres podem passar de 72 bytes.</li>
 * </ul>
 *
 * A senha nunca é "aparada" (trim): o que o usuário digita no login é exatamente o que foi cadastrado.
 */
public final class PasswordPolicy {
    public static final int MIN_CHARS = 12;
    public static final int MAX_CHARS = 100;
    public static final int MAX_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Devolve a mensagem de erro em português ou {@code null} quando a senha é válida. */
    public static String violation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "A senha deve ter entre " + MIN_CHARS + " e " + MAX_CHARS + " caracteres.";
        }
        if (raw.length() < MIN_CHARS || raw.length() > MAX_CHARS) {
            return "A senha deve ter entre " + MIN_CHARS + " e " + MAX_CHARS + " caracteres.";
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            return "A senha é longa demais para o algoritmo de proteção (máximo de " + MAX_BCRYPT_BYTES
                    + " bytes; acentos e símbolos ocupam mais de um byte). Use uma senha menor.";
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return violation(raw) == null;
    }
}
