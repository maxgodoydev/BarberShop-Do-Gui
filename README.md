<div align="center">

# ✂️ BarberShop do Gui

**Agendamento online, painel da equipe e BI para uma barbearia de bairro real, perto do Grajaú, zona sul de São Paulo.**

[🌐 **Ver o site no ar**](https://barbershop-do-gui.onrender.com/servicos.html) · [📂 Repositório](https://github.com/maxgodoydev/BarberShop-Do-Gui) · [📚 Documentação](#-documentação)

![Java 21](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-336791) ![Python](https://img.shields.io/badge/Python-Pandas-3776AB) ![Power BI](https://img.shields.io/badge/Power_BI-DAX-F2C811) ![Render](https://img.shields.io/badge/Deploy-Render-46E3B7)

**Desenvolvido pela equipe Segunda Vitrine:** Alexandre · Max Godoy · Luca

![Página inicial do site público](docs/images/interface/site-home-hero.png)

</div>

> 🕐 O site está no plano gratuito do Render: se ficou parado, a primeira abertura pode levar cerca de 1 minuto.

---

## 🎯 Que problema isso resolve?

Barbearia pequena agenda por mensagem e caderno. Isso gera três dores:

- **Conflito de horário:** dois pedidos para o mesmo profissional no mesmo intervalo, ou um corte longo que invade o horário seguinte.
- **Sem visão do negócio:** ninguém sabe qual é o dia mais cheio, o horário de pico, qual serviço rende mais ou quantos pedidos são cancelados.
- **Dados pessoais soltos:** nomes e telefones em conversas e papéis, sem como apagar a pedido do cliente (LGPD).

## 👥 Para quem foi feito?

| Quem | O que ganha |
|---|---|
| **Cliente** | Escolhe serviço, profissional e horário livre em poucos passos, **sem criar senha**. Um link por e-mail confirma o pedido. |
| **Recepção** | Agenda do dia e da semana, agendamento no balcão, estoque e equipe. Não vê financeiro nem BI. |
| **Proprietário** | Tudo isso, mais BI, receita por serviço e por profissional, bloqueios de agenda, usuários e auditoria. |

## ⚙️ Como funciona

![Fluxo de agendamento do cliente](docs/media/gifs/agendamento-fluxo-cliente.gif)

1. O cliente escolhe serviço, profissional e data. O sistema mostra **só horários livres** (considera duração, pausas, bloqueios e outros agendamentos).
2. O horário fica **reservado por 10 minutos** enquanto ele preenche os dados.
3. O pedido nasce como **SOLICITADO** e o cliente recebe um **link de confirmação** (vale 30 min). Sem confirmar, o horário é liberado sozinho.
4. A equipe acompanha, confirma, dá baixa ou cancela.

![Agenda e balcão da equipe](docs/media/gifs/agenda-balcao-equipe.gif)

## 🤖 Automação de processos

O fluxo de ponta a ponta roda sem ninguém na recepção digitando:

- **Reserva temporária e liberação automática** de horários não confirmados.
- **Link mágico por e-mail** para confirmar o pedido e acessar o espaço do cliente.
- **Anti-conflito no banco** (`EXCLUDE USING gist`): mesmo com duas requisições simultâneas, intervalos sobrepostos são recusados.
- **Regras de negócio automáticas:** combinações proibidas de serviços, limite de duração por visita e política de falta (*no-show*).
- **Integração entre setores:** o atendimento (agenda e balcão), o financeiro (valores por serviço e profissional, só para o proprietário) e o estoque (produtos do pedido) usam o mesmo banco.
- **CI e rotinas agendadas** no GitHub Actions: testes, verificação do banco e pipeline de dados toda segunda-feira.

## 📊 BI e análise de dados

O mesmo banco alimenta o **painel de BI do sistema** e um **pipeline Python/Pandas** com relatório de qualidade dos dados, medidas **DAX** e roteiro de **Power BI**. Os indicadores mostram dia mais cheio, horário de pico, situação dos pedidos e receita por serviço e por profissional.

![Painel de BI](docs/images/dashboards/bi-pedidos-situacao-e-receita.png)
![Mapa de calor por dia e horário](docs/images/dashboards/bi-mapa-de-calor-dia-hora.png)

A análise usa **views sem dados pessoais** e a exportação CSV é anonimizada. As imagens mostram **dados fictícios**.

## 🛡️ Segurança e LGPD

Login administrativo com BCrypt, bloqueio por tentativas, CSRF, CSP e **troca obrigatória de senha** no primeiro acesso. O cliente pode excluir a própria conta: os dados pessoais saem e fica só o histórico anônimo para os indicadores.

## 🧰 Por que estas tecnologias?

| Decisão | Motivo |
|---|---|
| **PostgreSQL (Supabase)** | O anti-conflito de horários fica no banco, não só no código. |
| **Spring Boot + JDBC puro** | O SQL é o centro do problema (disponibilidade, sobreposição, views analíticas). A consulta fica visível e parametrizada. |
| **Regras puras em `model/`** | Status e cálculo de horários livres são testáveis isoladamente com JUnit (69 testes). |
| **HTML/CSS/JS puro** | Permite CSP restritiva e reduz a superfície de ataque do front. |
| **Cliente sem senha** | Barbearia tem cliente eventual: senha seria atrito e mais um dado a proteger. |
| **Python/Pandas + Power BI** | Separa análise de operação e mantém a LGPD: o BI nunca toca em nome, e-mail ou telefone. |
| **Render + Docker** | Publicação simples e gratuita para um projeto acadêmico com cliente real. |

## 🤝 Quem fez o quê: equipe Segunda Vitrine

Projeto acadêmico (FATEC Zona Sul, Desenvolvimento de Software Multiplataforma) feito em grupo para uma barbearia real. Começou com HTML, CSS, Bootstrap e JavaScript e foi remodelado com Java, Python, SQL, Analytics e BI.

| Integrante | Atuação |
|---|---|
| [**Max Godoy**](https://github.com/maxgodoydev) | **Líder do projeto** e contato direto com o dono da barbearia. Planejou a arquitetura e as tecnologias. Cuidou do **BI**, das **regras de negócio**, do **fluxo do processo** e da **automação do agendamento**, além da ligação entre **atendimento, financeiro e estoque**. |
| [**Luca**](https://github.com/Luck16Ash) | Atuou em frentes próximas às do Max, com mais foco em **segurança, Java, Python, LGPD**, organização das **tarefas** e **revisão do projeto**. |
| [**Alexandre**](https://github.com/alexandregial-cmd) | Também atuou nas mesmas frentes, com mais foco em **Spring Boot, Java** e demais necessidades técnicas. |

Mais detalhes em [`docs/equipe.md`](docs/equipe.md).

## 🚀 Como rodar

**Sem instalar banco** (Postgres embutido, só JDK 21+ e Maven):

```powershell
cd app
$env:BOOTSTRAP_OWNER_USERNAME = "gui"; $env:BOOTSTRAP_OWNER_PASSWORD = "uma-senha-com-12+"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"      # http://localhost:8080
```

- Painel da equipe: `http://localhost:8080/admin-login.html` (sem senha padrão; contas técnicas são criadas pelo proprietário).
- Com `MAIL_MODE=dev` nenhum e-mail é enviado: o link do cliente aparece no log do servidor.
- Testes: `cd app && mvn test`. Pipeline de dados: `cd analytics && pip install -r requirements.txt && python -m barbershop_analytics --input sample_data --output output`.
- Banco próprio, variáveis e produção: [`docs/deploy.md`](docs/deploy.md) e [`.env.example`](.env.example).

## 📚 Documentação

[Arquitetura](docs/arquitetura.md) · [Modelo de dados](docs/modelo-de-dados.md) · [Regras de agendamento](docs/regras-de-agendamento.md) · [API](docs/api.md) · [Segurança e LGPD](docs/seguranca.md) · [Métricas](docs/metricas.md) · [Pipeline de dados](docs/pipeline-de-dados.md) · [Power BI](docs/powerbi.md) · [Deploy](docs/deploy.md) · [Testes](docs/testes.md) · [Limitações](docs/limitacoes.md)

---

<sub>Código sob licença MIT. Marca, logo e fotos pertencem à BarberShop do Gui e são usados com autorização do proprietário. Todos os dados das imagens são fictícios. Desenvolvido com apoio de IA generativa, declarado conforme as regras acadêmicas.</sub>
