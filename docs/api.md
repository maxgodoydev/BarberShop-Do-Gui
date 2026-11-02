# API REST (JSON)
Escritas exigem cabeçalho CSRF: obtenha `GET /api/csrf` → `{headerName, token}` e envie `headerName: token` (o cookie `XSRF-TOKEN` é definido junto).

**Público**
| Método e caminho | Descrição |
|---|---|
| GET `/api/public/config` | Dados públicos configurados (só campos preenchidos). |
| GET `/api/public/services` · `/api/public/professionals` | Catálogo ativo. |
| GET `/api/public/availability?servicoId&data[&profissionalId]` | Horários livres por profissional. |
| POST `/api/public/holds` | Reserva 5 min `{servicoId, profissionalId, inicio}` → `{reservaId, token, expiraEm}`; 409 se indisponível. |
| POST `/api/public/bookings` | Envia pedido `{reservaId, token, nome, email, telefone?, observacao?, aceitePrivacidade}` → status SOLICITADO; 410 se expirou. |
| POST `/api/public/access-link` | `{email}` → resposta genérica. |
| GET `/api/public/health` | Saúde (inclui banco). |

**Cliente (sessão por link):** POST `/api/cliente/sessao {token}` · GET `/api/cliente/agendamentos` · POST `/api/cliente/agendamentos/{id}/cancelar` · POST `/api/cliente/sair`.
**Autenticação:** POST `/api/auth/login` (form `username`,`password`) · POST `/api/auth/logout` · GET `/api/auth/me` · POST `/api/auth/senha`.
**Admin (PROPRIETARIO)**: `/api/admin/agendamentos` (GET filtros `data,profissionalId,servicoId,status`; PATCH `/{id}/status`), `/servicos` (GET/POST/PUT/PATCH ativo), `/profissionais` (+ `/{id}/horarios` GET/PUT), `/bloqueios` (GET/POST/DELETE), `/usuarios` (GET/POST técnico/PATCH ativo).
**Admin (PROPRIETARIO e TECNICO):** GET `/api/admin/dashboard`, GET `/api/admin/export/agendamentos.csv`, GET `/api/admin/servicos`, GET `/api/admin/profissionais`.
Erros: `{mensagem, erros?}` com 400/401/403/404/409/410/429/503.

## Novos endpoints (etapa 4b)
- `GET /api/public/availability?servicoIds=1,2&data=AAAA-MM-DD` — vários serviços (substitui `servicoId`).
- `GET /api/public/products` · `GET /api/public/booking-rules` (limites e exclusões).
- `POST /api/public/holds` `{servicoIds[], profissionalId, inicio}` · `POST /api/public/holds/release` `{reservaId, token}`.
- `POST /api/public/bookings` `{reservaId, token, nome, sobrenome, email, telefone?, alergias?, produtos[{produtoId,quantidade}], aceitePrivacidade}`.
- `POST /api/public/confirm` `{token}` — link mágico do e-mail.
