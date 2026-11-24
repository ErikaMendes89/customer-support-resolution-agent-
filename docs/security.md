# Segurança e limites

[Voltar ao projeto](../README.md) · [Consultar os casos de teste](evaluation.md)

## Quem pode ver cada dado

- As duas contas de demonstração são atribuídas a organizações diferentes no servidor. As consultas e alterações usam a organização da conta autenticada.
- As chaves estrangeiras compostas mantêm eventos, trechos e decisões associados à organização correta.
- O Angular mantém as credenciais em memória durante a sessão da interface. A API usa HTTP Basic para demonstração local e exige token CSRF nas operações de escrita.
- As senhas da demonstração são configuradas por ambiente e o backend guarda os hashes em memória. Antes de disponibilizar a API para usuários reais, providencie HTTPS, gestão de usuários e controle de papéis.

## O que o rascunho permite

- O modelo recebe casos e trechos como dados não confiáveis. Seu papel é propor texto, com fontes, para análise humana.
- O backend valida os identificadores de fonte. A pessoa revisora confere se os trechos sustentam as afirmações.
- Um rascunho com referências aceitas pela validação pode ser aprovado, editado ou rejeitado. Evidência insuficiente fica sinalizada e admite rejeição justificada. A decisão é registrada no histórico do caso.
- O fluxo da aplicação termina no registro da decisão. A mudança de estado do caso é uma operação separada.

## Onde conferir

- [Configuração de autenticação e CSRF](../backend/src/main/java/dev/erikamendes/support/identity/SecurityConfiguration.java).
- [Testes de propostas](../backend/src/test/java/dev/erikamendes/support/proposals/ProposalIntegrationTests.java) e [testes da revisão](../backend/src/test/java/dev/erikamendes/support/review/ReviewIntegrationTests.java).
- [Contrato da API](api.md) para regras de escopo e respostas de erro.

Nos exemplos deste projeto, use dados fictícios e credenciais locais.
