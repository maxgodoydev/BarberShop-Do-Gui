package br.com.barbershopdogui.service;

import br.com.barbershopdogui.security.PasswordPolicy;
import br.com.barbershopdogui.web.Dtos.AdminUserInput;
import br.com.barbershopdogui.web.Dtos.AdminUserView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Contas administrativas individuais. O proprietário cria contas de recepção e técnicas. */
@Service
public class AdminUserService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final AuditService audit;

    public AdminUserService(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
        this.audit = audit;
    }

    public List<AdminUserView> list() {
        return jdbc.query(
                "SELECT id,username,nome,papel,ativo,ultimo_login_em,exige_troca_senha FROM usuarios_admin ORDER BY username",
                (rs, n) -> new AdminUserView(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nome"),
                        rs.getString("papel"),
                        rs.getBoolean("ativo"),
                        rs.getObject("ultimo_login_em", LocalDateTime.class),
                        rs.getBoolean("exige_troca_senha")));
    }

    /**
     * Cria conta de RECEPCAO (atendimento) ou TECNICO (equipe dev); o proprietário é criado só na inicialização.
     * A conta nasce com troca de senha obrigatória. Inserção e auditoria acontecem na mesma transação.
     */
    @Transactional
    public void createStaff(AdminUserInput in) {
        String username = in.username() == null ? "" : in.username().trim().toLowerCase();
        String name = in.nome() == null ? "" : in.nome().trim();
        if (username.length() < 3 || username.length() > 60) {
            throw badRequest("O usuário deve ter entre 3 e 60 caracteres.");
        }
        if (name.isEmpty() || name.length() > 100) {
            throw badRequest("O nome é obrigatório e deve ter até 100 caracteres.");
        }
        // Perfil explícito: nunca assume TECNICO por omissão (evita conceder perfil sem o proprietário escolher).
        if (!"RECEPCAO".equals(in.papel()) && !"TECNICO".equals(in.papel())) {
            throw badRequest("Perfil inválido.");
        }
        requireValidPassword(in.senhaTemporaria());

        String role = in.papel();
        String hash = encoder.encode(in.senhaTemporaria());
        try {
            jdbc.update("INSERT INTO usuarios_admin(username,nome,senha_hash,papel,ativo,criado_em,exige_troca_senha) VALUES(?,?,?,?,TRUE,?,TRUE)",
                    username, name, hash, role, LocalDateTime.now(clock));
        } catch (DuplicateKeyException ex) {
            // Só a inserção é tratada aqui: uma falha na auditoria não pode virar "usuário duplicado".
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse usuário já existe.");
        }
        audit.log(username, "CRIAR_CONTA", "Conta criada com papel " + role);
    }

    @Transactional
    public void setActive(long id, boolean active, String currentUsername) {
        String target = findUsername(id);
        if (target.equals(currentUsername) && !active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você não pode desativar a própria conta.");
        }
        jdbc.update("UPDATE usuarios_admin SET ativo=? WHERE id=?", active, id);
        audit.log(currentUsername, active ? "ATIVAR_USUARIO" : "DESATIVAR_USUARIO", "Usuário ID " + id + " (" + target + ")");
    }

    /** Troca da própria senha. Também libera a troca obrigatória pendente. */
    @Transactional
    public void changePassword(String username, String current, String next) {
        List<String> hashes = jdbc.queryForList("SELECT senha_hash FROM usuarios_admin WHERE username=?", String.class, username);
        if (current == null || hashes.isEmpty() || !encoder.matches(current, hashes.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha atual incorreta.");
        }
        requireValidPassword(next);
        if (next.equals(current)) {
            // Sem isso, a senha temporária poderia ser "trocada" por ela mesma e o fluxo obrigatório perderia o sentido.
            throw badRequest("A nova senha deve ser diferente da senha atual.");
        }
        jdbc.update("UPDATE usuarios_admin SET senha_hash=?, exige_troca_senha=FALSE, falhas_login=0, bloqueado_ate=NULL WHERE username=?",
                encoder.encode(next), username);
        audit.log(username, "ALTERAR_SENHA", "Troca de senha efetuada pelo próprio usuário");
    }

    /**
     * Redefine a senha de outro usuário administrativo (acessível exclusivamente ao proprietário).
     * Exige nova troca no próximo acesso (exige_troca_senha = TRUE) e limpa falhas/bloqueio de login.
     */
    @Transactional
    public void resetUserPassword(long id, String newPassword, String currentAdminUsername) {
        String target = findUsername(id);
        if (target.equalsIgnoreCase(currentAdminUsername)) {
            throw badRequest("Para alterar a sua própria senha, utilize a opção de alteração de senha.");
        }
        requireValidPassword(newPassword);
        jdbc.update("UPDATE usuarios_admin SET senha_hash=?, exige_troca_senha=TRUE, falhas_login=0, bloqueado_ate=NULL WHERE id=?",
                encoder.encode(newPassword), id);
        audit.log(currentAdminUsername, "REDEFINIR_SENHA", "Senha temporária redefinida para usuário ID " + id + " (" + target + ")");
    }

    public boolean requiresPasswordChange(String username) {
        List<Boolean> list = jdbc.queryForList("SELECT exige_troca_senha FROM usuarios_admin WHERE username=?", Boolean.class, username);
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário administrativo não encontrado.");
        }
        return Boolean.TRUE.equals(list.get(0));
    }

    /** Situação atual da conta, lida do banco a cada requisição administrativa (uma única consulta). */
    public record AccountState(boolean active, boolean mustChangePassword) { }

    public Optional<AccountState> accountState(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ativo, exige_troca_senha FROM usuarios_admin WHERE username=?", username);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new AccountState(Boolean.TRUE.equals(row.get("ativo")), Boolean.TRUE.equals(row.get("exige_troca_senha"))));
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_admin", Integer.class);
        return n == null ? 0 : n;
    }

    public void createOwner(String username, String name, String password) {
        String problem = PasswordPolicy.violation(password);
        if (problem != null) {
            throw new IllegalArgumentException("Senha do proprietário inválida: " + problem);
        }
        jdbc.update("INSERT INTO usuarios_admin(username,nome,senha_hash,papel,ativo,criado_em) VALUES(?,?,?,'PROPRIETARIO',TRUE,?)",
                username, name, encoder.encode(password), LocalDateTime.now(clock));
    }

    private String findUsername(long id) {
        List<String> names = jdbc.queryForList("SELECT username FROM usuarios_admin WHERE id=?", String.class, id);
        if (names.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        return names.get(0);
    }

    private static void requireValidPassword(String raw) {
        String problem = PasswordPolicy.violation(raw);
        if (problem != null) {
            throw badRequest(problem);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
