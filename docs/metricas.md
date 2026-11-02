# Métricas e definições

| Indicador | Definição (numerador ÷ denominador) | Observação |
|---|---|---|
| Pedidos | status ∈ {SOLICITADO, CONFIRMADO, CONCLUIDO, CANCELADO} | Reserva temporária e expirada **não** são pedidos. |
| Demanda não cancelada | status ∈ {SOLICITADO, CONFIRMADO, CONCLUIDO} | Procura, não atendimento realizado. |
| Concluídos | status = CONCLUIDO | Único estado que indica atendimento realizado. |
| Taxa de cancelamento | CANCELADO ÷ pedidos | Inclui cancelamento por cliente e equipe. |
| Taxa de conclusão | CONCLUIDO ÷ pedidos com `inicio` < agora | Ignora pedidos futuros. |
| Taxa de abandono de reserva | EXPIRADO ÷ (EXPIRADO + pedidos) | Mede desistência no funil. |
| Vencidos sem desfecho | SOLICITADO/CONFIRMADO com `inicio` < agora | Gargalo operacional (falta resposta ou baixa). |

**Financeiro:** receita, lucro e ticket médio **não são calculados**. O preço cadastrado não prova pagamento. Só cabe falar em receita quando houver registro de pagamentos. O painel e o BI mostram, no máximo, "valor de tabela (estimativa)".
As mesmas definições estão em `database/queries/`, `analytics/barbershop_analytics/metrics.py`, `DashboardService.java` e `analytics/powerbi/medidas.dax`.


> Etapa 5: os valores em R$ do BI e da tela Equipe são o **preço de tabela dos atendimentos concluídos** (estimativa), pois o sistema não registra pagamentos. Só proprietário vê valores; a recepção vê quantidades.
