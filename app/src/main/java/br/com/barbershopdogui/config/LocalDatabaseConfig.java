package br.com.barbershopdogui.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * SOMENTE para uso local (perfil "local"): sobe um PostgreSQL real embutido na própria JVM, aplica os scripts
 * de database/ e entrega o DataSource. Nada precisa ser instalado. Os dados são descartados a cada execução.
 * Em produção este perfil não é ativado e o app usa DB_URL/DB_USERNAME/DB_PASSWORD normalmente.
 */
@Configuration
@Profile("local")
public class LocalDatabaseConfig {
    private static final Logger LOG = LoggerFactory.getLogger(LocalDatabaseConfig.class);

    /**
     * Ordem importa: a migração 004 preenche agendamento_servicos a partir do que já existe, então vem depois do seed.
     * Já a tabela produtos só nasce na 004, então os produtos de demonstração vêm por último.
     */
    private static final List<String> SCRIPTS = List.of(
            "schema.sql", "analytics_views.sql", "seed_demo.sql",
            "migracoes/004_etapa4.sql",
"migracoes/005_exclusao_e_supabase.sql",
"migracoes/006_bi.sql",
"migracoes/007_etapa4b.sql",
"migracoes/008_perfis_balcao.sql",
"migracoes/009_regras_servicos.sql",
"seed_demo_produtos.sql");

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
        LOG.info("Perfil 'local': iniciando PostgreSQL embutido (a 1ª vez baixa/extrai os binários; pode demorar)...");
        return EmbeddedPostgres.builder().setServerConfig("timezone", "America/Sao_Paulo").start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres pg, @Value("${app.local.sql-dir:../database}") String sqlDir) throws IOException, SQLException {
        DataSource ds = pg.getPostgresDatabase();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String name : SCRIPTS) {
                Path file = Path.of(sqlDir, name).toAbsolutePath().normalize();
                if (!Files.exists(file)) {
                    throw new IllegalStateException("Script não encontrado: " + file + " (rode a partir da pasta app/ ou ajuste app.local.sql-dir)");
                }
                LOG.info("Aplicando {}", name);
                st.execute(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        LOG.info("Banco local pronto.");
        return ds;
    }
}
