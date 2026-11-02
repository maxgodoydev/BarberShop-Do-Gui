-- Q02 Indicadores-chave. Denominadores explícitos:
--   taxa_cancelamento_pct = cancelados / pedidos
--   taxa_conclusao_pct    = concluidos / pedidos_vencidos (pedidos com início anterior a :agora)
--   taxa_abandono_reserva_pct = expiradas / (expiradas + pedidos)
-- :agora é parametrizado para que o resultado seja reproduzível (ex.: '2026-10-24 12:00:00').
SELECT
  COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO'))           AS pedidos,
  COUNT(*) FILTER (WHERE status = 'CONCLUIDO')                                                    AS concluidos,
  COUNT(*) FILTER (WHERE status = 'CANCELADO')                                                    AS cancelados,
  COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO') AND inicio < :agora) AS pedidos_vencidos,
  ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'CANCELADO')
        / NULLIF(COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO')), 0), 1) AS taxa_cancelamento_pct,
  ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'CONCLUIDO')
        / NULLIF(COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO') AND inicio < :agora), 0), 1) AS taxa_conclusao_pct,
  ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'EXPIRADO')
        / NULLIF(COUNT(*) FILTER (WHERE status IN ('EXPIRADO','SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO')), 0), 1) AS taxa_abandono_reserva_pct
FROM vw_fato_agendamentos;
