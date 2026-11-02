-- Q08 Possíveis gargalos: pedidos vencidos ainda SOLICITADO (sem resposta) ou CONFIRMADO (sem baixa).
SELECT status, COUNT(*) AS vencidos_sem_desfecho
FROM vw_fato_agendamentos
WHERE status IN ('SOLICITADO','CONFIRMADO') AND inicio < :agora
GROUP BY status
ORDER BY status;
