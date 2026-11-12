# Relatório de testes

**Ambiente de geração (24/10/2026):** OpenJDK 21 (somente JRE + módulo `jdk.compiler`), Python 3.12 (pandas 3.0, numpy), Node 22, SQLite 3.45. **Sem** Maven, sem acesso à internet, **sem PostgreSQL**, sem navegador.

## v13 — correções da sessão 3 (06/11/2026)

**Verificado de verdade (PostgreSQL 16 + Python 3.12 + Node 22):**
- `aplicar.sh --demo` 2x e `verifica_banco.py` (17/17) antes e depois das mudanças; reaplicação **sobre o banco já ajustado** (004 com `ON CONFLICT DO NOTHING` genérico) sem erro e sem desfazer o catálogo.
- `database/ajustes/catalogo_gui.sql`: 2 execuções seguidas sem efeito extra; em banco sem seed com o id 1 renomeado, não toca no id 1. Sem a correção na 004, reaplicá-la depois do ajuste falhava com `servicos_pkey` (reproduzido).
- `scripts/paridade_bi.py`: BI (`fn_bi_resumo`) e pipeline concordam (1.375 pedidos, 1.068 concluídos, 15,2% de cancelamento, distribuição por dia). Mutação (adicionar EXPIRADO a PEDIDO) é detectada e o script sai com 1.
- ETL sobre o CSV de demonstração (mesmo comando do workflow agendado) e `python -m unittest` do pipeline (10 testes) OK; `node --check` em `bi.js`; `check_project.py` OK; YAML dos workflows válido.

- **Receita estimada por mês e por profissional** (tela Equipe, só proprietário; `admin-extra.js` + `equipe.html`): o front chama `/api/admin/equipe/resumo` uma vez por mês (6 meses), sem endpoint novo. Testado em Node com API simulada: 7 chamadas para o proprietário (1 do período + 6 meses), totais por mês/profissional/geral corretos, nomes com HTML escapados; para a recepção a seção fica oculta e não dispara chamadas. No banco demo, o total por agendamento (`preco_referencia`) e por serviço (`agendamento_servicos.preco`) coincidem (R$ 37.055,00, 0 divergências).

**NÃO verificado:** Java. Este ambiente não alcança o repositório Maven, então `RateLimiter` (3 métodos novos), `AuthController` e os 2 testes novos em `AuthControllerTest` **não foram compilados nem executados**. Rodar `mvn clean test` em `app` (esperado: 69 testes). O comportamento visual do BI (rótulos, variação %) não foi aberto em navegador; o workflow agendado só roda no GitHub.

## v12 — segurança administrativa e migração 010 (06/11/2026)
| Item | Resultado |
|---|---|
| `aplicar.sh --demo` (schema, views, seed, migrações 004–010) em PostgreSQL 16 novo, aplicado **2x** | OK, sem erro; `exige_troca_senha` criada com padrão `FALSE` |
| Simulação de **produção** (sem seed, serviços 1–7 com preço/descrição reais, sem profissionais) + migração 010 **2x** | Serviços 1–7 **intactos** (hash idêntico antes/depois, incluindo Luzes R$ 200 e Corte tradicional R$ 45,90); serviços 8–13 criados; **0 vínculos** em `profissional_servicos`; próximo ID = 14 |
| `scripts/verifica_banco.py` | 17/17 casos como esperado |
| `scripts/check_project.py` | OK (a 010 consta em scripts e docs) |
| `node --check` em `admin.js` | OK |
| `PasswordPolicy` (compilada e executada isoladamente com `javac`/`java`) | OK: 72 bytes exatos aceitos, 73 rejeitados; `é`×37 e emoji×19 rejeitados; espaços nas pontas preservados |

