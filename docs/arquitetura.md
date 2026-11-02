# Arquitetura

```
Navegador (HTML/CSS/JS estático) ──REST/JSON──▶ Spring Boot (app/) ──JDBC──▶ PostgreSQL
                                                      │                        │
                                                 SMTP / modo dev          views analíticas (sem PII)
                                                                               │
                                              analytics/ (Python + Pandas) ──▶ CSV ──▶ Power BI (DAX)
```
Diagramas Mermaid em `docs/diagramas/`.

## Backend (`app/`, pacote `br.com.barbershopdogui`)
| Pacote | Conteúdo |
|---|---|
| `model` | Regras puras, sem framework: `BookingStatus`, `BookingRules` (transições, baixa, cancelamento), `AvailabilityCalculator` (horários livres), `TimeInterval`, `WorkWindow`. Testadas isoladamente. |
| `service` | `CatalogService`, `ScheduleService`, `AvailabilityService`, `BookingService` (reserva → solicitação → status), `CustomerAccessService` (link de uso único), `DashboardService`, `AdminUserService`, `HoldExpirationJob`, `RateLimiter`, `Tokens`. Acesso a dados com `JdbcTemplate` (SQL parametrizado). |
| `web` | `PublicController`, `CustomerController`, `AdminController`, `AuthController`, `ApiExceptionHandler`, `Dtos`. |
| `security`, `config` | `SecurityConfig`, contas administrativas (`AdminUserDetailsService`, `LoginAttempts`, `AdminAccountsInitializer`), relógio único e e-mail. |

Persistência: JDBC puro (decisão herdada do projeto original; adequada ao tamanho e à clareza do SQL). Fuso: tudo em `America/Sao_Paulo`, `TIMESTAMP` sem fuso.

## Frontend (`app/src/main/resources/static/`)
Páginas HTML reais (sem SPA): `index`, `servicos`, `agendar`, `consulta`, `privacidade`, `admin-login` e, em `admin/`, `painel`, `agenda`, `servicos`, `profissionais`, `horarios`, `usuarios`. JS puro (`common.js`, `agendar.js`, `consulta.js`, `admin.js`...). Sem scripts/estilos inline para permitir CSP restritiva.

## Decisões e limites
- Anti-conflito: restrição `EXCLUDE USING gist` no PostgreSQL + checagem amigável no serviço (ver `regras-de-agendamento.md`).
- Sessão de cliente: atributo de sessão HTTP criado só após consumir um token; sessão de admin: Spring Security (form login).
- Limitador de requisições em memória: válido para **uma** instância; com várias, usar Redis/gateway.
- Implantação via buildpack/JAR (ver `deploy.md`).
