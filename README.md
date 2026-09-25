# Customer Support Resolution Agent

Uma aplicação de portfólio para acompanhar casos de suporte e propor rascunhos de resolução fundamentados em documentos, para revisão humana.

**Estado atual: fase 5 — avaliação reproduzível e testes de segurança.** Além de casos, recuperação, propostas e revisão humana, a CI executa um corpus sintético para verificar abstenção, fontes, citações, resistência a instruções em documentos e isolamento por organização. Um runner separado permite avaliar o Ollama real em banco descartável; métricas determinísticas com modelos falsos não representam qualidade semântica real.

## O que existe agora

- API Spring Boot com duas contas de demonstração separadas por organização, endpoints de casos protegidos por HTTP Basic e `GET /actuator/health` público.
- Angular para criar casos, listar, consultar detalhes, mudar estados e ler o histórico da organização autenticada.
- PostgreSQL 17 com pgvector, iniciado por Docker Compose; Flyway versiona a extensão `vector`, organizações fictícias, casos e eventos.
- Base de conhecimento com textos de até 12 mil caracteres, embeddings locais pelo Ollama, fontes por documento/trecho e exclusão de documentos.
- Propostas versionadas por caso, com fontes preservadas mesmo após excluir o documento original; interface para consulta e geração.
- Revisão humana de cada proposta, com decisão única e auditável, justificativa para edições e rejeições e consulta ao histórico por caso.
- CI que executa testes de integração e avaliação sintética contra PostgreSQL/pgvector, publica o resumo de métricas como artefato e compila o frontend.

As duas contas servem apenas à demonstração local. Seus nomes e senhas são configurados por ambiente, não ficam no repositório, e as senhas são codificadas em memória pelo backend. A interface guarda a credencial de acesso somente em memória enquanto estiver aberta; sair remove essa credencial da interface. **HTTP Basic deve ser usado apenas em localhost ou sobre HTTPS.** Ainda não há gestão de usuários persistentes nem papéis completos. O isolamento dos casos é feito pelo identificador da organização associado à conta autenticada, e não por um identificador fornecido pelo cliente.

## Por que construir este projeto

Um agente de suporte útil deve indicar de onde veio cada informação, reconhecer quando falta contexto e deixar decisões sensíveis para uma pessoa. O objetivo das próximas fases é medir essas propriedades com casos reproduzíveis, além de apresentar uma aplicação utilizável.

### Fluxo planejado

1. Um atendente registra uma solicitação de suporte.
2. O sistema classifica o caso e recupera trechos relevantes de documentos autorizados.
3. O agente produz uma proposta de resposta com referências aos trechos utilizados.
4. O backend valida a proposta e encaminha casos incertos ou sensíveis para revisão.
5. O atendente aprova, edita ou rejeita a proposta; o histórico fica registrado.

Não haverá execução automática de reembolsos, cancelamentos ou alterações em sistemas externos no primeiro produto funcional.

## Tecnologias

| Parte | Tecnologia | Uso atual |
| --- | --- | --- |
| Backend | Java 17, Spring Boot 4.1.1 | API, segurança, regras de estado e transações |
| Frontend | Angular 21, TypeScript | Interface de casos e histórico |
| Banco | PostgreSQL 17, pgvector 0.8.1 | Casos, eventos, documentos e busca vetorial |
| Embeddings | Ollama + `nomic-embed-text:v1.5` | Vetores locais com 768 dimensões |
| Migrações | Flyway | Esquema versionado |
| Build | Maven Wrapper, npm | Builds reproduzíveis |
| CI | GitHub Actions | Integração do backend e build do frontend |

O modelo de embeddings é configurável por ambiente, mas a coluna vetorial da fase 2 exige **768 dimensões**. O modelo de geração é `qwen3:1.7b` no Ollama e pode ser configurado por ambiente. Os testes de integração usam vetores fictícios e determinísticos para verificar armazenamento, ranking e isolamento; eles não medem qualidade semântica do Ollama.

