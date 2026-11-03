# Banco no Supabase (PostgreSQL gerenciado)

> **Status:** os scripts foram testados em PostgreSQL 16 local. **Não foram testados em um projeto Supabase real.** Siga os passos e, se algo falhar, envie a mensagem de erro.

## 1. Criar o projeto
Região **São Paulo (sa-east-1)** (menor latência). Guarde a senha do banco.

## 2. Criar as tabelas (SQL Editor, nesta ordem)
1. `database/schema.sql`
2. `database/analytics_views.sql`
3. `database/migracoes/004_etapa4.sql`
4. `database/migracoes/005_exclusao_e_supabase.sql`
5. `database/migracoes/006_bi.sql`
6. `database/migracoes/007_etapa4b.sql`
7. `database/migracoes/008_perfis_balcao.sql`
8. `database/migracoes/009_regras_servicos.sql`

Alternativa fora do SQL Editor: com as variáveis `PG*` do pooler, `database/aplicar.sh` aplica tudo na ordem certa (sem o seed).

**Não rode** `seed_demo.sql` em produção (dados fictícios). Cadastre serviços e profissionais reais pelo painel admin.

## 3. Conectar o app (variáveis de ambiente do deploy)
No Supabase: *Connect → Session pooler* (funciona em IPv4). Copie host e usuário de lá; o formato é:
```
DB_URL=jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<ref-do-projeto>
DB_PASSWORD=<senha do banco>
DB_POOL_SIZE=5
```
- Use o **Session pooler** (porta 5432). O *Transaction pooler* (6543) exige `&prepareThreshold=0` na URL.
- O app conecta com o usuário **dono das tabelas** (`postgres...`). **Não crie outro usuário para o app** sem criar políticas de RLS: com a RLS ligada ele enxergaria zero linhas.

## 4. Segurança (já feita pela migração 005)
O Supabase expõe o schema `public` numa API REST pública (chaves `anon`/`authenticated`). Este app **não usa** essa API, então a migração:
- liga **RLS em todas as tabelas** (sem políticas = ninguém lê pela API);
- tira acesso de `anon`/`authenticated` a tabelas, views e funções (inclusive as futuras);
- **Nunca** coloque a *service_role key* no front-end nem no repositório. O app não precisa de nenhuma chave do Supabase, só da senha do banco.

Conferir no painel do Supabase: *Authentication → Policies* (RLS ativada) e *Advisors → Security* (sem alertas críticos).

## 5. Python / BI (somente leitura, restrito)
```sql
CREATE ROLE analytics_ro LOGIN PASSWORD '<senha forte>';
GRANT USAGE ON SCHEMA public TO analytics_ro;
GRANT SELECT ON vw_fato_agendamentos, vw_dim_servico, vw_dim_profissional, vw_dim_calendario TO analytics_ro;
```
Esse papel lê **só as views** (sem nome, e-mail nem telefone). Use-o em `ANALYTICS_DB_URL`. Quem acessa o BI: administrador geral e equipe de TI.

## 6. Backup e custos
O plano gratuito pausa projetos inativos e tem backups limitados: exporte o banco (`pg_dump`) antes de apresentações e guarde fora do Supabase.
