-- Testes da etapa 4 (rode após schema, seed e migracoes/004). Tudo termina em ROLLBACK.
\set ON_ERROR_STOP off
BEGIN;
INSERT INTO servicos(id,nome,descricao,preco,duracao_minutos) VALUES (9101,'E4-serv','t',10,30) ON CONFLICT DO NOTHING;
INSERT INTO profissionais(id,nome) VALUES (9101,'E4-prof') ON CONFLICT DO NOTHING;
INSERT INTO clientes(id,nome,email) VALUES (9101,'E4 Cliente','e4@teste.invalid');
INSERT INTO agendamentos(id,servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9101,9101,9101,'2031-03-03 10:00','2031-03-03 10:30',30,10,'CONFIRMADO',9101);
\echo CASO 1 - ESPERADO ok: marcar FALTOU bloqueia o cliente
SAVEPOINT c1; UPDATE agendamentos SET status='FALTOU' WHERE id=9101; SELECT bloqueado, left(bloqueio_motivo,28) FROM clientes WHERE id=9101; RELEASE c1;
\echo CASO 2 - ESPERADO erro P0001 (cliente bloqueado nao agenda)
SAVEPOINT c2; INSERT INTO agendamentos(servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9101,9101,'2031-03-04 10:00','2031-03-04 10:30',30,10,'SOLICITADO',9101); ROLLBACK TO c2;
\echo CASO 3 - ESPERADO ok: desbloqueio pela equipe volta a permitir
SAVEPOINT c3; UPDATE clientes SET bloqueado=FALSE, bloqueado_em=NULL, bloqueio_motivo=NULL WHERE id=9101;
INSERT INTO agendamentos(id,servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id)
  VALUES (9102,9101,9101,'2031-03-04 10:00','2031-03-04 10:30',30,10,'SOLICITADO',9101); RELEASE c3;
\echo CASO 4 - ESPERADO ok: 2 servicos no mesmo agendamento
SAVEPOINT c4; INSERT INTO agendamento_servicos(agendamento_id,servico_id,ordem,preco,duracao_minutos) VALUES (9102,9101,1,10,30);
INSERT INTO agendamento_servicos(agendamento_id,servico_id,ordem,preco,duracao_minutos) SELECT 9102,id,2,25,20 FROM servicos WHERE id<>9101 LIMIT 1; RELEASE c4;
\echo CASO 5 - ESPERADO erro 23505 (mesmo servico duplicado no agendamento)
SAVEPOINT c5; INSERT INTO agendamento_servicos(agendamento_id,servico_id,preco,duracao_minutos) VALUES (9102,9101,10,30); ROLLBACK TO c5;
\echo CASO 6 - ESPERADO erro 23514 (quantidade de produto > 5)
SAVEPOINT c6; INSERT INTO agendamento_produtos(agendamento_id,produto_id,quantidade,preco_unitario) SELECT 9102,MIN(id),6,30 FROM produtos; ROLLBACK TO c6;
\echo CASO 7 - ESPERADO erro 23514 (token de confirmacao sem agendamento)
SAVEPOINT c7; INSERT INTO tokens_acesso(cliente_id,token_hash,expira_em,finalidade) VALUES (9101,repeat('a',64),now()+interval '1 day','CONFIRMACAO'); ROLLBACK TO c7;
ROLLBACK;
