# Política inicial de privacidade — modelo para revisão

**Status:** rascunho acadêmico. Não substitui análise jurídica, definição formal de responsabilidades nem revisão pelo responsável pelo negócio. Não declarar conformidade integral com a LGPD com base apenas neste arquivo.

## 1. Dados que a solução pretende coletar

| Dado | Finalidade pretendida | Cuidado |
|---|---|---|
| Nome | Identificar a solicitação no atendimento | Não pedir nome de terceiros sem necessidade. |
| Contato | Retornar sobre a solicitação | Usar somente para tratar o pedido; proteger contra acesso não autorizado. |
| Serviço e data/hora preferidos | Entender o pedido e avaliar disponibilidade | Preferência não significa confirmação. |
| Observação opcional | Registrar uma preferência de atendimento pertinente | Orientar a pessoa a não incluir dados sensíveis. |
| Status e timestamps operacionais | Organizar o fluxo e medir os estados | Restringir o acesso administrativo. |

A versão demonstrativa não pede CPF, endereço residencial, data de nascimento ou dados de pagamento porque não são necessários para o fluxo proposto.

## 2. Acesso e segurança

- A equipe autorizada deve acessar dados de agendamento apenas quando necessário para tratar o pedido.
- A aplicação deve ficar atrás de autenticação e autorização no servidor para a área administrativa.
- Usar HTTPS no ambiente publicado; variáveis de ambiente para credenciais; nunca versionar senhas/tokens.
- Restringir logs, evitar registrar contato/observações quando não necessário e manter dependências atualizadas.
- Não utilizar dados reais no seed, screenshots públicos, exemplos de código ou demonstrações abertas.

## 3. Retenção, correção e exclusão

O prazo de retenção ainda precisa ser definido com o responsável pelo negócio, considerando a finalidade do atendimento, obrigações legais aplicáveis e a necessidade de eliminação/minimização. Não existe um prazo automático estabelecido por este projeto. Deve ser disponibilizado um canal de contato para pedidos de acesso, correção ou exclusão, com verificação proporcional da identidade e avaliação de exceções legais aplicáveis.

## 4. Versão acadêmica e limites

- Os registros incluídos no `seed.sql` são inteiramente fictícios.
- A versão técnica não implementa um fluxo completo de solicitações de titular de dados, política formal de retenção, auditoria de acesso ou gestão de consentimento/base legal.
- Antes de coletar dados pessoais reais, o responsável deve definir controlador/operador, finalidade e base legal adequadas, aviso de privacidade, retenção e processo de atendimento dos direitos.
- A política não é uma licença de software. A licença do código está no arquivo `LICENSE`.

## 5. Revisão antes da publicação

Confirmar quais dados são realmente necessários, quem é responsável pelo tratamento, quem atende solicitações de titulares, canal de contato, prazo de retenção, controles de acesso, backups e rotina de exclusão. Revisar o texto com assessoramento apropriado antes de uso comercial.
