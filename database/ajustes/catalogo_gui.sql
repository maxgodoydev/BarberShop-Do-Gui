-- AJUSTE DE CATÁLOGO DA BARBEARIA DO GUI (v2)  (OPT-IN: NÃO faz parte de aplicar.sh nem das migrações)
--
-- Decisões do proprietário (nov/2026): corte R$ 35 (40 min) | Degradê e Corte na máquina desativados |
-- Barba R$ 20 | Cabelo e barba R$ 50 | Corte infantil R$ 40 (PROPOSTA: confirmar com o Gui; fica marcado como
-- "ilustrativo" = aparece com asterisco no site até você rodar: UPDATE servicos SET ilustrativo=FALSE WHERE nome='Corte infantil';)
--
-- O que mudou em relação à v1:
--   * Acha os serviços pelo NOME (a coluna é UNIQUE), não por id fixo.
--   * Se existirem DOIS serviços de corte ('Corte' e 'Corte tradicional', ex.: o de R$ 45,90 criado à mão), mantém o
--     de MAIOR histórico, DESATIVA e renomeia o outro (nunca apaga). A v1 estourava por violação de UNIQUE nesse cenário.
--   * Marca ilustrativo=FALSE nos preços confirmados (a v1 deixava o site mostrando "R$ 35,00*").
--   * Recria as regras de combinação (que não podem repetir/misturar) pelos IDs FINAIS, em vez de depender do nome da 004.
--   * Qualquer divergência -> RAISE EXCEPTION -> nada é gravado (antes: 0 linhas afetadas e COMMIT silencioso).
--   * Serviços fora desta lista (Platinado, Luzes, etc.) NÃO são tocados: o Gui decide depois.
-- Uso (produção: só depois de BACKUP): cole no SQL Editor do Supabase  |  local: psql -v ON_ERROR_STOP=1 -f catalogo_gui.sql
BEGIN;

DO $$
DECLARE
  a BIGINT; b BIGINT;
  v_corte BIGINT; v_dup BIGINT; v_inf BIGINT; v_barba BIGINT; v_combo BIGINT;
  v_now TIMESTAMP := (now() AT TIME ZONE 'America/Sao_Paulo');
  n INT;
BEGIN
  SELECT id INTO a FROM servicos WHERE nome = 'Corte tradicional';
  SELECT id INTO b FROM servicos WHERE nome = 'Corte';
  IF a IS NULL AND b IS NULL THEN
    RAISE EXCEPTION 'Não achei serviço chamado "Corte tradicional" nem "Corte". Rode o diagnóstico e ajuste o script.';
  ELSIF a IS NOT NULL AND b IS NOT NULL THEN
    IF (SELECT count(*) FROM agendamento_servicos WHERE servico_id = b) > (SELECT count(*) FROM agendamento_servicos WHERE servico_id = a)
    THEN v_corte := b; v_dup := a; ELSE v_corte := a; v_dup := b; END IF;
  ELSE
    v_corte := COALESCE(a, b); v_dup := NULL;
  END IF;

  IF v_dup IS NOT NULL THEN  -- libera o nome (UNIQUE) e tira do site; histórico preservado
    UPDATE servicos SET ativo = FALSE, nome = left(nome, 70) || ' (desativado ' || id || ')', atualizado_em = v_now WHERE id = v_dup;
  END IF;

  UPDATE servicos SET nome = 'Corte tradicional', descricao = 'Corte tradicional ou degradê, na tesoura e na máquina.',
         preco = 35.00, duracao_minutos = 40, ativo = TRUE, ilustrativo = FALSE, atualizado_em = v_now
   WHERE id = v_corte;

  UPDATE servicos SET ativo = FALSE, atualizado_em = v_now WHERE nome IN ('Degradê', 'Corte na máquina');
  GET DIAGNOSTICS n = ROW_COUNT;
  IF n <> 2 THEN RAISE EXCEPTION 'Esperava achar "Degradê" e "Corte na máquina" (achei %). Confira os nomes.', n; END IF;

  UPDATE servicos SET preco = 40.00, atualizado_em = v_now WHERE nome = 'Corte infantil' RETURNING id INTO v_inf;
  IF v_inf IS NULL THEN RAISE EXCEPTION 'Serviço "Corte infantil" não encontrado.'; END IF;

  UPDATE servicos SET preco = 20.00, ilustrativo = FALSE, atualizado_em = v_now WHERE nome = 'Barba' RETURNING id INTO v_barba;
  IF v_barba IS NULL THEN RAISE EXCEPTION 'Serviço "Barba" não encontrado.'; END IF;

  UPDATE servicos SET preco = 50.00, ilustrativo = FALSE, atualizado_em = v_now WHERE nome = 'Cabelo e barba' RETURNING id INTO v_combo;
  IF v_combo IS NULL THEN RAISE EXCEPTION 'Serviço "Cabelo e barba" não encontrado.'; END IF;

  -- Regras (simétricas): combo não combina com corte/infantil/barba; corte não combina com infantil; infantil não combina com barba.
  WITH p(x, y) AS (VALUES (v_combo, v_corte), (v_combo, v_inf), (v_combo, v_barba), (v_corte, v_inf), (v_inf, v_barba))
  INSERT INTO servico_exclusoes (servico_id, exclui_id)
  SELECT x, y FROM p UNION SELECT y, x FROM p
  ON CONFLICT DO NOTHING;
END $$;

COMMIT;

-- Conferência (mostra TODOS os serviços, inclusive os que o script não tocou, e agendamentos futuros que dependem de cada um):
SELECT s.id, s.nome, s.preco, s.duracao_minutos, s.ativo, s.ilustrativo,
       (SELECT count(*) FROM agendamentos a
         WHERE a.status IN ('SOLICITADO','CONFIRMADO') AND a.inicio > (now() AT TIME ZONE 'America/Sao_Paulo')
           AND EXISTS (SELECT 1 FROM agendamento_servicos x WHERE x.agendamento_id = a.id AND x.servico_id = s.id)) AS agendamentos_futuros
  FROM servicos s ORDER BY s.ativo DESC, s.id;
-- Regras ativas (rode à parte): SELECT a.nome, b.nome FROM servico_exclusoes e JOIN servicos a ON a.id=e.servico_id JOIN servicos b ON b.id=e.exclui_id WHERE a.ativo AND b.ativo ORDER BY 1,2;
