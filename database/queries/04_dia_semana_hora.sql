-- Q04 Demanda por dia da semana (ISO: 1=seg ... 7=dom) e hora de início (pedidos não cancelados).
SELECT dia_semana_iso, hora_inicio, COUNT(*) AS quantidade
FROM vw_fato_agendamentos
WHERE status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO')
GROUP BY dia_semana_iso, hora_inicio
ORDER BY dia_semana_iso, hora_inicio;
