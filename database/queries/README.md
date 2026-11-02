# Consultas analíticas

Escritas em SQL **padrão** sobre as views de `database/analytics_views.sql`, para rodarem no PostgreSQL
(e, como as views isolam as funções específicas, também em SQLite nos testes automatizados).
O parâmetro `:agora` deve ser informado pelo cliente SQL (`psql -v agora="'2026-10-24 12:00:00'"`) ou pelo
pipeline (`analytics/src/run_sql.py`). Definições dos indicadores: `docs/metricas.md`.
