package br.com.barbershopdogui.security;

import br.com.barbershopdogui.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria a conta do PROPRIETÁRIO apenas se a tabela estiver vazia e as variáveis de ambiente existirem.
 * Não há senha padrão. Remova BOOTSTRAP_OWNER_PASSWORD do ambiente depois do primeiro acesso.
 */
@Component
public class AdminAccountsInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AdminAccountsInitializer.class);
    private final AdminUserService users;
    private final String username;
    private final String password;
    private final String name;

    public AdminAccountsInitializer(AdminUserService users, @Value("${app.bootstrap.owner-username:}") String username,
                                    @Value("${app.bootstrap.owner-password:}") String password,
                                    @Value("${app.bootstrap.owner-name:Proprietário}") String name) {
        this.users = users;
        this.username = username;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (users.count() > 0) {
                return;
            }
            if (username.isBlank() || password.isBlank()) {
                LOG.warn("Nenhuma conta administrativa existe. Defina BOOTSTRAP_OWNER_USERNAME e BOOTSTRAP_OWNER_PASSWORD (12+ caracteres) e reinicie.");
                return;
            }
            if (password.length() < 12) {
                throw new IllegalStateException("BOOTSTRAP_OWNER_PASSWORD deve ter pelo menos 12 caracteres.");
            }
            users.createOwner(username.trim().toLowerCase(), name, password);
            LOG.info("Conta de proprietário '{}' criada. Remova BOOTSTRAP_OWNER_PASSWORD do ambiente.", username.trim().toLowerCase());
        } catch (org.springframework.dao.DataAccessException ex) {
            LOG.error("Não foi possível verificar contas administrativas (o esquema do banco foi aplicado?): {}", ex.getClass().getSimpleName());
        }
    }
}
