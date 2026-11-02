-- Produtos de DEMONSTRAÇÃO (fictícios; preços ilustrativos, validar com o Gui).
-- Depende da tabela `produtos`, criada em migracoes/004_etapa4.sql: por isso NÃO fica em seed_demo.sql
-- (que roda antes da 004). Aplicar somente em banco de demonstração, depois das migrações. Idempotente.
BEGIN;
INSERT INTO produtos (nome, marca, preco, estoque) VALUES
  ('Pomada modeladora', 'QOD Barber Shop', 34.00, 10),
  ('Pomada modeladora efeito seco', 'Fox For Men', 32.00, 10),
  ('Creme pós-barba', 'QOD Barber Shop', 32.00, 10),
  ('Loção pós-barba', 'Barba Brava', 29.00, 10)
ON CONFLICT (nome) DO NOTHING;
COMMIT;