Testes JUnit **escritos** nesta versão (revisados por leitura; **ainda não executados com Maven**): `AdminUserServiceTest` (criação, senha inválida, papel inválido, duplicado, falha de auditoria, limite de 72 bytes, troca, redefinição, ativar/desativar, auditoria sem senha, `@Transactional`, situação da conta), `PasswordPolicyTest`, `ForcedPasswordChangeFilterTest` (troca pendente, conta desativada encerra a sessão, falha de consulta = 503), `AuthControllerTest` (limite de 5 tentativas em 15 min por usuário) e 2 casos novos em `AdminPasswordToolTest`.

**Não validado:** `mvn verify` (compilação e execução dos testes acima); a CI do GitHub executa no push.

## v11 — correções da revisão (28/10/2026, PostgreSQL 16.15 em Ubuntu 24.04)
| Item | Resultado |
|---|---|
| `database/aplicar.sh --demo` em banco novo, aplicado **2x** | OK, sem erro (migrações 004–008 idempotentes); também OK sem `--demo` (cenário de produção) |
| `scripts/verifica_banco.py` (`verifica_restricoes`, `verifica_etapa4`, `verifica_etapa5`) | **17/17 casos** como esperado. Removida a constraint `ex_agendamento_sem_sobreposicao`, o script falhou (exit 1): detecta regressão |
| `scripts/check_project.py` | OK; agora também falha se uma migração faltar em scripts/docs ou se `application-prod.properties` perder cookie seguro/forward-headers |
| `node --check` nos 10 arquivos de `static/js`; testes Python | OK (10 testes) |
| Sintaxe Java (`javac` sem dependências) | Sem erro de *parse*; símbolos do Spring/JUnit não resolvidos por falta do Maven Central no ambiente |

**Não validado:** compilação e testes Java (`mvn verify`), perfil `prod` em execução real, versões `embedded-postgres` 2.0.7 / `binaries-bom` 16.4.0, deploy real. A CI do GitHub executará `mvn verify` no primeiro push.

## Etapa 6 — painel de BI (24/10/2026, PostgreSQL 16 + navegador com a saída real da função)
| Item | Resultado |
|---|---|
| `migracoes/006_bi.sql` (`fn_bi_resumo`) aplicada 2x | OK. `anon` sem acesso à função |
| Conferência cruzada JSON × consultas diretas (90 dias) | Receita R$ 37.055,00 e 1.068 atendimentos **iguais**; soma por serviço, por profissional e por semana = receita; mapa de calor = pedidos por dia; período vazio devolve zeros e listas vazias sem erro |
| JSON sem dado pessoal | Confirmado (sem nome, e-mail ou telefone) |
| `admin/bi.html` (Playwright, papel TÉCNICO) | 8 KPIs, 6 gráficos SVG (linha, barras, rosca, barras horizontais, mapa de calor), troca de período, download do CSV (43 linhas), menus exclusivos do dono ocultos; sem erro de JavaScript |

**Não validado:** `AdminBiController` rodando no Spring (compilação), a impressão em PDF e o desempenho com dados reais grandes.

## Etapa 5 — exclusão de cadastro, Supabase e admin (24/10/2026, PostgreSQL 16 local)
| Item | Resultado |
|---|---|
| `migracoes/005` aplicada 2x (idempotente); `verifica_etapa5.sql` | **6/6**: exclusão bloqueada com agendamento futuro, cliente bloqueado não se exclui sozinho (admin pode), anonimização (nome, e-mail, telefone, observações) mantendo o histórico, já excluído, libera após o horário passar |
| RLS | Ligada em 13/13 tabelas. Papel `anon`: *permission denied* em tabela, view e função (`/rpc`) |
| Papel de BI somente-leitura (`analytics_ro`) | Lê as views (1.646 linhas), **não** lê `clientes`; views não têm colunas de nome, e-mail ou telefone |
| 4 SQLs novos do Java (listagem de clientes, bloquear, desbloquear, exclusão) | Válidos contra o schema (`PREPARE`) |
| Front (API simulada, Playwright) | Página `admin/clientes.html` lista, bloqueia, desbloqueia, exclui; botão "Excluir meu cadastro" abre confirmação e chama a API; sem erro de JavaScript |

