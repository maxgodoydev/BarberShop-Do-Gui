package br.com.barbershopdogui.security;

import java.io.Console;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilitário administrativo controlado para gerar hash BCrypt com custo 12.
 *
 * Uso:
 * java -cp ... br.com.barbershopdogui.security.AdminPasswordTool <username>
 */
public final class AdminPasswordTool {

    private AdminPasswordTool() {
    }

    /** Gera o hash BCrypt (custo 12) seguindo a mesma política de senhas do sistema. Não faz trim. */
    public static String hashPassword(String rawPassword) {
        String problem = PasswordPolicy.violation(rawPassword);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        return new BCryptPasswordEncoder(12).encode(rawPassword);
    }

    public static void main(String[] args) {

        if (args.length != 1 || args[0].isBlank()) {
            System.err.println(
                    "Uso: AdminPasswordTool <username>"
            );
            System.exit(1);
        }

        Console console = System.console();

        if (console == null) {
            System.err.println(
                    "Execute pelo terminal para informar a senha com segurança."
            );
            System.exit(1);
        }

        String username = args[0].trim().toLowerCase();

        char[] passwordChars = console.readPassword(
                "Digite a nova senha: "
        );

        char[] confirmationChars = console.readPassword(
                "Confirme a nova senha: "
        );

        if (passwordChars == null || confirmationChars == null) {
            System.err.println("Não foi possível ler a senha.");
            System.exit(1);
        }

        try {
            String password = new String(passwordChars);
            String confirmation = new String(confirmationChars);

            if (!password.equals(confirmation)) {
                System.err.println("As senhas não coincidem.");
                System.exit(1);
            }

            String hash = hashPassword(password);

            // Escapa aspas simples para formar um literal SQL seguro.
            String safeUsername = username.replace("'", "''");

            System.out.println();
            System.out.println("==================================================");
            System.out.println("REDEFINIÇÃO ADMINISTRATIVA - BarberShop do Gui");
            System.out.println("==================================================");
            System.out.println("Usuário: " + username);
            System.out.println("Hash BCrypt (custo 12): " + hash);
            System.out.println();
            System.out.println("SQL para execução no Supabase SQL Editor:");
            System.out.println("--------------------------------------------------");

            System.out.printf(
                    "UPDATE public.usuarios_admin%n"
                    + "SET senha_hash = '%s',%n"
                    + "    exige_troca_senha = TRUE,%n"
                    + "    falhas_login = 0,%n"
                    + "    bloqueado_ate = NULL,%n"
                    + "    ativo = TRUE%n"
                    + "WHERE username = '%s'%n"
                    + "RETURNING id, username;%n",
                    hash,
                    safeUsername
            );

            System.out.println("--------------------------------------------------");

        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
            java.util.Arrays.fill(confirmationChars, '\0');
        }
    }
}
