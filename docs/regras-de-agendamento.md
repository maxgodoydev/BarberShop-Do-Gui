# Regras de agendamento

## Estados
`RESERVA_TEMPORARIA` (5 min, sem dados do cliente) → `SOLICITADO` (cliente enviou; **ainda não confirmado**) → `CONFIRMADO` (equipe) → `CONCLUIDO` (baixa, só após o horário começar).
`CANCELADO` (por `CLIENTE` ou `EQUIPE`) pode vir de SOLICITADO ou CONFIRMADO. `EXPIRADO` = reserva temporária vencida.
Transições: `RESERVA_TEMPORARIA→SOLICITADO|EXPIRADO`, `SOLICITADO→CONFIRMADO|CANCELADO`, `CONFIRMADO→CONCLUIDO|CANCELADO`. Estados finais não mudam.
Confirmar exige horário futuro; concluir exige horário já iniciado.

## Disponibilidade
`livre = janelas do profissional no dia − bloqueios (dele ou da loja) − agendamentos ativos − reservas temporárias não expiradas`.
Um início só é oferecido se **todo o intervalo** `[início, início+duração)` cabe numa janela (não atravessa pausa nem fim do expediente) e não se sobrepõe a nada. Passo padrão: 15 min; antecedência mínima: 60 min; horizonte: 60 dias (configuráveis).
Intervalos são semiabertos: um serviço que termina às 10:50 não bloqueia outro que começa às 10:50.

## Concorrência
1. `POST /api/public/holds` recalcula a disponibilidade no servidor e insere a reserva.
2. O PostgreSQL garante a exclusão: `EXCLUDE USING gist (profissional_id WITH =, tsrange(inicio,fim,'[)') WITH &&) WHERE status IN (RESERVA_TEMPORARIA, SOLICITADO, CONFIRMADO, CONCLUIDO)`. Duas requisições simultâneas para intervalos que se sobrepõem: uma vence; a outra recebe erro `23P01` convertido em HTTP 409.
3. Reservas vencidas deixam de contar na disponibilidade imediatamente (`expira_em`), são liberadas no momento do próximo pedido do mesmo profissional e varridas a cada 60 s (`HoldExpirationJob`).
4. O envio (`POST /api/public/bookings`) exige o token da reserva (guardado como hash), usa `SELECT ... FOR UPDATE` e falha com 410 se expirou.

## Limitações conhecidas
- Bloqueios e janelas são checados na aplicação, não por restrição do banco; criar um bloqueio sobre agendamentos existentes **não os cancela** (o sistema informa quantos foram afetados).
- Quem digita o e-mail de outra pessoa pode criar um pedido em nome dela (o e-mail não é verificado antes do pedido); a equipe confirma manualmente.
- Teste de concorrência real (várias conexões) **não foi executado** neste ambiente.

## Fluxo do pop-up (etapa 4b)
1. **Serviços** (até 3, máx. 200 min, sem repetir nem combinar itens de `servico_exclusoes`) + alergia opcional.
2. **Profissional, dia e hora**: só quem executa *todos* os serviços; duração = soma; agenda semanal (próxima semana só se a atual lotar). Reserva temporária de 10 min (`app.booking.hold-minutes`).
3. **Produtos** (opcional): reserva para retirar e pagar na loja.
4. **Dados**: nome, sobrenome, e-mail, telefone (opcional). 5. **Privacidade/LGPD** (aceite obrigatório). 6. **Resumo** e envio.
7. **Link mágico**: pedido fica `SOLICITADO` e ocupa a agenda por 30 min (`app.booking.confirm-minutes`); ao abrir `/confirmar.html#token=…` vira `CONFIRMADO`. Sem confirmar, vira `EXPIRADO` e o horário é liberado.
- **Fila anti-duplicidade**: `pg_advisory_xact_lock(profissional)` serializa pedidos simultâneos do mesmo profissional; a restrição `EXCLUDE` do banco é a garantia final.
- Cancelamento online até **24 h** antes (`app.booking.cancel-min-hours`). Falta/atraso > 30 min (`FALTOU`, marcado pela equipe) bloqueia o cadastro; só a equipe/dono libera. Exclusão do cadastro só sem agendamentos ativos.
- Máx. 3 profissionais ativos. Alergia é dado de saúde: apagada na exclusão do cadastro (`migracoes/007_etapa4b.sql`).


## Atualização da etapa 5 (vale sobre trechos antigos acima)
- **Link mágico único:** o e-mail enviado após o pedido confirma o horário (SOLICITADO → CONFIRMADO) **e** abre o espaço do cliente sem senha. Cliente que já existe pede acesso em "Meu espaço". O link de acesso (15 min) e o de confirmação (30 min) são independentes: pedir um não invalida o outro.
- **Reserva temporária:** 10 minutos. **Cancelamento online:** até 24 h antes.
- **Balcão:** recepção ou proprietário agendam para quem está na loja, inclusive sem e-mail. O agendamento nasce CONFIRMADO, com origem BALCAO e o usuário registrado; não exige a antecedência mínima de 60 min do site.
- **Falta/atraso:** só pode ser registrado (FALTOU) depois de 30 min do horário marcado; o cadastro do cliente é bloqueado e só proprietário/técnica liberam.
- **Estoque:** baixa automática dos produtos reservados quando o atendimento é CONCLUIDO; recepção e proprietário ajustam a quantidade; produto "acabando" quando estoque ≤ mínimo.
- **BI:** a demanda por serviço conta **todos** os serviços de cada agendamento (tabela `agendamento_servicos`; view `vw_fato_agendamento_servicos`).
- **Auditoria:** bloqueio, liberação, exclusão de cliente, mudanças de status, agendamentos de balcão e ajustes de estoque ficam registrados (só ids, sem dados pessoais).