**Não validado:** compilação Spring/`mvn verify`, `AdminCustomerController`/`CustomerController` rodando de verdade, e o Supabase real.

## Etapa 4 — regras novas (24/10/2026, PostgreSQL 16 + JUnit)
| Item | Resultado |
|---|---|
| `database/migracoes/004_etapa4.sql` (multi-serviço, produtos, exclusões de serviços, token de confirmação, bloqueio de cliente, status `FALTOU`) | Aplicada 2x sem erro (idempotente); 1.646 agendamentos migrados para `agendamento_servicos` |
| `database/tests/verifica_etapa4.sql` | **7/7 casos corretos**: `FALTOU` bloqueia o cliente; cliente bloqueado não agenda (P0001); desbloqueio libera; 2 serviços no mesmo agendamento; serviço duplicado (23505); quantidade > 5 (23514); token de confirmação sem agendamento (23514) |
| JUnit `Etapa4RulesTest` + testes antigos | **23/23 OK** (máx. 3 serviços e 200 min, exclusões simétricas, tolerância exata de 30 min, semana atual/próxima, novas transições) |

**Ainda NÃO ligado ao Spring/front (etapa 4b):** serviços (`BookingService`, `AvailabilityService`, `CatalogService`) ainda leem 1 serviço; faltam os endpoints de produtos, de confirmação por link (`/api/public/confirm`), de liberação da reserva e de semana, o botão "desbloquear" no admin e o passo de produtos no pop-up. Nada disso foi compilado (Maven Central indisponível no ambiente de teste).

## Etapa 2 — execução em PostgreSQL 16 real (24/10/2026)
Ambiente: Ubuntu 24.04, PostgreSQL 16.15, OpenJDK 21 (JDK completo), JUnit 5.10 (pacotes apt). **Maven Central inacessível neste ambiente**, então `mvn verify` continua NÃO executado.

| Teste | Resultado |
|---|---|
| `schema.sql` (com `btree_gist` e `EXCLUDE`), `analytics_views.sql`, `seed_demo.sql` com `ON_ERROR_STOP=1` | **OK**, sem erro |
| `database/tests/verifica_restricoes.sql` (reescrito com SAVEPOINT: o original abortava a transação no 1º erro esperado e os casos 2–4 nunca rodavam) | **4/4 casos corretos**: sobreposição parcial → 23P01; outro profissional → ok; horário encostando → ok; status ativo sem cliente → 23514 |
| 8 consultas de `database/queries/` (02 e 08 exigem `-v agora="'2026-10-24 12:00:00'"`) | **OK** |
| **64 SQLs embutidos no Java** (JdbcTemplate) validados com `PREPARE` contra o schema real | **64/64 OK** (5 de `DashboardService` interpolam constantes; validadas com os valores reais) |
| Regras de domínio Java com JUnit 5 real (`javac` + console launcher) | **14/14 OK** (`AvailabilityCalculatorTest`, `BookingRulesTest`) |

Continua sem validação: compilação do Spring Boot (`mvn verify`), integração app ↔ banco, segurança/CSRF, e-mail, concorrência real e navegador com backend. Para fechar: rode `mvn verify` na sua máquina e envie a saída.

