"""Definições de negócio compartilhadas (devem espelhar docs/metricas.md e database/queries/)."""
STATUS_VALIDOS = ["RESERVA_TEMPORARIA", "SOLICITADO", "CONFIRMADO", "CONCLUIDO", "CANCELADO", "EXPIRADO", "FALTOU"]
# Pedido = solicitação efetivamente enviada pelo cliente (inclusive faltas/atrasos). Reserva temporária e expirada medem funil, não demanda.
PEDIDO = ["SOLICITADO", "CONFIRMADO", "CONCLUIDO", "CANCELADO", "FALTOU"]
DEMANDA_NAO_CANCELADA = ["SOLICITADO", "CONFIRMADO", "CONCLUIDO"]
OCUPAM_AGENDA = ["RESERVA_TEMPORARIA", "SOLICITADO", "CONFIRMADO", "CONCLUIDO"]
COLUNAS_OBRIGATORIAS = ["agendamento_id", "servico_id", "profissional_id", "inicio", "fim", "status", "criado_em"]
COLUNAS_FATO = ["agendamento_id", "servico_id", "profissional_id", "data_inicio", "inicio", "fim", "dia_semana_iso", "hora_inicio", "ano_mes",
                "duracao_minutos", "preco_referencia", "status", "cancelado_por", "criado_em", "data_criacao", "antecedencia_horas"]
