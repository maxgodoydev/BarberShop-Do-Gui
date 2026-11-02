# Apresentação do projeto em entrevista

## Versão de aproximadamente 45–60 segundos

“Nosso projeto acadêmico foi pensado a partir de uma necessidade prática: organizar melhor a presença digital de uma barbearia e dar visibilidade aos pedidos de horário. A proposta reúne uma página web, um fluxo de solicitação de agendamento, armazenamento relacional em PostgreSQL e um painel que usa consultas SQL para mostrar serviços mais solicitados, status e volume por período. Eu e o @piruetai estamos na frente de Dados & Analytics, com foco sugerido em modelagem, consultas, definição das métricas e leitura dos resultados, enquanto a frente Java cuida da aplicação e da integração. Uma preocupação importante foi não tratar a solicitação como confirmação e não apresentar dados fictícios como se fossem resultados reais. A próxima etapa é validar o catálogo e os horários com a barbearia e executar a bateria completa de testes em um ambiente com Maven e PostgreSQL.”

## Pontos técnicos que vale explicar

- Por que PostgreSQL: os registros têm relacionamentos e regras de integridade que se beneficiam de um banco relacional.
- Por que catálogo de métricas: faz a equipe explicar o cálculo e evitar concluir que todos os agendamentos representam serviços realizados.
- Por que status separados: `SOLICITADO`, `CONFIRMADO`, `CONCLUIDO` e `CANCELADO` representam estados diferentes do fluxo.
- Por que dados fictícios: permitem montar e testar visualizações sem expor dados pessoais ou alegar resultados comerciais inexistentes.
- Limite que a equipe reconhece: o projeto não consulta a agenda oficial, não valida dias/horários de atendimento e não mede faturamento.

Adapte o texto ao que cada integrante efetivamente implementou e verificou. Não alegar deploy, testes integrados ou contribuições individuais que não tenham evidência.
