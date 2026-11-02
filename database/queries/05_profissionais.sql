-- Q05 Distribuição por profissional e percentual sobre o total de pedidos não cancelados.
WITH base AS (
  SELECT profissional_id, COUNT(*) AS qtd,
         COUNT(*) FILTER (WHERE status = 'CONCLUIDO') AS concluidos,
         COUNT(*) FILTER (WHERE status = 'CANCELADO') AS cancelados_prof
  FROM vw_fato_agendamentos
  WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO')
  GROUP BY profissional_id
), cancel AS (
  SELECT profissional_id, COUNT(*) AS cancelados
  FROM vw_fato_agendamentos WHERE status = 'CANCELADO' GROUP BY profissional_id
)
SELECT p.profissional, COALESCE(b.qtd,0) AS pedidos_nao_cancelados, COALESCE(b.concluidos,0) AS concluidos,
       COALESCE(c.cancelados,0) AS cancelados,
       ROUND(100.0 * COALESCE(b.qtd,0) / NULLIF((SELECT SUM(qtd) FROM base),0), 1) AS participacao_pct
FROM vw_dim_profissional p
LEFT JOIN base b ON b.profissional_id = p.profissional_id
LEFT JOIN cancel c ON c.profissional_id = p.profissional_id
ORDER BY pedidos_nao_cancelados DESC, p.profissional;