## Arquitetura modular

O backend parte de um monólito modular. Cada capacidade tem suas próprias regras, casos de uso, API e adaptadores. `identity` oferece a autenticação de demonstração e o contexto da organização; `cases` contém o fluxo de casos, separados entre `domain`, `application`, `infrastructure` e `api`.

| Módulo | Situação | Responsabilidade planejada |
| --- | --- | --- |
| `identity` | Demonstração | Duas contas e o contexto da organização autenticada |
| `cases` | Implementado | Casos, transições de estado, consulta e histórico |
| `knowledge` | Implementado | Documentos de texto, trechos, embeddings e busca |
| `proposals` | Implementado | Recuperação por organização, geração, validação e persistência de rascunhos |
| `review` | Implementado | Aprovação, edição, rejeição e histórico imutável das decisões |
| `audit` | Parcial no módulo `review` | As decisões são preservadas em `proposal_reviews`; auditoria abrangente permanece no roadmap |

As regras de estado ficam no domínio; as transações são coordenadas na camada de aplicação; os repositórios usam JDBC com consultas explícitas. Um caso e seu evento são gravados na mesma transação. A alteração de estado bloqueia a linha do caso até registrar o evento. As consultas e alterações incluem a organização autenticada. As chaves estrangeiras compostas dos eventos e trechos impedem associá-los a registros de outra organização.

Na ingestão, o texto é dividido em trechos de até 900 caracteres Unicode, com sobreposição de aproximadamente 120. Os embeddings são gerados **antes** da transação; documento e trechos são inseridos juntos, de modo que uma falha do modelo não deixe um documento parcial visível. O hash SHA-256 evita duplicar o mesmo conteúdo para a mesma organização e modelo. A busca filtra a organização e o modelo antes de calcular a distância de cosseno no pgvector; a primeira versão usa ranking exato para priorizar a consistência dos resultados. Índices aproximados como HNSW serão avaliados com volume e métricas de recall.

A geração busca até cinco trechos do modelo de embeddings atual e descarta os que estão abaixo da similaridade mínima configurada (`PROPOSAL_MIN_SIMILARITY`, padrão `0.55`). O limiar deve ser calibrado em um corpus representativo: similaridade de cosseno não é uma probabilidade de correção. O texto do caso e os documentos são incluídos no prompt como dados não confiáveis, sem ferramentas nem ações externas. A API exige ao menos uma referência válida no formato `[S1]` no texto da resposta e descarta uma resposta vazia, longa demais ou com referência desconhecida. Essa validação verifica a existência da fonte, **não** comprova que a afirmação esteja correta: a pessoa deve confrontar o rascunho com os trechos. A chamada ao modelo ocorre antes da transação; antes de salvar, a aplicação bloqueia o caso, confere seu estado e verifica se os trechos recuperados ainda existem. As fontes usadas ficam como snapshots em `proposal_sources`, para permitir auditoria após exclusão de documentos. A proposta não altera o estado do caso.

Na revisão, a aplicação bloqueia primeiro o caso e depois a proposta na mesma transação, preservando uma ordem de bloqueios. A restrição `UNIQUE (proposal_id)` impede duas decisões para uma mesma proposta mesmo com solicitações simultâneas. A chave composta relaciona decisão, proposta, caso e organização; consultas usam o escopo da conta autenticada. Apenas rascunhos `READY_FOR_REVIEW` podem ser aprovados ou editados; uma proposta com evidência insuficiente pode ser rejeitada com justificativa. Editar exige justificativa, texto diferente e pelo menos uma citação a uma fonte já preservada. Essa checagem valida os identificadores das citações, não verifica o sentido das afirmações editadas. Casos resolvidos ou encerrados não aceitam novas decisões. O estado do caso continua sendo alterado por uma operação separada.

