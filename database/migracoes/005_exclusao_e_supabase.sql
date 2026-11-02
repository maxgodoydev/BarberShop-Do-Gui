-- Etapa 5: exclusão de cadastro (LGPD) + endurecimento para Supabase. Idempotente.
-- Execução: psql -v ON_ERROR_STOP=1 -f database/migracoes/005_exclusao_e_supabase.sql   (ou cole no SQL Editor do Supabase)
BEGIN;
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS excluido_em TIMESTAMP;

-- Exclusão = anonimização: some o dado pessoal, mas o agendamento antigo fica para os indicadores (sem identificar ninguém).
-- Regras: sem agendamento ativo futuro; cliente bloqueado NÃO pode se excluir sozinho (senão fugiria do bloqueio);
-- admin geral / equipe de TI (p_por_admin = true) podem excluir cliente bloqueado.
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
  UPDATE agendamentos SET observacao = NULL WHERE cliente_id = p_id;
  DELETE FROM tokens_acesso WHERE cliente_id = p_id;
  RETURN 'OK';
END $f$;

-- Supabase expõe o schema public por uma API REST (chaves anon/authenticated). Este app NÃO usa essa API,
-- então fechamos: RLS ligada em toda tabela (sem políticas = ninguém lê) e views sem acesso público.
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', r.tablename);
  END LOOP;
  -- Views continuam rodando como dono (padrão): o papel somente-leitura do BI lê só as views, sem acesso às tabelas.
  FOR r IN SELECT viewname FROM pg_views WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER VIEW public.%I RESET (security_invoker)', r.viewname);
  END LOOP;
  FOR r IN SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated') LOOP
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', r.rolname);
    EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r.rolname);
  END LOOP;
  -- Objetos criados no futuro também não nascem abertos para a API pública.
  FOR r IN SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated') LOOP
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', r.rolname);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', r.rolname);
  END LOOP;
END $$;
-- Funções também ficam acessíveis pela API REST (/rpc) se herdarem EXECUTE de PUBLIC: fechamos.
REVOKE EXECUTE ON FUNCTION fn_excluir_cliente(BIGINT, BOOLEAN, TIMESTAMP) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION fn_regras_cliente_agendamento() FROM PUBLIC;
COMMIT;
