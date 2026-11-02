# Product Model Canvas — BarberShop do Gui

> Documento de concepção. Os pontos abaixo são hipóteses de solução, não resultados validados nem declaração de demanda comercial comprovada.

| Bloco | Definição inicial |
|---|---|
| **Problema** | Informações digitais pouco centralizadas; difícil consultar a agenda e transformar registros em uma visão resumida. |
| **Público-alvo** | Pessoas que procuram os serviços da barbearia e a pessoa/equipe responsável por acompanhar solicitações. |
| **Necessidades do negócio** | Apresentar informações básicas com clareza; organizar pedidos de horário; visualizar volume, status e procura por serviço. |
| **Proposta de valor** | Uma presença web simples para orientar o cliente e uma visão organizada dos pedidos para apoiar a rotina administrativa. |
| **Solução proposta** | Página institucional, catálogo demonstrativo, formulário de solicitação, banco relacional e painel administrativo. |
| **Funcionalidades essenciais** | Consultar catálogo, enviar solicitação, evitar duplicidade de horário ativo no banco, filtrar pedidos, atualizar status e observar indicadores. |
| **Diferenciais do projeto** | Conectar uma necessidade cotidiana a um fluxo simples de dados, consultas SQL documentadas e indicadores com regras explícitas. |
| **Indicadores de sucesso a validar** | Solicitações registradas sem erro; tempo para localizar um agendamento; consistência dos status; uso dos filtros; entendimento das métricas. |
| **Riscos e limitações** | Preços/serviços/horários não confirmados; disponibilidade do horário não consulta uma agenda oficial; base demonstrativa; publicação exige revisão de segurança e privacidade. |
| **Fora do escopo** | Pagamento, loja virtual, venda/estoque, app nativo, microsserviços, integrações externas ou recursos de IA sem finalidade. |

## Premissas e decisões

- A pessoa cliente envia uma **solicitação**. O sistema não deve chamar essa solicitação de agendamento confirmado sem decisão da equipe.
- O corte masculino tem referência aproximada de R$ 40,00 informada no briefing. Os demais preços/serviços usados no seed são exemplos a validar.
- Os horários sugeridos pelo formulário são opções de entrada para demonstração; não representam o horário oficial nem confirmam disponibilidade comercial.
- Os dados da carga inicial são fictícios e servem exclusivamente para testar a interface e as consultas.
- A frente Java constrói regras e integração; Dados & Analytics define estrutura, consultas, indicadores e limitações. A divisão é sugerida, não um histórico de contribuições já realizadas.

## Como validar o produto

1. Confirmar catálogo, preços, duração, dias/horários e regras reais de atendimento com a pessoa responsável.
2. Testar o fluxo com dados fictícios antes de cadastrar qualquer informação real.
3. Definir quem acompanha solicitações e como confirma horários.
4. Testar se os indicadores respondem a perguntas úteis sem confundir demanda, cancelamento e serviço realizado.
5. Revisar segurança, privacidade, retenção e atendimento de pedidos de correção/exclusão antes de uso público.
