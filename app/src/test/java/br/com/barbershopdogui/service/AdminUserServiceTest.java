package br.com.barbershopdogui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.barbershopdogui.web.Dtos.AdminUserInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Testes de unidade do AdminUserService com um JdbcTemplate em memória (sem banco real).
 * O fake só entende as consultas usadas pelo serviço; se o SQL do serviço mudar, o teste falha de forma explícita.
 */
class AdminUserServiceTest {
    private static final String SENHA_OK = "SenhaTemporaria#1";
    private static final String SENHA_NOVA = "OutraSenhaForte#2";

    private FakeJdbc jdbc;
    private RecordingAudit audit;
    private PasswordEncoder encoder;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneId.of("UTC"));
        jdbc = new FakeJdbc();
        audit = new RecordingAudit(jdbc, clock);
        encoder = new BCryptPasswordEncoder(4); // custo baixo só para os testes ficarem rápidos
        service = new AdminUserService(jdbc, encoder, clock, audit);
    }

    // ---------- criação

    @Test
    void criaContaDeRecepcaoComTrocaObrigatoriaEAuditoria() {
        service.createStaff(new AdminUserInput("  Maria.Silva ", " Maria Silva ", SENHA_OK, "RECEPCAO"));

        FakeJdbc.User u = jdbc.users.get("maria.silva");
        assertEquals("RECEPCAO", u.papel);
        assertEquals("Maria Silva", u.nome);
        assertTrue(u.ativo);
        assertTrue(u.exigeTroca, "conta nova deve exigir troca de senha");
        assertTrue(encoder.matches(SENHA_OK, u.hash));
        assertFalse(u.hash.contains(SENHA_OK), "a senha nunca é gravada em texto puro");
        assertEquals(List.of("maria.silva|CRIAR_CONTA|Conta criada com papel RECEPCAO"), audit.entries);
    }

    @Test
    void rejeitaSenhaTemporariaInvalidaSemCriarConta() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", "curta", "TECNICO")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", " ".repeat(14), "TECNICO")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", "a".repeat(101), "TECNICO")));
        assertTrue(jdbc.users.isEmpty());
        assertTrue(audit.entries.isEmpty());
    }

    @Test
    void rejeitaPerfilAusenteOuInvalidoEmVezDeAssumirTecnico() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "PROPRIETARIO")));
        assertTrue(jdbc.users.isEmpty());
    }

    @Test
    void usuarioDuplicadoRetornaConflito() {
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        assertStatus(HttpStatus.CONFLICT, () -> service.createStaff(new AdminUserInput("JOAO", "Outro João", SENHA_OK, "TECNICO")));
    }

    @Test
    void falhaNaAuditoriaNaoViraUsuarioDuplicado() {
        audit.failNext = new DuplicateKeyException("falha simulada na auditoria");
        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class,
                () -> service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO")));
        assertEquals("falha simulada na auditoria", ex.getMessage(), "o erro original deve propagar para o rollback");
    }

    // ---------- limite do BCrypt (72 bytes)

    @Test
    void rejeitaSenhaComMaisDe72BytesMesmoComMenosDe100Caracteres() {
        String unicode = "ç".repeat(40); // 40 caracteres, 80 bytes em UTF-8
        assertTrue(unicode.length() <= 100);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createStaff(new AdminUserInput("joao", "João", unicode, "TECNICO")));
    }

    @Test
    void aceitaSenhaDe72BytesExatos() {
        String limite = "a".repeat(72);
        service.createStaff(new AdminUserInput("joao", "João", limite, "TECNICO"));
        assertTrue(encoder.matches(limite, jdbc.users.get("joao").hash));
    }

    // ---------- troca da própria senha

    @Test
    void trocaDeSenhaLiberaExigenciaELimpaBloqueio() {
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        FakeJdbc.User u = jdbc.users.get("joao");
        u.falhas = 4;
        u.bloqueado = true;
        audit.entries.clear();

        service.changePassword("joao", SENHA_OK, SENHA_NOVA);

        assertFalse(u.exigeTroca);
        assertEquals(0, u.falhas);
        assertFalse(u.bloqueado);
        assertTrue(encoder.matches(SENHA_NOVA, u.hash));
        assertFalse(encoder.matches(SENHA_OK, u.hash));
        assertEquals(List.of("joao|ALTERAR_SENHA|Troca de senha efetuada pelo próprio usuário"), audit.entries);
    }

    @Test
    void trocaDeSenhaExigeSenhaAtualCorreta() {
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.changePassword("joao", "errada-errada-1", SENHA_NOVA));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.changePassword("joao", null, SENHA_NOVA));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.changePassword("inexistente", SENHA_OK, SENHA_NOVA));
        assertTrue(jdbc.users.get("joao").exigeTroca, "nada deve mudar em caso de falha");
    }

    @Test
    void trocaDeSenhaRejeitaNovaSenhaInvalidaOuIgualAAtual() {
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.changePassword("joao", SENHA_OK, "curta"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.changePassword("joao", SENHA_OK, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.changePassword("joao", SENHA_OK, SENHA_OK));
        assertTrue(jdbc.users.get("joao").exigeTroca);
        assertTrue(encoder.matches(SENHA_OK, jdbc.users.get("joao").hash));
    }

    // ---------- redefinição pelo proprietário

    @Test
    void redefinicaoExigeNovaTrocaELimpaBloqueio() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        service.changePassword("joao", SENHA_OK, SENHA_NOVA); // joão já tinha trocado a senha
        FakeJdbc.User u = jdbc.users.get("joao");
        u.falhas = 5;
        u.bloqueado = true;
        long id = u.id;
        audit.entries.clear();

        service.resetUserPassword(id, "SenhaTemporaria#3", "gui");

        assertTrue(u.exigeTroca, "após redefinição o colaborador precisa trocar de novo");
        assertEquals(0, u.falhas);
        assertFalse(u.bloqueado);
        assertTrue(encoder.matches("SenhaTemporaria#3", u.hash));
        assertEquals(1, audit.entries.size());
        assertTrue(audit.entries.get(0).startsWith("gui|REDEFINIR_SENHA|"));
        assertFalse(audit.entries.get(0).contains("SenhaTemporaria#3"), "auditoria nunca grava senha");
    }

    @Test
    void redefinicaoNaoPermiteRedefinirAPropriaSenha() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        long id = jdbc.users.get("gui").id;
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resetUserPassword(id, SENHA_NOVA, "gui"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resetUserPassword(id, SENHA_NOVA, "GUI"));
        assertTrue(encoder.matches("SenhaDoDono#2026", jdbc.users.get("gui").hash));
    }

    @Test
    void redefinicaoRejeitaSenhaInvalidaEUsuarioInexistente() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        long id = jdbc.users.get("joao").id;
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resetUserPassword(id, "curta", "gui"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resetUserPassword(id, null, "gui"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resetUserPassword(id, "ç".repeat(40), "gui"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.resetUserPassword(9999L, SENHA_NOVA, "gui"));
        assertTrue(encoder.matches(SENHA_OK, jdbc.users.get("joao").hash), "senha antiga permanece em caso de erro");
    }

    // ---------- ativação / desativação

    @Test
    void ativaEDesativaRegistrandoAuditoria() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));
        long id = jdbc.users.get("joao").id;
        audit.entries.clear();

        service.setActive(id, false, "gui");
        assertFalse(jdbc.users.get("joao").ativo);
        service.setActive(id, true, "gui");
        assertTrue(jdbc.users.get("joao").ativo);

        assertEquals(List.of(
                "gui|DESATIVAR_USUARIO|Usuário ID " + id + " (joao)",
                "gui|ATIVAR_USUARIO|Usuário ID " + id + " (joao)"), audit.entries);
    }

    @Test
    void naoPermiteDesativarAPropriaContaNemUsuarioInexistente() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        long id = jdbc.users.get("gui").id;
        assertStatus(HttpStatus.CONFLICT, () -> service.setActive(id, false, "gui"));
        assertTrue(jdbc.users.get("gui").ativo);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.setActive(9999L, true, "gui"));
    }

    // ---------- consulta e proprietário

    @Test
    void consultaExigenciaDeTrocaDeSenha() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "RECEPCAO"));
        assertFalse(service.requiresPasswordChange("gui"), "proprietário não precisa trocar no primeiro acesso");
        assertTrue(service.requiresPasswordChange("joao"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.requiresPasswordChange("ninguem"));
    }

    @Test
    void situacaoDaContaRefleteAtivacaoETrocaPendente() {
        service.createOwner("gui", "Gui", "SenhaDoDono#2026");
        service.createStaff(new AdminUserInput("joao", "João", SENHA_OK, "TECNICO"));

        var novo = service.accountState("joao").orElseThrow();
        assertTrue(novo.active());
        assertTrue(novo.mustChangePassword());

        service.changePassword("joao", SENHA_OK, SENHA_NOVA);
        assertFalse(service.accountState("joao").orElseThrow().mustChangePassword());

        service.setActive(jdbc.users.get("joao").id, false, "gui");
        assertFalse(service.accountState("joao").orElseThrow().active());

        assertTrue(service.accountState("ninguem").isEmpty());
    }

    @Test
    void createOwnerAplicaAMesmaPoliticaDeSenha() {
        assertThrows(IllegalArgumentException.class, () -> service.createOwner("gui", "Gui", "curta"));
        assertEquals(0, service.count());
    }

    // ---------- transações

    @Test
    void operacoesQueAlteramDadosEAuditamSaoTransacionais() throws Exception {
        assertTrue(AdminUserService.class.getMethod("createStaff", AdminUserInput.class).isAnnotationPresent(Transactional.class));
        assertTrue(AdminUserService.class.getMethod("setActive", long.class, boolean.class, String.class).isAnnotationPresent(Transactional.class));
        assertTrue(AdminUserService.class.getMethod("changePassword", String.class, String.class, String.class).isAnnotationPresent(Transactional.class));
        assertTrue(AdminUserService.class.getMethod("resetUserPassword", long.class, String.class, String.class).isAnnotationPresent(Transactional.class));
    }

    // ---------- utilitários

    private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected.value(), ex.getStatusCode().value());
    }

    /** Auditoria que apenas registra as chamadas (formato "usuario|acao|detalhe"). */
    private static final class RecordingAudit extends AuditService {
        final List<String> entries = new ArrayList<>();
        RuntimeException failNext;

        RecordingAudit(JdbcTemplate jdbc, Clock clock) {
            super(jdbc, clock);
        }

        @Override
        public void log(String user, String action, String detail) {
            if (failNext != null) {
                RuntimeException ex = failNext;
                failNext = null;
                throw ex;
            }
            entries.add(user + "|" + action + "|" + detail);
        }
    }

    /** Simula só a tabela usuarios_admin, reconhecendo exatamente as consultas do AdminUserService. */
    private static final class FakeJdbc extends JdbcTemplate {
        static final class User {
            long id;
            String username;
            String nome;
            String hash;
            String papel;
            boolean ativo;
            boolean exigeTroca;
            int falhas;
            boolean bloqueado;
        }

        final Map<String, User> users = new LinkedHashMap<>();
        private long seq = 0;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.startsWith("SELECT username FROM usuarios_admin WHERE id=?")) {
                long id = ((Number) args[0]).longValue();
                return (List<T>) users.values().stream().filter(u -> u.id == id).map(u -> u.username).toList();
            }
            User u = users.get(String.valueOf(args[0]));
            if (sql.startsWith("SELECT senha_hash FROM usuarios_admin WHERE username=?")) {
                return u == null ? List.of() : (List<T>) List.of(u.hash);
            }
            if (sql.startsWith("SELECT exige_troca_senha FROM usuarios_admin WHERE username=?")) {
                return u == null ? List.of() : (List<T>) List.of(u.exigeTroca);
            }
            throw new AssertionError("Consulta não prevista no teste: " + sql);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.startsWith("SELECT ativo, exige_troca_senha FROM usuarios_admin WHERE username=?")) {
                User u = users.get(String.valueOf(args[0]));
                if (u == null) {
                    return List.of();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ativo", u.ativo);
                row.put("exige_troca_senha", u.exigeTroca);
                return List.of(row);
            }
            throw new AssertionError("Consulta não prevista no teste: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type) {
            if (sql.startsWith("SELECT COUNT(*) FROM usuarios_admin")) {
                return type.cast(users.size());
            }
            throw new AssertionError("Consulta não prevista no teste: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO usuarios_admin")) {
                boolean owner = sql.contains("'PROPRIETARIO'");
                String username = (String) args[0];
                if (users.containsKey(username)) {
                    throw new DuplicateKeyException("usuarios_admin_username_key");
                }
                User u = new User();
                u.id = ++seq;
                u.username = username;
                u.nome = (String) args[1];
                u.hash = (String) args[2];
                u.papel = owner ? "PROPRIETARIO" : (String) args[3];
                u.ativo = true;
                u.exigeTroca = !owner && sql.contains("TRUE,?,TRUE)");
                users.put(username, u);
                return 1;
            }
            if (sql.startsWith("UPDATE usuarios_admin SET ativo=? WHERE id=?")) {
                return byId(((Number) args[1]).longValue()).map(u -> {
                    u.ativo = (Boolean) args[0];
                    return 1;
                }).orElse(0);
            }
            if (sql.startsWith("UPDATE usuarios_admin SET senha_hash=?, exige_troca_senha=FALSE") && sql.endsWith("WHERE username=?")) {
                User u = users.get((String) args[1]);
                if (u == null) {
                    return 0;
                }
                u.hash = (String) args[0];
                u.exigeTroca = false;
                u.falhas = 0;
                u.bloqueado = false;
                return 1;
            }
            if (sql.startsWith("UPDATE usuarios_admin SET senha_hash=?, exige_troca_senha=TRUE") && sql.endsWith("WHERE id=?")) {
                return byId(((Number) args[1]).longValue()).map(u -> {
                    u.hash = (String) args[0];
                    u.exigeTroca = true;
                    u.falhas = 0;
                    u.bloqueado = false;
                    return 1;
                }).orElse(0);
            }
            throw new AssertionError("Comando não previsto no teste: " + sql);
        }

        private java.util.Optional<User> byId(long id) {
            return users.values().stream().filter(u -> u.id == id).findFirst();
        }
    }
}
