-- Views analíticas SEM dados pessoais (sem nome, e-mail, telefone ou observação).
-- Consumidas pelas consultas em database/queries/, pelo pipeline Python e pelo Power BI.
CREATE OR REPLACE VIEW vw_dim_servico AS
SELECT id AS servico_id, nome AS servico, preco AS preco_tabela_atual, duracao_minutos AS duracao_tabela_atual,
       requer_avaliacao, ilustrativo, ativo
FROM servicos;

CREATE OR REPLACE VIEW vw_dim_profissional AS
SELECT id AS profissional_id, nome AS profissional, ativo FROM profissionais;

-- Granularidade: 1 linha por agendamento (inclui reservas temporárias e expiradas).
CREATE OR REPLACE VIEW vw_fato_agendamentos AS
SELECT a.id                                   AS agendamento_id,
       a.servico_id,
       a.profissional_id,
       CAST(a.inicio AS DATE)                 AS data_inicio,
       a.inicio,
       a.fim,
       CAST(EXTRACT(ISODOW FROM a.inicio) AS INTEGER) AS dia_semana_iso,
       CAST(EXTRACT(HOUR FROM a.inicio) AS INTEGER)   AS hora_inicio,
       CAST(EXTRACT(YEAR FROM a.inicio) AS INTEGER) * 100 + CAST(EXTRACT(MONTH FROM a.inicio) AS INTEGER) AS ano_mes,
       a.duracao_minutos,
       a.preco_referencia,                    -- preço de tabela: NÃO é receita
       a.status,
       a.cancelado_por,
       a.criado_em,
       CAST(a.criado_em AS DATE)              AS data_criacao,
       ROUND(CAST(EXTRACT(EPOCH FROM (a.inicio - a.criado_em)) / 3600.0 AS NUMERIC), 1) AS antecedencia_horas
FROM agendamentos a;

CREATE OR REPLACE VIEW vw_dim_calendario AS
SELECT CAST(d AS DATE) AS data,
       CAST(EXTRACT(YEAR FROM d) AS INTEGER)  AS ano,
       CAST(EXTRACT(MONTH FROM d) AS INTEGER) AS mes,
       CAST(EXTRACT(ISODOW FROM d) AS INTEGER) AS dia_semana_iso
FROM generate_series(
       COALESCE((SELECT MIN(CAST(inicio AS DATE)) FROM agendamentos), CURRENT_DATE - 90),
       COALESCE((SELECT MAX(CAST(inicio AS DATE)) FROM agendamentos), CURRENT_DATE) + 30,
       INTERVAL '1 day') AS d;

-- Papel somente leitura para o Power BI / pipeline. Crie com senha própria:
--   CREATE ROLE analytics_ro LOGIN PASSWORD '<defina-fora-do-repositorio>';
--   GRANT USAGE ON SCHEMA public TO analytics_ro;
--   GRANT SELECT ON vw_fato_agendamentos, vw_dim_servico, vw_dim_profissional, vw_dim_calendario TO analytics_ro;
