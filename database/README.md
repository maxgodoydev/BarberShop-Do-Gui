# Banco de dados (PostgreSQL 15+)

Ordem de execução completa (banco vazio):
```bash
createdb barbershop_gui

# 1. Esquema base e views
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/schema.sql          # tabelas, índices, restrição anti-sobreposição
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/analytics_views.sql # views sem dados pessoais (BI/pipeline)

# 2. OPCIONAL: dados 100% fictícios para demonstração
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/seed_demo.sql

# 3. Migrações evolutivas obrigatórias (ordem estrita)
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/004_etapa4.sql
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/005_exclusao_e_supabase.sql
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/006_bi.sql
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/007_etapa4b.sql
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/008_perfis_balcao.sql
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/migracoes/009_regras_servicos.sql

# 4. OPCIONAL (só demo): produtos fictícios. Precisa rodar DEPOIS da 004, que cria a tabela produtos
psql -v ON_ERROR_STOP=1 -d barbershop_gui -f database/seed_demo_produtos.sql
```
Você também pode rodar tudo de forma automatizada pelo script oficial:
`database/aplicar.sh` (produção limpa) ou `database/aplicar.sh --demo` (com dados fictícios).
- `schema.sql` e `analytics_views.sql` são idempotentes (`IF NOT EXISTS` / `CREATE OR REPLACE`). `seed_demo.sql` usa `ON CONFLICT DO NOTHING`.
- Requer a extensão `btree_gist` (permitida em Supabase, Neon, Render e na maioria dos provedores; em PostgreSQL local pode exigir o pacote `postgresql-contrib`).
- Regenerar dados fictícios: `python3 scripts/gerar_dados_demo.py [--anchor AAAA-MM-DD]`.
- `queries/`: consultas analíticas (ver `queries/README.md`). `tests/verifica_restricoes.sql`: roteiro manual das restrições.
- Status: scripts **não executados em PostgreSQL real** neste ambiente (ver docs/testes.md).


## Supabase (projeto novo)
No painel do Supabase, abra **SQL Editor > New query** e execute os arquivos SQL na ordem acima:
`schema.sql`, `analytics_views.sql`, depois as migrações `004` a `009`.
Não execute `seed_demo.sql` nem `seed_demo_produtos.sql` em produção (são dados fictícios).
A migração `005_exclusao_e_supabase.sql` habilita RLS e revoga o acesso de `anon` e
`authenticated` às tabelas; este projeto acessa o PostgreSQL pelo backend Java (JDBC),
não diretamente pelo navegador. Portanto, configure o backend com a conexão PostgreSQL
do Supabase usando `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Use a string de conexão
**Session pooler** se o ambiente de hospedagem não suportar IPv6/conexão direta.
