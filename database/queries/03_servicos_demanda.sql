-- Q03 Demanda por serviço (pedidos não cancelados) e atendimentos concluídos, lado a lado.
SELECT s.servico,
       COUNT(*) FILTER (WHERE f.status IN ('SOLICITADO','CONFIRMADO','CONCLUIDO'))  AS demanda_nao_cancelada,
       COUNT(*) FILTER (WHERE f.status = 'CONCLUIDO')                                AS concluidos,
       COUNT(*) FILTER (WHERE f.status = 'CANCELADO')                                AS cancelados
FROM vw_dim_servico s
LEFT JOIN vw_fato_agendamentos f ON f.servico_id = s.servico_id
GROUP BY s.servico_id, s.servico
ORDER BY demanda_nao_cancelada DESC, s.servico;
