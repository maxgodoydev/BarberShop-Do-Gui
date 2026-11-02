-- Etapa 4b: alergias informadas no agendamento (dado de saúde: coletado só com ciência do cliente e apagado na exclusão do cadastro).
-- Idempotente. Aplicar depois de 004, 005 e 006.
BEGIN;
ALTER TABLE agendamentos ADD COLUMN IF NOT EXISTS alergias VARCHAR(300);

-- Exclusão = anonimização (igual à 005) + apaga também as alergias.
CREATE OR REPLACE FUNCTION fn_excluir_cliente(p_id BIGINT, p_por_admin BOOLEAN, p_agora TIMESTAMP) RETURNS TEXT
LANGUAGE plpgsql AS $f$
DECLARE v_bloqueado BOOLEAN;
BEGIN
  SELECT bloqueado INTO v_bloqueado FROM clientes WHERE id = p_id AND excluido_em IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN 'NAO_ENCONTRADO'; END IF;
  IF EXISTS (SELECT 1 FROM agendamentos WHERE cliente_id = p_id
             AND status IN ('RESERVA_TEMPORARIA','SOLICITADO','CONFIRMADO') AND fim > p_agora) THEN
    RETURN 'AGENDAMENTOS_ATIVOS';
  END IF;
  IF v_bloqueado AND NOT p_por_admin THEN RETURN 'BLOQUEADO'; END IF;
  UPDATE clientes SET nome = 'Cliente removido', email = 'removido-' || id || '@removido.invalid',
         telefone = NULL, excluido_em = p_agora WHERE id = p_id;
  UPDATE agendamentos SET observacao = NULL, alergias = NULL WHERE cliente_id = p_id;
  DELETE FROM tokens_acesso WHERE cliente_id = p_id;
  RETURN 'OK';
END $f$;
REVOKE EXECUTE ON FUNCTION fn_excluir_cliente(BIGINT, BOOLEAN, TIMESTAMP) FROM PUBLIC;
COMMIT;
