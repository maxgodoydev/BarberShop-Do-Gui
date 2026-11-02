-- Q06 Evolução mensal por data do atendimento (início); ano_mes = AAAAMM calculado na view.
SELECT ano_mes,
       COUNT(*) FILTER (WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO','CANCELADO')) AS pedidos,
       COUNT(*) FILTER (WHERE status = 'CONCLUIDO') AS concluidos,
       COUNT(*) FILTER (WHERE status = 'CANCELADO') AS cancelados
FROM vw_fato_agendamentos
GROUP BY ano_mes
ORDER BY ano_mes;
