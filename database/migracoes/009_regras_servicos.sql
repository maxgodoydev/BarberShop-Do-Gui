-- Ajustes das regras de seleção: limite de 200 minutos e exclusão infantil + barba.
-- Execução: psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/009_regras_servicos.sql
BEGIN;

INSERT INTO servico_exclusoes (servico_id, exclui_id)
SELECT a.id, b.id
FROM servicos a
JOIN servicos b ON (a.nome = 'Corte infantil' AND b.nome = 'Barba')
                OR (a.nome = 'Barba' AND b.nome = 'Corte infantil')
ON CONFLICT DO NOTHING;

COMMIT;
