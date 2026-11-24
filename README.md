# Customer Support Resolution Agent

Uma aplicação de portfólio para acompanhar casos de suporte e propor rascunhos de resolução fundamentados em documentos, para revisão humana.

**Estado atual: fase 6 — demonstração interativa implementada; publicação condicionada à configuração do GitHub Pages.** Além de casos, recuperação, propostas e revisão humana, a CI executa um corpus sintético para verificar abstenção, fontes, citações, resistência a instruções em documentos e isolamento por organização. Um runner separado permite avaliar o Ollama real em banco descartável; métricas determinísticas com modelos falsos não representam qualidade semântica real.

## Comece aqui

Um atendente precisa saber se um produto tem garantia. A aplicação busca a política na base da organização e prepara um rascunho com a fonte. O atendente confere o trecho e registra sua decisão.

| Se você quer... | Comece por |
| --- | --- |
| Entender o fluxo sem instalar nada | [Exemplo de garantia](docs/first-case.md#exemplo-de-garantia) |
| Testar com dados fictícios | [Execução local](docs/local-setup.md) e [primeiro caso guiado](docs/first-case.md#experimento-local) |
| Acompanhar a implementação | [Arquitetura e roteiro de leitura](docs/architecture.md) |
| Conferir os limites e os testes | [Segurança](docs/security.md) e [avaliação](docs/evaluation.md) |

## O que você vai aprender

- **RAG** (geração aumentada por recuperação): usar trechos de documentos para dar contexto ao modelo e exibir as fontes consultadas.
- **Abstenção**: sinalizar evidência insuficiente quando a busca não encontra apoio ou a resposta vem com citações inválidas.
- **Revisão humana** (*human-in-the-loop*): aprovar, editar ou rejeitar um rascunho e registrar a decisão.
- **Avaliação**: testar regras com resultados reproduzíveis e avaliar a qualidade do modelo local em uma execução separada.

## O que existe agora

- Cadastro, estados e histórico de casos em Angular e Spring Boot.
- Ingestão de documentos de texto, busca vetorial com pgvector e modelos locais via Ollama.
- Rascunhos com fontes preservadas, revisão humana e duas organizações fictícias para demonstrar isolamento.
- Testes de integração e avaliação sintética na CI.

## Arquitetura modular

O backend organiza identidade, casos, conhecimento, propostas e revisão em um monólito modular. A interface Angular chama a API Spring Boot. O PostgreSQL com pgvector armazena casos e trechos, e o Ollama local gera embeddings e rascunhos.

Siga o [fluxo de arquitetura e os links para o código](docs/architecture.md#roteiro-de-leitura).

```text
backend/               # API, regras de negócio e migrações
frontend/              # Interface Angular
evaluation/run_live.py  # Avaliação real opt-in em banco descartável
compose.yaml           # PostgreSQL/pgvector local
.github/workflows/ci.yml
```

## Demonstração interativa e métricas

A pasta `demo/` contém uma apresentação interativa com dados fictícios, respostas pré-definidas e revisão humana simulada. Não chama o Ollama nem a API; o fluxo real continua disponível na aplicação Java/Angular. A CI testa as interações e o layout móvel, gera o pacote `public-demo` e inclui métricas sintéticas da mesma execução com SHA e link de origem. Resultados reais do Ollama ainda não estão publicados.

Consulte [o roteiro, os testes e a configuração de publicação](demo/README.md). O deploy no GitHub Pages fica desabilitado até configurar o serviço e a variável `DEMO_PUBLISH=true`; o pacote pode ser baixado nos artefatos da CI. Depois de ativado, só é publicado após sucesso dos testes e builds na `main`.

## Como executar localmente

O [guia de execução](docs/local-setup.md) reúne requisitos, comandos e verificações para subir banco, API e interface. O [contrato da API](docs/api.md) documenta rotas, estados e respostas.

## Testes e CI

Consulte [como rodar os testes e interpretar as métricas](docs/evaluation.md).

## Roadmap

- [x] Fase 0: fundação Spring Boot/Angular, PostgreSQL/pgvector, Flyway, autenticação de demonstração e CI.
- [x] Fase 1: cadastro de casos, estados, histórico e isolamento por organização.
- [x] Fase 2: ingestão de texto, trechos, embeddings e busca vetorial com fontes.
- [x] Fase 3: propostas de resolução com fontes.
- [x] Fase 4: revisão humana e trilha de decisões.
- [x] Fase 5: corpus sintético, gates de segurança e runner real opt-in.
- [x] Fase 6 (implementação): demonstração interativa com dados fictícios, métricas rastreáveis da CI, testes e pacote público.
- [ ] Fase 6 (publicação): configurar GitHub Pages, ativar `DEMO_PUBLISH` e confirmar a URL publicada.

Este repositório é público e usa somente exemplos fictícios. Não adicione dados de clientes, senhas, tokens nem instruções internas de trabalho.
