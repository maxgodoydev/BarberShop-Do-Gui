#!/usr/bin/env bash
# Linux/macOS. Requer JDK 21, Maven, PostgreSQL 15+ (psql). Não testado em máquina real.
set -euo pipefail
for c in java mvn psql; do command -v $c >/dev/null || { echo "Instale '$c'"; exit 1; }; done
read -rp "Usuário PostgreSQL [postgres]: " PGU; PGU=${PGU:-postgres}; read -rsp "Senha PostgreSQL: " PGPASSWORD; echo; export PGPASSWORD
DB=barbershop_gui
psql -h localhost -U "$PGU" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB'" | grep -q 1 || psql -h localhost -U "$PGU" -d postgres -c "CREATE DATABASE $DB"
for f in schema analytics_views seed_demo migracoes/004_etapa4 migracoes/005_exclusao_e_supabase migracoes/006_bi migracoes/007_etapa4b migracoes/008_perfis_balcao migracoes/009_regras_servicos seed_demo_produtos; do psql -h localhost -U "$PGU" -d $DB -v ON_ERROR_STOP=1 -f database/$f.sql; done
export DB_URL=jdbc:postgresql://localhost:5432/$DB DB_USERNAME=$PGU DB_PASSWORD=$PGPASSWORD
read -rp "Usuário do proprietário: " BOOTSTRAP_OWNER_USERNAME; read -rsp "Senha (12+): " BOOTSTRAP_OWNER_PASSWORD; echo; export BOOTSTRAP_OWNER_USERNAME BOOTSTRAP_OWNER_PASSWORD
cd app && mvn spring-boot:run
