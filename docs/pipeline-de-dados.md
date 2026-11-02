# Pipeline de dados (`analytics/`)

```bash
cd analytics
pip install -r requirements.txt                      # pandas, numpy (psycopg/pyarrow são opcionais)
python -m barbershop_analytics --input sample_data --output output --agora 2026-10-24T12:00:00     # CSV (demonstração)
ANALYTICS_DB_URL=postgresql://analytics_ro:***@host/db python -m barbershop_analytics --source postgres --output output --parquet
python -m unittest discover -s tests -v              # testes
```
Etapas: ler (CSV ou views) → converter tipos → validar → separar rejeitadas → calcular → exportar → relatório de qualidade.

Regras de qualidade (`quality.py`): R01 obrigatório ausente (erro) · R02 status inválido (erro) · R03 fim ≤ início (erro) · R04 ID duplicado (erro) · R05/R06 serviço/profissional órfão (erro) · R07 duração inconsistente (aviso) · R08 cancelado sem origem (aviso) · R09 vencido sem desfecho (aviso) · R10 criado após o início (aviso) · R11 sobreposição ativa do mesmo profissional (crítico; linhas mantidas p/ auditoria).
Erros removem a linha do conjunto analítico (IDs ficam em `rejeitados_ids.csv`). Valores ausentes: `cancelado_por` vazio vira "NAO_INFORMADO" apenas em cancelamentos.
Saídas em `output/`: `fato_agendamentos_tratado.csv`, `agg_*.csv`, `dim_*.csv`, `kpis.json`, `quality_report.json|md` (+ `.parquet` com `--parquet`). Nenhum arquivo contém dados pessoais. Base vazia → arquivos vazios e KPIs nulos, sem falha.
Reconciliação: `tests/test_pipeline.py` roda as consultas de `database/queries/` (via SQLite sobre os mesmos CSVs) e compara com o Pandas.
