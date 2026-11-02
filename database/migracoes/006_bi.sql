-- Etapa 6: painel de BI. Uma função devolve TODOS os números agregados em JSON (sem nome, e-mail ou telefone).
-- Uso: SELECT fn_bi_resumo(90, now()::timestamp);   Período = últimos p_dias; "anterior" = p_dias antes disso.
BEGIN;
CREATE OR REPLACE FUNCTION fn_bi_resumo(p_dias INTEGER, p_agora TIMESTAMP) RETURNS JSONB
LANGUAGE sql STABLE AS $f$
WITH par AS (SELECT p_agora - make_interval(days => p_dias) AS ini, p_agora - make_interval(days => 2 * p_dias) AS ini_ant, p_agora AS fim),
atual AS (SELECT a.* FROM agendamentos a, par WHERE a.inicio >= par.ini AND a.inicio < par.fim),
ant AS (SELECT a.* FROM agendamentos a, par WHERE a.inicio >= par.ini_ant AND a.inicio < par.ini),
demanda AS (SELECT * FROM atual WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO')),
kp AS (
  SELECT
    COALESCE(SUM(preco_referencia) FILTER (WHERE status = 'CONCLUIDO'), 0) AS receita,
    COUNT(*) FILTER (WHERE status = 'CONCLUIDO') AS concluidos,
    COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO','FALTOU')) AS pedidos,
    COUNT(*) FILTER (WHERE status = 'CANCELADO') AS cancelados,
    COUNT(*) FILTER (WHERE status = 'FALTOU') AS faltas
  FROM atual),
kpa AS (
  SELECT COALESCE(SUM(preco_referencia) FILTER (WHERE status = 'CONCLUIDO'), 0) AS receita,
         COUNT(*) FILTER (WHERE status = 'CONCLUIDO') AS concluidos FROM ant),
rec AS (
  SELECT COUNT(*) FILTER (WHERE n >= 1) AS com_visita, COUNT(*) FILTER (WHERE n >= 2) AS recorrentes
  FROM (SELECT cliente_id, COUNT(*) n FROM atual WHERE status = 'CONCLUIDO' GROUP BY cliente_id) t),
prod AS (
  SELECT COALESCE(SUM(ap.quantidade * ap.preco_unitario), 0) AS receita FROM agendamento_produtos ap
  JOIN atual a ON a.id = ap.agendamento_id WHERE a.status = 'CONCLUIDO')
SELECT jsonb_build_object(
  'periodoDias', p_dias,
  'geradoEm', p_agora,
  'kpis', jsonb_build_object(
    'receita', kp.receita, 'receitaAnterior', kpa.receita,
    'atendimentos', kp.concluidos, 'atendimentosAnterior', kpa.concluidos,
    'ticketMedio', CASE WHEN kp.concluidos = 0 THEN 0 ELSE round(kp.receita / kp.concluidos, 2) END,
    'pedidos', kp.pedidos,
    'taxaCancelamento', CASE WHEN kp.pedidos = 0 THEN 0 ELSE round(100.0 * kp.cancelados / kp.pedidos, 1) END,
    'taxaFalta', CASE WHEN kp.concluidos + kp.faltas = 0 THEN 0 ELSE round(100.0 * kp.faltas / (kp.concluidos + kp.faltas), 1) END,
    'clientesNovos', (SELECT COUNT(*) FROM clientes c, par WHERE c.criado_em >= par.ini AND c.criado_em < par.fim),
    'clientesRecorrentesPct', CASE WHEN rec.com_visita = 0 THEN 0 ELSE round(100.0 * rec.recorrentes / rec.com_visita, 1) END,
    'receitaProdutos', prod.receita),
  'semanal', COALESCE((SELECT jsonb_agg(jsonb_build_object('semana', s, 'receita', r, 'atendimentos', q) ORDER BY s)
      FROM (SELECT date_trunc('week', inicio)::date s, SUM(preco_referencia) r, COUNT(*) q FROM atual WHERE status = 'CONCLUIDO' GROUP BY 1) t), '[]'::jsonb),
  'diaSemana', COALESCE((SELECT jsonb_agg(jsonb_build_object('dia', d, 'quantidade', q) ORDER BY d)
      FROM (SELECT EXTRACT(ISODOW FROM inicio)::int d, COUNT(*) q FROM demanda GROUP BY 1) t), '[]'::jsonb),
  'mapaCalor', COALESCE((SELECT jsonb_agg(jsonb_build_object('dia', d, 'hora', h, 'quantidade', q) ORDER BY d, h)
      FROM (SELECT EXTRACT(ISODOW FROM inicio)::int d, EXTRACT(HOUR FROM inicio)::int h, COUNT(*) q FROM demanda GROUP BY 1, 2) t), '[]'::jsonb),
  'servicos', COALESCE((SELECT jsonb_agg(jsonb_build_object('nome', n, 'quantidade', q, 'receita', r) ORDER BY r DESC, n)
      FROM (SELECT s.nome n, COUNT(*) q, SUM(a.preco_referencia) r FROM atual a JOIN servicos s ON s.id = a.servico_id WHERE a.status = 'CONCLUIDO' GROUP BY s.id, s.nome) t), '[]'::jsonb),
  'profissionais', COALESCE((SELECT jsonb_agg(jsonb_build_object('nome', n, 'quantidade', q, 'receita', r) ORDER BY r DESC, n)
      FROM (SELECT p.nome n, COUNT(*) q, SUM(a.preco_referencia) r FROM atual a JOIN profissionais p ON p.id = a.profissional_id WHERE a.status = 'CONCLUIDO' GROUP BY p.id, p.nome) t), '[]'::jsonb),
  'status', COALESCE((SELECT jsonb_agg(jsonb_build_object('status', st, 'quantidade', q) ORDER BY q DESC)
      FROM (SELECT status st, COUNT(*) q FROM atual WHERE status <> 'RESERVA_TEMPORARIA' GROUP BY 1) t), '[]'::jsonb))
FROM kp, kpa, rec, prod;
$f$;
REVOKE EXECUTE ON FUNCTION fn_bi_resumo(INTEGER, TIMESTAMP) FROM PUBLIC;
COMMIT;
