# Modelo de dados e dicionário

Esquema: `database/schema.sql`. Diagrama: `docs/diagramas/modelo-relacional.mmd`. Todos os `TIMESTAMP` são hora local (America/Sao_Paulo).

| Tabela (granularidade) | Colunas principais e significado |
|---|---|
| `servicos` (1 linha por serviço) | `preco` = preço de **tabela**; `duracao_minutos`; `requer_avaliacao` (procedimento químico); `ilustrativo` = valor ainda não validado; `ativo`. |
| `profissionais` | `nome`, `ativo`, `ordem`. |
| `profissional_servicos` (PK composta) | Quais serviços cada profissional executa. |
| `horarios_atendimento` | `dia_semana` ISO 1–7, `hora_inicio`, `hora_fim`; pausa = duas janelas no mesmo dia. |
| `bloqueios` | `profissional_id` (NULL = loja inteira), `inicio`, `fim`, `motivo`. |
| `clientes` | `nome`, `email` (único, sem diferenciar maiúsculas), `telefone` opcional. Sem senha. |
| `agendamentos` (1 linha por reserva/pedido) | `cliente_id` (NULL só em RESERVA_TEMPORARIA/EXPIRADO), `servico_id`, `profissional_id`, `inicio`, `fim`, `duracao_minutos` e `preco_referencia` (cópias na data do pedido; **não é pagamento**), `status`, `cancelado_por`, `expira_em`, `reserva_token_hash`. |
| `tokens_acesso` | Hash SHA-256 do link do cliente, `expira_em`, `usado_em` (uso único). |
| `usuarios_admin` | `papel` PROPRIETARIO/TECNICO, hash BCrypt, `falhas_login`, `bloqueado_ate`. |

Integridade: FKs com `RESTRICT` onde há histórico; checks de domínio (status, períodos, cliente obrigatório em pedidos); restrição `EXCLUDE` anti-sobreposição; índices por data/status/cliente/expiração.

Views analíticas (`database/analytics_views.sql`), **sem dados pessoais**: `vw_fato_agendamentos` (1 linha por agendamento, com `dia_semana_iso`, `hora_inicio`, `ano_mes`, `antecedencia_horas`), `vw_dim_servico`, `vw_dim_profissional`, `vw_dim_calendario`.

Limitações dos dados: não há pagamentos, então **não existe receita medida**; não há registro de "não compareceu"; o `preco_referencia` é de tabela. Pedidos antigos sem baixa ficam como CONFIRMADO/SOLICITADO (aparecem como pendência de qualidade).