## Executados e aprovados (etapa 1, sem PostgreSQL)
| Teste | Comando / método | Resultado |
|---|---|---|
| Pipeline Python + reconciliação | `cd analytics && python -m unittest discover -s tests -v` | **10 testes OK** (KPIs e 7 agregados das consultas SQL de `database/queries/` executadas em SQLite sobre os mesmos CSVs = resultados do Pandas; contagem independente via `csv`; base vazia; dados sujos R02–R05; sobreposição R11; arquivo ausente; código de saída do CLI; `seed_demo.sql` carregado em esquema mínimo SQLite confere com o CSV: 1646 linhas, status, 3 profissionais, 13 serviços, e-mails só `.invalid`) |
| Regras de domínio Java (lógica pura) | `javac` + executor próprio sobre as classes JUnit reais `AvailabilityCalculatorTest` e `BookingRulesTest`, usando um **shim mínimo da API JUnit** (`@Test`, `Assertions`) | **14 testes OK** (sobreposição parcial, serviço de 200 min × agendamento posterior, pausa de almoço, bloqueios, antecedência mínima, intervalos que só se tocam, transições de status, baixa/confirmação por horário, cancelamento do cliente, horizonte) |
| Sintaxe JavaScript | `node --check` em 6 arquivos | OK |
| Análise sintática do Java Spring | `javac -proc:none` nos 20+ arquivos de `src/main`: nenhum erro de sintaxe (restaram só erros de símbolos/pacotes, esperados sem as dependências) | OK, **não é compilação** |
| Estrutura do repositório | `python3 scripts/check_project.py` | OK (links locais do front, ausência de estilo/handler inline p/ CSP, sem segredos, seed fictício) |
| Determinismo dos dados de demonstração | gerador executado 2× | mesmo hash |

## NÃO executados (falta de ambiente) — não considerar validados
- **`mvn verify` / compilação completa do Spring Boot:** nunca rodou. Há risco real de pequenos erros de compilação ou de API (Spring Boot 4.1 / Security 7 / Jackson 3 são recentes). Corrija na primeira execução local ou no CI.
- **Todo SQL em PostgreSQL:** `schema.sql` (incl. `btree_gist` e `EXCLUDE`), `analytics_views.sql`, `seed_demo.sql` e `tests/verifica_restricoes.sql` não foram executados em PostgreSQL. As consultas analíticas foram testadas só em SQLite.
- **Integração Java ↔ PostgreSQL**, autenticação/autorização dos endpoints, CSRF, envio de e-mail real, link de acesso ponta a ponta, **concorrência** (duas requisições simultâneas) e expiração das reservas.
- **Front-end em navegador:** fluxos, responsividade e acessibilidade não foram testados (apenas sintaxe e consistência de IDs/links).
- **Fonte `--source postgres` do pipeline** e exportação Parquet (sem `psycopg`/`pyarrow`).
- **Power BI:** nenhum `.pbix`; DAX não validado.
- Não existem testes automatizados de controllers/serviços Spring nem teste de integração com Testcontainers.

## Reexecução
```bash
python3 scripts/check_project.py
(cd analytics && pip install -r requirements.txt && python -m unittest discover -s tests -v)
(cd app && mvn --batch-mode verify)          # roda BookingRulesTest e AvailabilityCalculatorTest com JUnit real
psql -d barbershop_gui -v ON_ERROR_STOP=1 -f database/schema.sql -f database/analytics_views.sql -f database/seed_demo.sql
```
O workflow `.github/workflows/verify.yml` executa tudo isso no GitHub Actions com um PostgreSQL 16 de serviço (também não executado aqui).


## Etapa 5 — o que foi (e não foi) verificado (28/10/2026)
- **Executado em PostgreSQL 16 (Ubuntu):** schema, views, seed e migrações 004 a 008 em sequência, sem erro; a 008 reaplicada (idempotente); a **007 executada pela primeira vez**. Consultas novas testadas com dados: BI por serviço, agendamento de balcão sem e-mail, baixa de estoque, resumo por profissional, bloqueio por FALTOU, recusa de novo agendamento do bloqueado, exclusão de cliente sem e-mail e a restrição de papéis.
- **Executado:** `node --check` em todos os JS.
- **NÃO verificado:** compilação Java (`mvn compile`) e testes JUnit — sem Maven/Spring neste ambiente; o novo `Etapa5RulesTest` não foi rodado. Fluxo no navegador (celular e desktop) também não foi aberto. O PostgreSQL usado aqui veio do Ubuntu (traz `btree_gist`); o embutido do perfil `local` (zonky) continua sem confirmação.
