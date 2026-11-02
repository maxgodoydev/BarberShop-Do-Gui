-- Q01 Visão geral por status. "pedidos" = SOLICITADO+CONFIRMADO+CONCLUIDO+CANCELADO (pedido efetivamente enviado).
-- Reservas temporárias e expiradas NÃO são pedidos: medem funil, não demanda.
SELECT status, COUNT(*) AS quantidade
FROM vw_fato_agendamentos
GROUP BY status
ORDER BY status;
