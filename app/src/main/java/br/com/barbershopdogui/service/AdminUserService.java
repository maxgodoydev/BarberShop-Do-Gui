package br.com.barbershopdogui.service;

import br.com.barbershopdogui.web.Dtos.AdminUserInput;
import br.com.barbershopdogui.web.Dtos.AdminUserView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Contas administrativas individuais. O proprietário cria contas de recepção e técnicas. */
@Service
public class AdminUserService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminUserService(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
    }

    public List<AdminUserView> list() {
        return jdbc.query("SELECT id,username,nome,papel,ativo,ultimo_login_em FROM usuarios_admin ORDER BY username",
                (rs, n) -> new AdminUserView(rs.getLong("id"), rs.getString("username"), rs.getString("nome"), rs.getString("papel"),
                        rs.getBoolean("ativo"), rs.getObject("ultimo_login_em", LocalDateTime.class)));
    }

    /** Cria conta de RECEPCAO (atendimento) ou TECNICO (equipe dev); o proprietário é criado só na inicialização. */
    public void createStaff(AdminUserInput in) {
        String role = "RECEPCAO".equals(in.papel()) ? "RECEPCAO" : "TECNICO";
        try {
            jdbc.update("INSERT INTO usuarios_admin(username,nome,senha_hash,papel,ativo,criado_em) VALUES(?,?,?,?,TRUE,?)",
                    in.username().trim().toLowerCase(), in.nome().trim(), encoder.encode(in.senhaTemporaria()), role, LocalDateTime.now(clock));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse usuário já existe.");
        }
    }

    public void setActive(long id, boolean active, String currentUsername) {
        List<String> names = jdbc.queryForList("SELECT username FROM usuarios_admin WHERE id=?", String.class, id);
        if (names.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        if (names.get(0).equals(currentUsername) && !active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você não pode desativar a própria conta.");
        }
        jdbc.update("UPDATE usuarios_admin SET ativo=? WHERE id=?", active, id);
    }

    public void changePassword(String username, String current, String next) {
        List<String> hashes = jdbc.queryForList("SELECT senha_hash FROM usuarios_admin WHERE username=?", String.class, username);
        if (hashes.isEmpty() || !encoder.matches(current, hashes.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha atual incorreta.");
        }
        jdbc.update("UPDATE usuarios_admin SET senha_hash=? WHERE username=?", encoder.encode(next), username);
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_admin", Integer.class);
        return n == null ? 0 : n;
    }

    public void createOwner(String username, String name, String password) {
        jdbc.update("INSERT INTO usuarios_admin(username,nome,senha_hash,papel,ativo,criado_em) VALUES(?,?,?,'PROPRIETARIO',TRUE,?)",
                username, name, encoder.encode(password), LocalDateTime.now(clock));
    }
}
