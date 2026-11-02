-- Q07 Cancelamentos por origem (cliente x equipe) e por serviço. Cancelamento sem origem registrada aparece como NAO_INFORMADO.
SELECT COALESCE(cancelado_por, 'NAO_INFORMADO') AS origem, COUNT(*) AS cancelados
FROM vw_fato_agendamentos
WHERE status = 'CANCELADO'
GROUP BY COALESCE(cancelado_por, 'NAO_INFORMADO')
ORDER BY cancelados DESC, origem;
