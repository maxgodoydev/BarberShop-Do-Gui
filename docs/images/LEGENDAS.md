# Legendas e referências para o README

Todos os dados exibidos nas capturas e nos GIFs são **fictícios e simulados** (clientes "Demonstração NNN", e-mails `.invalid`). As imagens demonstram a capacidade tecnológica do sistema, não resultados reais do negócio.

Caminhos relativos a partir da raiz do repositório (README.md na raiz).

## Site público (`docs/images/interface/`)

| Arquivo | Legenda |
|---|---|
| `site-home-hero.png` | Página inicial: chamada para agendar e acesso ao catálogo de serviços |
| `site-como-funciona.png` | Fluxo do cliente em três passos: escolher, reservar o horário e aguardar a confirmação |
| `site-catalogo-servicos.png` | Catálogo de serviços com duração e preço de referência (valores demonstrativos) |
| `site-galeria-barbearia.png` | Galeria do espaço e de trabalhos (fotos fornecidas pela equipe, com autorização do proprietário) |
| `site-localizacao-horarios.png` | Endereço, horários de funcionamento e mapa incorporado |

## Agendamentos (`docs/images/agendamentos/`)

| Arquivo | Legenda |
|---|---|
| `agenda-balcao-filtros-e-lista.png` | Agenda e balcão: filtros por período, profissional, serviço e status |
| `agenda-pedidos-solicitados-e-confirmados.png` | Pedidos com status Solicitado, Confirmado e Cancelado e ações de confirmar, dar baixa e cancelar |
| `horarios-bloqueios-de-agenda.png` | Bloqueios de agenda (férias, folga, imprevisto) para a barbearia ou para um profissional |

## Administração, estoque e clientes

| Arquivo | Legenda |
|---|---|
| `administracao/servicos-catalogo.png` | Gestão de serviços: preço, duração, situação e indicação de avaliação prévia |
| `administracao/profissionais-cadastro.png` | Cadastro de profissionais e serviços que cada um executa |
| `estoque/estoque-de-produtos.png` | Estoque de produtos com quantidade, estoque mínimo e cadastro/edição |
| `clientes/clientes-gestao-e-lgpd.png` | Gestão de clientes: bloqueio, exclusão de dados pessoais (LGPD) e indicadores de contas |

## Dashboards e Analytics

| Arquivo | Legenda |
|---|---|
| `dashboards/bi-pedidos-situacao-e-receita.png` | Painel de BI: pedidos por dia da semana, situação dos pedidos, receita por serviço e por profissional |
| `dashboards/bi-mapa-de-calor-dia-hora.png` | Mapa de calor de pedidos por dia da semana e horário |
| `dashboards/bi-destaques-e-receita-semanal.png` | Destaques automáticos (dia mais cheio, horário de pico) e receita por semana |
| `analytics/exportacao-agendamentos-anonimizada-excel.png` | Exportação CSV de agendamentos sem dados pessoais, aberta no Excel |

## GIFs (`docs/media/gifs/`)

| Arquivo | Duração | Legenda |
|---|---|---|
| `agendamento-fluxo-cliente.gif` | ~14 s | Fluxo do cliente: serviços, profissional e horário, produtos e resumo do pedido (a etapa de dados pessoais foi omitida) |
| `agenda-balcao-equipe.gif` | ~13 s | Área da equipe: agenda por dia e por semana, filtros e ações |
| `painel-bi-indicadores.gif` | ~10 s | Painel de BI: indicadores, gráficos e receita semanal |
| `exportacao-csv-anonimizada.gif` | ~7 s | Planilha CSV anonimizada exportada pelo sistema |

## Trechos prontos para o README

```markdown
![Fluxo de agendamento do cliente](docs/media/gifs/agendamento-fluxo-cliente.gif)
*Fluxo do cliente: serviços, profissional e horário, produtos e resumo do pedido.*

![Painel de BI](docs/images/dashboards/bi-pedidos-situacao-e-receita.png)
*Painel de BI com dados simulados: pedidos, situação e receita por serviço e profissional.*
```

> Os arquivos de `_nao-publicar-recapturar/` ficam fora do repositório até serem recapturados.
