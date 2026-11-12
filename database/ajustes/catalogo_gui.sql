-- AJUSTE DE CATÁLOGO DA BARBEARIA DO GUI  (OPT-IN: NÃO faz parte de aplicar.sh nem das migrações automáticas)
--
-- Decisões do proprietário (nov/2026):
--   * "Degradê" e "Corte tradicional" são o mesmo serviço  -> vira "Corte", R$ 35,00, 40 min (id 1).
--   * Sem "Corte na máquina"                               -> desativado (id 4).
--   * Combo "Cabelo e barba" mantido                       -> R$ 50,00 (corte 35 + barba 20 - 5).  (proposta: valor do combo delegado pelo proprietário)
--   * Barba                                                -> R$ 20,00.                              (proprietário: "uns 20 reais")
--
-- Segurança:
--   * NUNCA apaga nem renumera serviços: o histórico (agendamentos, BI) depende dos IDs. Duplicados são DESATIVADOS.
--   * Cada UPDATE confere id E nome atual; se o banco tiver outra coisa naquele id, não toca nada.
--   * Idempotente: pode rodar mais de uma vez. Só afeta agendamentos NOVOS; agendamentos existentes mantêm o
--     preco_referencia/duração gravados na data do pedido (a receita histórica do BI não muda).
--   * Alternativa sem SQL: fazer o mesmo pela tela Serviços (como proprietário).
-- Uso (produção: só depois de BACKUP, no SQL Editor do Supabase; local: psql -f database/ajustes/catalogo_gui.sql)
BEGIN;

UPDATE servicos
   SET nome = 'Corte',
       descricao = 'Corte tradicional ou degradê, na tesoura e na máquina.',
       preco = 35.00,
       duracao_minutos = 40
 WHERE id = 1 AND nome IN ('Corte tradicional', 'Corte');

UPDATE servicos SET ativo = FALSE
 WHERE (id = 2 AND nome = 'Degradê') OR (id = 4 AND nome = 'Corte na máquina');

UPDATE servicos SET preco = 20.00 WHERE id = 5 AND nome = 'Barba';
UPDATE servicos SET preco = 50.00 WHERE id = 6 AND nome = 'Cabelo e barba';

-- Conferência: deve mostrar 1 Corte 35,00 40 ativo | 2 Degradê inativo | 4 Corte na máquina inativo | 5 Barba 20,00 | 6 combo 50,00
SELECT id, nome, preco, duracao_minutos, ativo FROM servicos WHERE id IN (1, 2, 4, 5, 6) ORDER BY id;
COMMIT;