```text
backend/
  src/main/java/dev/erikamendes/support/
    identity/          # Segurança e contexto da organização
    cases/             # Domínio, aplicação, infraestrutura e API
    knowledge/         # Ingestão, embeddings e busca
    proposals/         # Geração, validação e fontes dos rascunhos
    review/            # Decisão humana e histórico
  src/main/resources/db/migration/
    V1__enable_vector.sql
    V2__cases_and_history.sql
    V3__knowledge_documents.sql
    V4__resolution_proposals.sql
    V5__human_reviews.sql
    V6__generation_idempotency.sql
frontend/src/app/features/cases/  # Interface de casos
frontend/src/app/features/knowledge/  # Documentos e busca
evaluation/run_live.py  # Avaliação real opt-in em banco descartável
compose.yaml           # PostgreSQL/pgvector local
.github/workflows/ci.yml
```

## Como executar localmente

Pré-requisitos: Java 17, Node.js 22.12+ ou 24, npm, Docker com Compose e Ollama para cadastrar documentos, buscar e gerar propostas. O projeto inclui Maven Wrapper; não é necessário instalar Maven globalmente.

Prepare o modelo local antes de usar a base de conhecimento:

```bash
ollama pull nomic-embed-text:v1.5
ollama pull qwen3:1.7b
ollama serve
```

Se o Ollama já estiver ativo, basta executar o `pull`. Use `OLLAMA_BASE_URL` e `OLLAMA_EMBEDDING_MODEL` no `.env` se sua instalação estiver em outro endereço ou usar outro modelo de **768 dimensões**. Trocar de modelo não converte vetores já cadastrados: documentos antigos ficam armazenados, mas apenas o modelo atual é consultado. Reimporte ou reindexe antes de usar os dados antigos em busca.

1. Copie `.env.example` para `.env` na raiz e substitua as três senhas por valores locais. Se já tiver um `.env` da fase 0, acrescente `DEMO_SECONDARY_USERNAME`, `DEMO_SECONDARY_PASSWORD`, `GENERATION_PROVIDER` e `OLLAMA_GENERATION_MODEL`. Nunca faça commit de `.env`.
2. Inicie o banco a partir da raiz:

   ```bash
   docker compose --env-file .env up -d postgres
   ```

3. Carregue as variáveis do arquivo no terminal e inicie a API:

   ```bash
   set -a
   . ./.env
   set +a
   cd backend
   ./mvnw spring-boot:run
   ```

   O backend escuta na porta `8080`. O Flyway executa `V1__enable_vector.sql` durante a inicialização. O usuário do banco local criado pelo Compose pode instalar essa extensão; em outro ambiente, prepare a extensão com uma conta autorizada antes da migração.

4. Em outro terminal, inicie o frontend:

   ```bash
   cd frontend
   npm ci
   npm start
   ```

5. Abra `http://localhost:4200` e entre com uma das duas contas do `.env`. Em **Casos**, crie um caso e acompanhe seu histórico. Em **Conhecimento**, cole texto ou selecione um `.txt`/`.md`, indexe e busque trechos. No detalhe do caso, clique em **Gerar proposta**, examine as fontes e registre sua aprovação, edição ou rejeição. Uma edição ou rejeição precisa de justificativa. Saia e entre com a outra conta para conferir que casos e documentos não aparecem ali. O servidor de desenvolvimento do Angular encaminha `/api` para `http://localhost:8080`.

Também é possível conferir a API diretamente:

```bash
curl http://localhost:8080/actuator/health
curl -u 'demo:SUA_SENHA_LOCAL' http://localhost:8080/api/v1/me
```

O valor `demo` no segundo comando deve ser substituído caso você altere `DEMO_USERNAME`.

### Contrato da API

