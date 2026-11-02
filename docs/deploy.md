# Deploy (preparação — nenhum deploy foi feito)
Opção sugerida: banco **PostgreSQL gerenciado** (ex.: Supabase/Neon) + aplicação Java em plataforma com Maven/JDK 21 (ex.: Render, Railway). Para Supabase, veja também [`supabase.md`](supabase.md).

1. **Crie o banco** e aplique os scripts **nesta ordem** (a mesma de `database/aplicar.sh`, que a CI também usa):
   `schema.sql` → `analytics_views.sql` → `migracoes/004_etapa4.sql` → `005_exclusao_e_supabase.sql` → `006_bi.sql` → `007_etapa4b.sql` → `008_perfis_balcao.sql`.
   - Com `psql` e as variáveis `PG*` apontando para o banco: `database/aplicar.sh` (produção) ou `database/aplicar.sh --demo` (inclui `seed_demo.sql`, **só em demonstração**; o seed entra antes das migrações).
   - **Sem as migrações 004–008 o app não funciona** (faltam `agendamento_servicos`, `auditoria`, o perfil `RECEPCAO`, a função de BI etc.). Todas são idempotentes.
2. **Usuário do app = dono das tabelas.** A migração 005 liga RLS em todas as tabelas, sem políticas; um usuário que **não** seja o dono enxergaria zero linhas. Crie à parte apenas o `analytics_ro` somente leitura nas views (para o Power BI/Python).
3. Build: `cd app && mvn -DskipTests package` → `target/barbershop-do-gui.jar`; start: `java -jar target/barbershop-do-gui.jar`.
4. **Ative o perfil de produção:** `SPRING_PROFILES_ACTIVE=prod`. Ele força cookie de sessão `Secure` e `forward-headers-strategy=framework` (IP real atrás do proxy, necessário para o limite de tentativas). Sem o perfil, os padrões são os de uso local.
5. Variáveis (ver `.env.example`): `DB_URL` (JDBC da plataforma, com `?sslmode=require`), `DB_USERNAME`, `DB_PASSWORD`, `PUBLIC_BASE_URL` (https), `BOOTSTRAP_OWNER_*`, `MAIL_MODE=smtp` + `MAIL_*`, `SHOP_*`.
6. Health check: `GET /api/public/health`. Depois do primeiro login remova `BOOTSTRAP_OWNER_PASSWORD` e crie as contas de recepção/técnica em *Usuários*.
7. Em produção: HTTPS obrigatório, backups do banco, **não** aplicar `seed_demo.sql`, não usar dados reais em ambiente de demonstração.
Obs.: a plataforma pode precisar da propriedade `java.runtime.version=21` (`system.properties`, já incluída).
