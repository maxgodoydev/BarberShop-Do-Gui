package br.com.barbershopdogui.security;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminUserDetailsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<UserDetails> rows = jdbc.query("SELECT username,senha_hash,papel,ativo,bloqueado_ate FROM usuarios_admin WHERE username=?", (rs, n) -> {
            LocalDateTime lockedUntil = rs.getObject("bloqueado_ate", LocalDateTime.class);
            boolean locked = lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(clock));
            return User.withUsername(rs.getString("username")).password(rs.getString("senha_hash")).roles(rs.getString("papel"))
                    .disabled(!rs.getBoolean("ativo")).accountLocked(locked).build();
        }, username == null ? "" : username.trim().toLowerCase());
        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("Credenciais inválidas.");
        }
        return rows.get(0);
    }
}