| Método e rota | Resultado |
| --- | --- |
| `GET /api/v1/me` | Usuário e organização autenticados |
| `GET /api/v1/me/csrf` | Emite o cookie de proteção para operações de escrita |
| `POST /api/v1/cases` | Cria um caso e seu evento inicial; recebe `title` e `description` |
| `GET /api/v1/cases?page=0&size=20` | Lista paginada da organização atual; `size` vai de 1 a 50 |
| `GET /api/v1/cases/{id}` | Caso e eventos em ordem cronológica |
| `PATCH /api/v1/cases/{id}/status` | Muda o estado; recebe `status` e `note` opcional |
| `POST /api/v1/documents` | Cadastra texto; recebe `title` e `content`; repetição do mesmo conteúdo/modelo retorna o documento existente |
| `GET /api/v1/documents?page=0&size=20` | Lista documentos da organização |
| `GET /api/v1/documents/{id}` | Metadados e trechos do documento |
| `DELETE /api/v1/documents/{id}` | Exclui documento e trechos da organização |
| `POST /api/v1/knowledge/search` | Recebe `query` e `topK` (1 a 10); retorna trechos, fonte e similaridade |
| `POST /api/v1/cases/{id}/proposals` | Gera e grava um rascunho ou indica evidência insuficiente; retorna `201`; aceita `Idempotency-Key` |
| `GET /api/v1/cases/{id}/proposals` | Lista até 50 propostas recentes do caso |
| `GET /api/v1/cases/{id}/proposals/{proposalId}` | Consulta a proposta e os snapshots das fontes citadas |
| `POST /api/v1/cases/{id}/proposals/{proposalId}/review` | Registra decisão `APPROVED`, `EDITED` ou `REJECTED`; recebe `decision`, `editedAnswer` opcional e `note` opcional conforme a decisão |
| `GET /api/v1/cases/{id}/proposals/{proposalId}/review` | Consulta a decisão da proposta |
| `GET /api/v1/cases/{id}/reviews` | Lista até 50 decisões recentes do caso |

Para a geração, envie `Idempotency-Key` com 1 a 80 caracteres alfanuméricos, `-` ou `_` (por exemplo, um UUID). Uma repetição após o sucesso retorna a mesma proposta sem chamar os modelos novamente. Enquanto a primeira chamada ainda está em processamento, a repetição retorna `409` e deve ser tentada depois com a mesma chave. Falhas liberam a reserva para uma nova tentativa; uma reserva abandonada pode ser recuperada após dez minutos. A interface preserva a chave enquanto a geração não termina com sucesso. Clientes antigos sem chave continuam funcionando, mas podem gerar propostas repetidas. Reenviar a mesma decisão de revisão pelo mesmo usuário, com os mesmos campos normalizados, retorna a decisão já gravada; uma decisão diferente retorna `409`.

As requisições POST, PATCH e DELETE exigem autenticação e o cabeçalho `X-XSRF-TOKEN` correspondente ao cookie `XSRF-TOKEN`. O Angular gerencia esse cabeçalho após chamar a rota de CSRF. Um ID inexistente ou de outra organização retorna `404`; uma transição proibida retorna `409`. O cliente não envia `organizationId` para determinar o escopo de uma operação. Falha do serviço de embeddings ou de geração retorna `503` sem gravar proposta. Caso resolvido/encerrado, fontes removidas durante a geração, proposta já decidida ou tentativa de aprovar evidências insuficientes retornam `409`. Rejeição sem justificativa e edição sem justificativa, sem citações válidas ou idêntica ao original retornam `400`.

A interface lê arquivos de texto no navegador e envia seu conteúdo como JSON. Não há processamento de PDF, Word, HTML, imagens ou arquivos binários; a geração de propostas requer o Ollama com os dois modelos configurados. A ingestão é síncrona e limitada a 12 mil caracteres, para manter o tempo de espera controlado. O modelo local precisa estar acessível ao backend.

Transições permitidas:

| Estado atual | Próximos estados |
| --- | --- |
| `OPEN` | `IN_PROGRESS`, `NEEDS_INFORMATION`, `RESOLVED` |
| `IN_PROGRESS` | `NEEDS_INFORMATION`, `RESOLVED` |
| `NEEDS_INFORMATION` | `IN_PROGRESS` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | Nenhum |

## Testes e CI

