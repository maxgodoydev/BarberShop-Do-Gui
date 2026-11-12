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

## Automação e paridade
- **Execução agendada:** `.github/workflows/analytics.yml` roda o ETL toda segunda-feira (e sob demanda) e publica `kpis.json` e o relatório de qualidade como artefato. Sem o secret `ANALYTICS_DB_URL` usa o CSV de demonstração; com o secret (usuário somente leitura `analytics_ro`, procedimento em `docs/supabase.md`) lê as views do PostgreSQL. Decisões assumidas: carga **completa** a cada execução (sem incremental; o volume não justifica) e **sem data warehouse** (a saída são arquivos).
- **Paridade BI x pipeline:** `scripts/paridade_bi.py` compara, no mesmo banco e na mesma janela, a função SQL `fn_bi_resumo` (tela de BI) com o pipeline Python: pedidos, atendimentos concluídos, taxa de cancelamento e pedidos por dia da semana. Roda na CI (`verify.yml`) e sai com código 1 se divergir. Receita fica de fora de propósito: o pipeline recusa calcular receita (preço de tabela não é pagamento), enquanto o BI exibe a soma do preço de tabela dos concluídos, rotulada como *estimada*.
