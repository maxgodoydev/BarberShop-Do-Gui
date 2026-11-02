package br.com.barbershopdogui.service;

import br.com.barbershopdogui.web.Dtos.ProfessionalInput;
import br.com.barbershopdogui.web.Dtos.ProfessionalView;
import br.com.barbershopdogui.web.Dtos.ServiceInput;
import br.com.barbershopdogui.web.Dtos.ServiceView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Serviços e profissionais (catálogo). */
@Service
public class CatalogService {
    public static final int MAX_PROFESSIONALS = 3;
    private static final RowMapper<ServiceView> SERVICE_MAPPER = (rs, n) -> new ServiceView(rs.getLong("id"), rs.getString("nome"),
            rs.getString("descricao"), rs.getBigDecimal("preco"), rs.getInt("duracao_minutos"), rs.getBoolean("requer_avaliacao"),
            rs.getBoolean("ilustrativo"), rs.getBoolean("ativo"));
    private static final String SERVICE_COLUMNS = "id,nome,descricao,preco,duracao_minutos,requer_avaliacao,ilustrativo,ativo";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CatalogService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<ServiceView> activeServices() {
        return jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM servicos WHERE ativo=TRUE ORDER BY ordem,nome", SERVICE_MAPPER);
    }

    public List<ServiceView> allServices() {
        return jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM servicos ORDER BY ordem,nome", SERVICE_MAPPER);
    }

    public ServiceView requireActiveService(long id) {
        List<ServiceView> rows = jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM servicos WHERE id=? AND ativo=TRUE", SERVICE_MAPPER, id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O serviço selecionado não está disponível.");
        }
        return rows.get(0);
    }

    public ServiceView createService(ServiceInput in) {
        try {
            Long id = jdbc.queryForObject("INSERT INTO servicos(nome,descricao,preco,duracao_minutos,requer_avaliacao,ilustrativo,ativo,ordem,criado_em,atualizado_em)"
                            + " VALUES(?,?,?,?,?,?,TRUE,?,?,?) RETURNING id", Long.class, in.nome().trim(), in.descricao().trim(), in.preco(),
                    in.duracaoMinutos(), in.requerAvaliacao(), in.ilustrativo(), in.ordem() == null ? 100 : in.ordem(), now(), now());
            return jdbc.queryForObject("SELECT " + SERVICE_COLUMNS + " FROM servicos WHERE id=?", SERVICE_MAPPER, id);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um serviço com esse nome.");
        }
    }

    public ServiceView updateService(long id, ServiceInput in) {
        int n;
        try {
            n = jdbc.update("UPDATE servicos SET nome=?,descricao=?,preco=?,duracao_minutos=?,requer_avaliacao=?,ilustrativo=?,ordem=?,atualizado_em=? WHERE id=?",
                    in.nome().trim(), in.descricao().trim(), in.preco(), in.duracaoMinutos(), in.requerAvaliacao(), in.ilustrativo(),
                    in.ordem() == null ? 100 : in.ordem(), now(), id);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um serviço com esse nome.");
        }
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado.");
        }
        return jdbc.queryForObject("SELECT " + SERVICE_COLUMNS + " FROM servicos WHERE id=?", SERVICE_MAPPER, id);
    }

    public void setServiceActive(long id, boolean active) {
        if (jdbc.update("UPDATE servicos SET ativo=?,atualizado_em=? WHERE id=?", active, now(), id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado.");
        }
    }

    public List<ProfessionalView> professionals(boolean onlyActive) {
        List<ProfessionalView> base = jdbc.query("SELECT id,nome,ativo FROM profissionais" + (onlyActive ? " WHERE ativo=TRUE" : "") + " ORDER BY ordem,nome",
                (rs, n) -> new ProfessionalView(rs.getLong("id"), rs.getString("nome"), rs.getBoolean("ativo"), List.of()));
        return base.stream().map(p -> new ProfessionalView(p.id(), p.nome(), p.ativo(),
                jdbc.queryForList("SELECT servico_id FROM profissional_servicos WHERE profissional_id=? ORDER BY servico_id", Long.class, p.id()))).toList();
    }

    @Transactional
    public ProfessionalView createProfessional(ProfessionalInput in) {
        requireProfessionalSlot();
        Long id = jdbc.queryForObject("INSERT INTO profissionais(nome,ativo,ordem,criado_em) VALUES(?,TRUE,?,?) RETURNING id", Long.class,
                in.nome().trim(), in.ordem() == null ? 100 : in.ordem(), now());
        setProfessionalServices(id, in.servicoIds());
        initDefaultWindows(id);
        return professionals(false).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /** Inicializa a grade semanal com os horários padrão da barbearia (editáveis depois em /admin/horarios.html). */
    private void initDefaultWindows(Long professionalId) {
        List<Long> others = jdbc.queryForList("SELECT DISTINCT profissional_id FROM horarios_atendimento WHERE profissional_id <> ? LIMIT 1", Long.class, professionalId);
        if (!others.isEmpty()) {
            jdbc.update("INSERT INTO horarios_atendimento(profissional_id,dia_semana,hora_inicio,hora_fim) "
                    + "SELECT ?,dia_semana,hora_inicio,hora_fim FROM horarios_atendimento WHERE profissional_id=?", professionalId, others.get(0));
        } else {
            for (int d = 1; d <= 5; d++) {
                jdbc.update("INSERT INTO horarios_atendimento(profissional_id,dia_semana,hora_inicio,hora_fim) VALUES(?,?,?,?)",
                        professionalId, d, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(19, 0));
            }
            jdbc.update("INSERT INTO horarios_atendimento(profissional_id,dia_semana,hora_inicio,hora_fim) VALUES(?,?,?,?)",
                    professionalId, 6, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0));
        }
    }

    @Transactional
    public ProfessionalView updateProfessional(long id, ProfessionalInput in) {
        if (jdbc.update("UPDATE profissionais SET nome=?,ordem=? WHERE id=?", in.nome().trim(), in.ordem() == null ? 100 : in.ordem(), id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado.");
        }
        setProfessionalServices(id, in.servicoIds());
        return professionals(false).stream().filter(p -> p.id() == id).findFirst().orElseThrow();
    }

    /** A barbearia trabalha com no máximo 3 profissionais ativos (regra do projeto). */
    private void requireProfessionalSlot() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM profissionais WHERE ativo=TRUE", Integer.class);
        if (n != null && n >= MAX_PROFESSIONALS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de " + MAX_PROFESSIONALS + " profissionais ativos. Desative um antes de cadastrar outro.");
        }
    }

    public void setProfessionalActive(long id, boolean active) {
        if (active) {
            Integer already = jdbc.queryForObject("SELECT COUNT(*) FROM profissionais WHERE ativo=TRUE AND id<>?", Integer.class, id);
            if (already != null && already >= MAX_PROFESSIONALS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de " + MAX_PROFESSIONALS + " profissionais ativos.");
            }
        }
        if (jdbc.update("UPDATE profissionais SET ativo=? WHERE id=?", active, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado.");
        }
    }

    private void setProfessionalServices(long professionalId, List<Long> serviceIds) {
        jdbc.update("DELETE FROM profissional_servicos WHERE profissional_id=?", professionalId);
        if (serviceIds == null) {
            return;
        }
        for (Long sid : serviceIds.stream().distinct().toList()) {
            try {
                jdbc.update("INSERT INTO profissional_servicos(profissional_id,servico_id) VALUES(?,?)", professionalId, sid);
            } catch (DataAccessException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço inválido na lista do profissional.");
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
