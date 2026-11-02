# Power BI — modelo, relacionamentos e layout

> **Estado:** não foi produzido arquivo `.pbix`. Não havia Power BI Desktop neste ambiente, então nada abaixo foi aberto ou validado
> no Power BI. Siga este roteiro para montar o relatório (≈1 h) e valide as medidas contra `analytics/output/kpis.json`.

## 1. Fontes e carga
1. Rode o pipeline: `cd analytics && python -m barbershop_analytics --input sample_data --output output --agora 2026-10-24T12:00:00`.
2. No Power BI Desktop: *Obter dados → Texto/CSV* (ou use `power_query.m`) e carregue de `analytics/output/`:
   `fato_agendamentos_tratado.csv` (renomeie a tabela para **Fato**), `dim_servico.csv`, `dim_profissional.csv`, `dim_calendario.csv`.
3. Crie a tabela **dim_status** (Inserir dados): `status`, `ordem`, `grupo` — RESERVA_TEMPORARIA(1,Funil), EXPIRADO(2,Funil), SOLICITADO(3,Pedido), CONFIRMADO(4,Pedido), CONCLUIDO(5,Pedido), CANCELADO(6,Pedido).
4. Marque `dim_calendario` como *tabela de datas* (coluna `data`).

## 2. Modelo (esquema estrela, granularidade = 1 linha por agendamento)
| De (1) | Para (*) | Chave | Filtro |
|---|---|---|---|
| dim_servico | Fato | servico_id | único (dim → fato) |
| dim_profissional | Fato | profissional_id | único |
| dim_calendario | Fato | data → data_inicio | único |
| dim_status | Fato | status | único |

Sem relacionamentos muitos-para-muitos nem filtro bidirecional. Dados pessoais **não** entram no modelo.

## 3. Medidas
Todas em `medidas.dax`. Crie uma tabela vazia "Medidas" para organizá-las. Formatos: contagens inteiras; taxas em % com 1 casa.

## 4. Páginas
**P1 — Visão geral:** cartões Pedidos, Concluídos, Taxa de Cancelamento; barras *Demanda por serviço* (`Demanda Nao Cancelada` × dim_servico[servico]); linha mensal (`Pedidos`, `Concluidos`, `Cancelados` × `ano_mes`); segmentações de período (dim_calendario) e profissional.

**P2 — Demanda e operação:** matriz dia da semana × hora (`Demanda Nao Cancelada`, mapa de calor condicional); barras por profissional com `Participacao Demanda Profissional`; colunas agrupadas *Pedidos vs Concluídos* por mês.

**P3 — Qualidade e cancelamentos:** rosca de status (dim_status); barras de cancelamentos por origem (`cancelado_por`, vazio = "Não informado"); cartões `Vencidos Sem Desfecho`, `Taxa Abandono Reserva`, `Cancelamentos Sem Origem`; tabela com `quality_report.csv` se importado.

Regras visuais: fundo preto/grafite, destaques vermelho (#C8102E) e azul (#2F6FED), texto branco; unidades sempre no título ("pedidos", "%"). **Não** criar visuais de receita/faturamento: preço de tabela não é pagamento.

## 5. Conferência obrigatória (reconciliação)
Com `--agora 2026-10-24T12:00:00` e os dados de demonstração, os valores esperados estão em `analytics/output/kpis.json`
(Pedidos 1606 · Concluídos 1068 · Cancelados 232 · Taxa de cancelamento 14,4% · Taxa de conclusão 77,7%). Defina `Agora Referencia` igual a essa data para comparar.

## 6. Atualização
Rode o pipeline com `--source postgres` (variável `ANALYTICS_DB_URL` de um usuário somente leitura) e use *Atualizar* no Power BI. Para publicar no Power BI Service, configure um gateway se a fonte for local.
