-- Migração 010: troca obrigatória de senha + serviços complementares do catálogo.
-- Aplicar DEPOIS da 009 e ANTES de implantar a versão da aplicação que usa a coluna exige_troca_senha.
--
-- Segurança desta migração:
--  * Idempotente: pode ser executada mais de uma vez sem efeito colateral.
--  * NÃO sobrescreve nenhum serviço existente (preço, descrição, avaliação prévia e status ficam como estão;
--    ex.: o serviço 7 "Luzes" de produção não é tocado). Serviços que já existam por ID ou por nome são ignorados.
--  * NÃO cria profissionais nem vínculos em profissional_servicos: quem atende cada serviço é cadastrado
--    pelo proprietário em /admin/profissionais.html.
--  * Contas administrativas existentes continuam sem exigir troca (DEFAULT FALSE); só contas novas
--    ou redefinidas pelo proprietário passam a exigir.
BEGIN;

-- 1) Primeiro acesso com troca obrigatória de senha
ALTER TABLE usuarios_admin ADD COLUMN IF NOT EXISTS exige_troca_senha BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Novos serviços (IDs 8 a 13). ON CONFLICT DO NOTHING cobre conflito de ID e de nome.
INSERT INTO servicos (id, nome, descricao, preco, duracao_minutos, requer_avaliacao, ilustrativo, ativo, ordem) VALUES
  (8,  'Platinado ou descoloração', 'Descoloração global. Exige avaliação prévia.', 200.00, 150, TRUE,  TRUE, TRUE, 8),
  (9,  'Coloração ou pigmentação',  'Cor ou pigmentação. Exige avaliação prévia.',  70.00,  45, TRUE,  TRUE, TRUE, 9),
  (10, 'Sobrancelha',               'Design de sobrancelha.',                       20.00,  10, FALSE, TRUE, TRUE, 10),
  (11, 'Acabamento',                'Acabamento de contorno (pezinho).',            15.00,  10, FALSE, TRUE, TRUE, 11),
  (12, 'Hidratação',                'Hidratação capilar.',                          35.00,  20, FALSE, TRUE, TRUE, 12),
  (13, 'Lavagem',                   'Lavagem simples.',                             15.00,  10, FALSE, TRUE, TRUE, 13)
ON CONFLICT DO NOTHING;

-- 3) Alinha a sequência de IDs ao maior ID existente, para cadastros manuais não colidirem com 8..13.
SELECT setval(pg_get_serial_sequence('servicos', 'id'), (SELECT MAX(id) FROM servicos));

COMMIT;
