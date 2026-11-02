-- Testes da etapa 5 (após schema, seed, 004 e 005). Termina em ROLLBACK.
\set ON_ERROR_STOP off
BEGIN;
INSERT INTO servicos(id,nome,descricao,preco,duracao_minutos) VALUES (9201,'E5-serv','t',10,30) ON CONFLICT DO NOTHING;
INSERT INTO profissionais(id,nome) VALUES (9201,'E5-prof') ON CONFLICT DO NOTHING;
INSERT INTO clientes(id,nome,email,telefone) VALUES (9201,'Ana Real','ana@real.invalid','11999990000'),(9202,'Bia Bloq','bia@real.invalid',NULL),(9203,'Caio Sem','caio@real.invalid',NULL);
INSERT INTO agendamentos(id,servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,cliente_id,observacao)
  VALUES (9201,9201,9201,'2031-05-05 10:00','2031-05-05 10:30',30,10,'CONFIRMADO',9201,'alergia X'),
         (9202,9201,9201,'2020-05-05 10:00','2020-05-05 10:30',30,10,'CONCLUIDO',9203,'obs passada');
UPDATE clientes SET bloqueado=TRUE WHERE id=9202;
\echo CASO 1 - ESPERADO AGENDAMENTOS_ATIVOS
SELECT fn_excluir_cliente(9201,false,'2031-05-01 09:00');
\echo CASO 2 - ESPERADO BLOQUEADO (cliente bloqueado nao se exclui sozinho)
SELECT fn_excluir_cliente(9202,false,'2031-05-01 09:00');
\echo CASO 3 - ESPERADO OK (admin exclui bloqueado)
SELECT fn_excluir_cliente(9202,true,'2031-05-01 09:00');
\echo CASO 4 - ESPERADO OK + dados pessoais anonimizados e historico mantido
SELECT fn_excluir_cliente(9203,false,'2031-05-01 09:00');
SELECT nome, email, telefone IS NULL AS tel_nulo, excluido_em IS NOT NULL AS marcado FROM clientes WHERE id=9203;
SELECT observacao IS NULL AS obs_limpa, status FROM agendamentos WHERE id=9202;
\echo CASO 5 - ESPERADO NAO_ENCONTRADO (ja excluido)
SELECT fn_excluir_cliente(9203,false,'2031-05-01 09:00');
\echo CASO 6 - ESPERADO OK apos o horario passar (ativo, mas ja terminou)
SELECT fn_excluir_cliente(9201,false,'2031-05-06 09:00');
ROLLBACK;
