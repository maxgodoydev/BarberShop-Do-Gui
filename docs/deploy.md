# Deploy (preparação — nenhum deploy foi feito)
Opção sugerida: banco **PostgreSQL gerenciado** (ex.: Supabase/Neon) + aplicação Java em plataforma com Maven/JDK 21 (ex.: Render, Railway). Para Supabase, veja também [`supabase.md`](supabase.md).

1. **Crie o banco** e aplique os scripts **nesta ordem** (a mesma de `database/aplicar.sh`, que a CI também usa):
   `schema.sql` → `analytics_views.sql` → `migracoes/004_etapa4.sql` → `005_exclusao_e_supabase.sql` → `006_bi.sql` → `007_etapa4b.sql` → `008_perfis_balcao.sql` → `009_regras_servicos.sql` → `010_recuperacao_servicos_e_senhas.sql`.
   - Com `psql` e as variáveis `PG*` apontando para o banco: `database/aplicar.sh` (produção) ou `database/aplicar.sh --demo` (inclui `seed_demo.sql`, **só em demonstração**; o seed entra antes das migrações).
   - **Sem as migrações 004–010 o app não funciona** (faltam `agendamento_servicos`, `auditoria`, o perfil `RECEPCAO`, a função de BI, integridade dos serviços etc.). Todas são idempotentes.
2. **Usuário do app = dono das tabelas.** A migração 005 liga RLS em todas as tabelas, sem políticas; um usuário que **não** seja o dono enxergaria zero linhas. Crie à parte apenas o `analytics_ro` somente leitura nas views (para o Power BI/Python).
3. Build: `cd app && mvn -DskipTests package` → `target/barbershop-do-gui.jar`; start: `java -jar target/barbershop-do-gui.jar`.
4. **Ative o perfil de produção:** `SPRING_PROFILES_ACTIVE=prod`. Ele força cookie de sessão `Secure` e `forward-headers-strategy=framework` (IP real atrás do proxy, necessário para o limite de tentativas). Sem o perfil, os padrões são os de uso local.
5. Variáveis (ver `.env.example`): `DB_URL` (JDBC da plataforma, com `?sslmode=require`), `DB_USERNAME`, `DB_PASSWORD`, `PUBLIC_BASE_URL` (https), `BOOTSTRAP_OWNER_*`, `MAIL_MODE=smtp` + `MAIL_*`, `SHOP_*`.
6. Health check: `GET /api/public/health`. Depois do primeiro login remova `BOOTSTRAP_OWNER_PASSWORD` e crie as contas de recepção/técnica em *Usuários*.
7. Em produção: HTTPS obrigatório, backups do banco, **não** aplicar `seed_demo.sql`, não usar dados reais em ambiente de demonstração.
Obs.: a plataforma pode precisar da propriedade `java.runtime.version=21` (`system.properties`, já incluída).

## Atualização de uma produção existente para a migração 010 (troca obrigatória de senha)
Ordem obrigatória: **banco primeiro, aplicação depois** (a nova versão lê a coluna `exige_troca_senha`; sem ela, o login administrativo falha).
1. Faça um backup/snapshot do banco (Supabase: *Database → Backups*).
2. No *SQL Editor* do Supabase, execute o conteúdo de `database/migracoes/010_recuperacao_servicos_e_senhas.sql`. Ela é idempotente e **não altera** serviços existentes nem cria profissionais/vínculos.
3. Confira: `SELECT column_name FROM information_schema.columns WHERE table_name='usuarios_admin' AND column_name='exige_troca_senha';` (1 linha) e `SELECT id, nome, preco FROM servicos ORDER BY id;` (IDs 1–13, serviços 1–7 inalterados).
4. Faça o deploy da nova versão (Render) com `SPRING_PROFILES_ACTIVE=prod` (o `Dockerfile` não define o perfil; sem a variável o cookie de sessão não é `Secure`).
5. Teste em produção: login do proprietário; criar conta de recepção; primeiro login dela deve exigir a troca; após trocar, o painel é liberado.
6. Cadastre os vínculos de serviço de cada profissional em *Profissionais* (a migração não faz isso de propósito).
Reversão: a aplicação anterior continua funcionando com a coluna nova (ela é só adicionada), então voltar o deploy não exige desfazer o SQL.

## Ajuste de catálogo (opcional, sempre depois do passo 010 e com backup)
`database/ajustes/catalogo_gui.sql` **não** faz parte de `aplicar.sh` nem das migrações: é uma decisão de negócio, aplicada por quem tem autoridade sobre o preço. Efeito: unifica *Corte tradicional* e *Degradê* em **Corte** (R$ 35, 40 min, id 1), **desativa** *Degradê* (id 2) e *Corte na máquina* (id 4), Barba R$ 20 e *Cabelo e barba* R$ 50 (Barba segundo o proprietário; o combo é proposta, pois ele delegou o valor). Nunca apaga serviço; cada `UPDATE` confere id **e** nome atual, então não toca em um id que tenha outro conteúdo; é idempotente. Agendamentos já gravados mantêm o `preco_referencia` da data (a receita histórica do BI não muda). O mesmo resultado se obtém pela tela *Serviços* (como proprietário), sem SQL.