Com o banco ativo e as variáveis de `.env` carregadas, execute `cd backend && ./mvnw verify`. Os testes de integração verificam migrações, autenticação, CSRF, casos e histórico, cadastro idempotente, busca vetorial, exclusão e isolamento entre duas organizações. Os testes substituem o Ollama por vetores e respostas controladas e verificam aprovação única, edição, rejeição, CSRF, estados e isolamento; nenhum serviço externo é necessário para `verify`.

Para conferir o frontend, execute `cd frontend && npm ci && npm run build`. A CI executa ambos os builds em tarefas separadas. O banco dos testes é efêmero e não contém dados reais.

## Avaliação da fase 5

O corpus versionado em `backend/src/test/resources/evaluation/scenarios.json` contém seis cenários fictícios: ausência de documentos, resposta fundamentada, consulta fora de assunto, instrução maliciosa no documento, resposta sem citação e documento de outra organização. `AgentEvaluationTests` roda na CI com modelos falsos determinísticos e falha se o estado, a presença de fontes, a abstenção ou o termo de referência divergir. O resumo em `backend/target/evaluation-summary.json` é publicado como artefato `synthetic-evaluation-summary` na CI. As métricas `statusAccuracy`, `sourcePresenceAccuracy` e `supportedTermAccuracy` usam somente esse pequeno corpus: **não medem compreensão do modelo real** nem demonstram segurança completa contra prompt injection.

Para testar o Ollama real, use uma instância local com **banco descartável e sem documentos** nas duas organizações, com API e ambos os modelos ativos. A avaliação cria casos que permanecem no banco, exclui os documentos que criou ao final e grava um relatório local (ignorado pelo Git):

```bash
set -a
. ./.env
set +a
EVAL_ALLOW_LIVE=1 python3 evaluation/run_live.py
```

O runner recusa um banco com documentos existentes. Ele verifica abstenção em perguntas sem apoio, citações e termo esperado em um caso positivo, ausência de uma instrução maliciosa no texto final e isolamento entre organizações. As checagens são regras aproximadas; leia cada resposta e fonte antes de afirmar que o modelo está fundamentado. Resultados com Ollama variam conforme modelo, configuração e hardware. O script não envia dados externos além da API local configurada em `EVAL_BASE_URL`.

## Segurança e limites previstos

- Casos, documentos e trechos são segregados por organização nas consultas e alterações.
- Documentos e mensagens recuperados são tratados como dados não confiáveis no prompt; a validação de citações não garante fidelidade semântica.
- Propostas sem fontes ou com citações inválidas sinalizam evidência insuficiente; a aprovação exige revisão humana.
- Ações sensíveis exigirão permissão específica e aprovação humana.
- Segredos serão fornecidos por ambiente; exemplos do repositório contêm apenas placeholders.
- As operações de escrita já exigem token CSRF. Antes de atender usuários reais, a autenticação de demonstração deverá ser substituída por uma solução de identidade adequada.

## Avaliação planejada

O corpus inicial cobre respostas conhecidas, informação ausente e injeção de prompt. Ainda falta ampliá-lo com documentos conflitantes, medição semântica de afirmações, latência e custo por modelo, antes de tratar resultados como referência de produto. Publique resultados reais somente com dataset, configuração do modelo, ambiente e método de medição.

## Roadmap

- [x] Fase 0: fundação Spring Boot/Angular, PostgreSQL/pgvector, Flyway, autenticação de demonstração e CI.
- [x] Fase 1: cadastro de casos, estados, histórico e isolamento por organização.
- [x] Fase 2: ingestão de texto, trechos, embeddings e busca vetorial com fontes.
- [x] Fase 3: propostas de resolução com fontes.
- [x] Fase 4: revisão humana e trilha de decisões.
- [x] Fase 5: corpus sintético, gates de segurança e runner real opt-in.
- [ ] Fase 6: demonstração pública com dados fictícios e métricas.

Este repositório é público e usa somente exemplos fictícios. Não adicione dados de clientes, senhas, tokens nem instruções internas de trabalho.
