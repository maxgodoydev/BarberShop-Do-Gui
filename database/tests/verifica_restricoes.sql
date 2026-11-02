-- Verificação das restrições (PostgreSQL). Tudo em transação com ROLLBACK ao final.
-- Cada caso roda em SAVEPOINT próprio: um erro ESPERADO não aborta os casos seguintes.
-- Execute: psql -d barbershop_gui -f database/tests/verifica_restricoes.sql   (precisa de ao menos 1 cliente: rode o seed antes)
-- STATUS: executado em PostgreSQL 16 (ver docs/testes.md).
\set ON_ERROR_STOP off
BEGIN;
INSERT INTO servicos(id,nome,descricao,preco,duracao_minutos) VALUES (9001,'T-serv','t',10,30) ON CONFLICT DO NOTHING;
INSERT INTO profissionais(id,nome) VALUES (9001,'T-prof'),(9002,'T-prof2') ON CONFLICT DO NOTHING;
INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  SELECT 9001,9001,'2030-01-07 10:00','2030-01-07 10:50',50,10,'CONFIRMADO',(SELECT MIN(id) FROM clientes) WHERE EXISTS (SELECT 1 FROM clientes);
\echo CASO 1 - ESPERADO erro 23P01 (sobreposição parcial)
SAVEPOINT c1;
INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9001,9001,'2030-01-07 10:30','2030-01-07 11:00',30,10,'CONFIRMADO',(SELECT MIN(id) FROM clientes));
ROLLBACK TO c1;
\echo CASO 2 - ESPERADO sucesso (outro profissional, mesmo horário)
SAVEPOINT c2;
INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9001,9002,'2030-01-07 10:30','2030-01-07 11:00',30,10,'CONFIRMADO',(SELECT MIN(id) FROM clientes));
RELEASE c2;
\echo CASO 3 - ESPERADO sucesso (encosta no fim do anterior: intervalo semiaberto)
SAVEPOINT c3;
INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9001,9001,'2030-01-07 10:50','2030-01-07 11:20',30,10,'SOLICITADO',(SELECT MIN(id) FROM clientes));
RELEASE c3;
\echo CASO 4 - ESPERADO erro 23514 (status ativo sem cliente)
SAVEPOINT c4;
INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status)
  VALUES (9001,9002,'2030-01-08 10:00','2030-01-08 10:30',30,10,'SOLICITADO');
ROLLBACK TO c4;
ROLLBACK;
