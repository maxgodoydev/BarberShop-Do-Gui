#!/usr/bin/env bash
# Aplica o banco NA ORDEM CORRETA (fonte única da ordem para CI, scripts locais e docs).
# Uso (a partir da raiz do projeto):  database/aplicar.sh [--demo]
#   --demo  inclui seed_demo.sql (dados FICTÍCIOS; nunca em produção). O seed vem ANTES das migrações
#           porque a 004 preenche agendamento_servicos a partir do que já existe.
# Conexão: variáveis padrão do libpq (PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE).
set -euo pipefail
cd "$(dirname "$0")"
ORDEM=(schema.sql analytics_views.sql)
[[ "${1:-}" == "--demo" ]] && ORDEM+=(seed_demo.sql)
ORDEM+=(migracoes/004_etapa4.sql migracoes/005_exclusao_e_supabase.sql migracoes/006_bi.sql migracoes/007_etapa4b.sql migracoes/008_perfis_balcao.sql migracoes/009_regras_servicos.sql migracoes/010_recuperacao_servicos_e_senhas.sql migracoes/011_lgpd_alergias.sql)
# Produtos de demo dependem da tabela criada na 004, então só entram DEPOIS das migrações.
[[ "${1:-}" == "--demo" ]] && ORDEM+=(seed_demo_produtos.sql)
for f in "${ORDEM[@]}"; do
  echo ">> $f"
  psql -X -q -v ON_ERROR_STOP=1 -f "$f"
done
echo "Banco pronto."
