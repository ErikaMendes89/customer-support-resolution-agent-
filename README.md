# Customer Support Resolution Agent

Uma aplicação de portfólio para acompanhar casos de suporte e, nas próximas fases, propor resoluções fundamentadas em documentos, com revisão humana.

**Estado atual: fase 1 — casos e histórico.** A API, a autenticação de demonstração, o frontend, a migração do PostgreSQL/pgvector e o acompanhamento de casos por organização estão implementados. RAG, geração por modelo e revisão humana ainda não estão implementados.

## O que existe agora

- API Spring Boot com duas contas de demonstração separadas por organização, endpoints de casos protegidos por HTTP Basic e `GET /actuator/health` público.
- Angular para criar casos, listar, consultar detalhes, mudar estados e ler o histórico da organização autenticada.
- PostgreSQL 17 com pgvector, iniciado por Docker Compose; Flyway versiona a extensão `vector`, organizações fictícias, casos e eventos.
- CI que executa testes de integração do backend contra PostgreSQL/pgvector e compila o frontend.

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
| Banco | PostgreSQL 17, pgvector 0.8.1 | Casos, eventos e extensão vetorial para a fase futura |
| Migrações | Flyway | Esquema versionado |
| Build | Maven Wrapper, npm | Builds reproduzíveis |
| CI | GitHub Actions | Integração do backend e build do frontend |

Nenhum modelo de linguagem, provedor de embeddings ou dimensão de vetores foi escolhido ainda: essa escolha depende do fluxo e dos testes da fase de conhecimento.

## Arquitetura modular

O backend parte de um monólito modular. Cada capacidade tem suas próprias regras, casos de uso, API e adaptadores. `identity` oferece a autenticação de demonstração e o contexto da organização; `cases` contém o fluxo de casos, separados entre `domain`, `application`, `infrastructure` e `api`.

| Módulo | Situação | Responsabilidade planejada |
| --- | --- | --- |
| `identity` | Demonstração | Duas contas e o contexto da organização autenticada |
| `cases` | Implementado | Casos, transições de estado, consulta e histórico |
| `knowledge` | Planejado | Documentos, trechos, embeddings e busca |
| `resolution` | Planejado | Tentativas, orquestração e propostas |
| `review` | Planejado | Aprovação, edição e rejeição humanas |
| `audit` | Planejado | Registro de decisões relevantes |

As regras de estado ficam no domínio; as transações ficam na camada de aplicação; o repositório usa JDBC com consultas explícitas. Um caso e seu evento são gravados na mesma transação. A alteração de estado bloqueia a linha do caso até registrar o evento, para impedir duas transições simultâneas baseadas no mesmo estado. As consultas e alterações incluem o identificador da organização autenticada. A chave estrangeira composta dos eventos impede associar um evento a um caso de outra organização.

```text
backend/
  src/main/java/dev/erikamendes/support/
    identity/          # Segurança e contexto da organização
    cases/             # Domínio, aplicação, infraestrutura e API
  src/main/resources/db/migration/
    V1__enable_vector.sql
    V2__cases_and_history.sql
frontend/src/app/features/cases/  # Interface de casos
compose.yaml           # PostgreSQL/pgvector local
.github/workflows/ci.yml
```

## Como executar localmente

Pré-requisitos: Java 17, Node.js 22.12+ ou 24, npm e Docker com Compose. O projeto inclui Maven Wrapper; não é necessário instalar Maven globalmente.

1. Copie `.env.example` para `.env` na raiz e substitua as três senhas por valores locais. Se já tiver um `.env` da fase 0, acrescente `DEMO_SECONDARY_USERNAME` e `DEMO_SECONDARY_PASSWORD`. Nunca faça commit de `.env`.
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

5. Abra `http://localhost:4200` e entre com uma das duas contas do `.env`. Crie um caso e faça uma transição de estado. Saia e entre com a outra conta para conferir que os casos não aparecem ali. O servidor de desenvolvimento do Angular encaminha `/api` para `http://localhost:8080`.

Também é possível conferir a API diretamente:

```bash
curl http://localhost:8080/actuator/health
curl -u 'demo:SUA_SENHA_LOCAL' http://localhost:8080/api/v1/me
```

O valor `demo` no segundo comando deve ser substituído caso você altere `DEMO_USERNAME`.

### Contrato da API da fase 1

| Método e rota | Resultado |
| --- | --- |
| `GET /api/v1/me` | Usuário e organização autenticados |
| `GET /api/v1/me/csrf` | Emite o cookie de proteção para operações de escrita |
| `POST /api/v1/cases` | Cria um caso e seu evento inicial; recebe `title` e `description` |
| `GET /api/v1/cases?page=0&size=20` | Lista paginada da organização atual; `size` vai de 1 a 50 |
| `GET /api/v1/cases/{id}` | Caso e eventos em ordem cronológica |
| `PATCH /api/v1/cases/{id}/status` | Muda o estado; recebe `status` e `note` opcional |

As escritas exigem autenticação e o cabeçalho `X-XSRF-TOKEN` correspondente ao cookie `XSRF-TOKEN`. O Angular gerencia esse cabeçalho após chamar a rota de CSRF. Um ID inexistente ou de outra organização retorna `404`; uma transição proibida retorna `409`. O cliente não envia `organizationId` para determinar o escopo de uma operação.

Transições permitidas:

| Estado atual | Próximos estados |
| --- | --- |
| `OPEN` | `IN_PROGRESS`, `NEEDS_INFORMATION`, `RESOLVED` |
| `IN_PROGRESS` | `NEEDS_INFORMATION`, `RESOLVED` |
| `NEEDS_INFORMATION` | `IN_PROGRESS` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | Nenhum |

## Testes e CI

Com o banco ativo e as variáveis de `.env` carregadas, execute `cd backend && ./mvnw verify`. Os testes de integração verificam migrações, autenticação, proteção CSRF, transições, histórico e isolamento entre duas organizações. Os testes sobrescrevem as credenciais de demonstração com valores descartáveis.

Para conferir o frontend, execute `cd frontend && npm ci && npm run build`. A CI executa ambos os builds em tarefas separadas. O banco dos testes é efêmero e não contém dados reais.

## Segurança e limites previstos

- Casos e eventos são segregados por organização nas consultas e alterações. A busca documental também terá esse requisito.
- Documentos e mensagens recuperados serão tratados como dados, nunca como instruções para o agente.
- Respostas sem evidência suficiente deverão sinalizar incerteza e seguir para revisão.
- Ações sensíveis exigirão permissão específica e aprovação humana.
- Segredos serão fornecidos por ambiente; exemplos do repositório contêm apenas placeholders.
- As operações de escrita já exigem token CSRF. Antes de atender usuários reais, a autenticação de demonstração deverá ser substituída por uma solução de identidade adequada.

## Avaliação planejada

O conjunto de avaliação terá perguntas com respostas conhecidas, informações ausentes, documentos conflitantes e tentativas de injeção de prompt. As métricas incluirão acerto da recuperação, fidelidade das referências, taxa de respostas sem suporte, encaminhamento à revisão, isolamento entre organizações, latência e custo. Resultados só serão publicados com dataset e método de medição.

## Roadmap

- [x] Fase 0: fundação Spring Boot/Angular, PostgreSQL/pgvector, Flyway, autenticação de demonstração e CI.
- [x] Fase 1: cadastro de casos, estados, histórico e isolamento por organização.
- [ ] Fase 2: ingestão e recuperação de documentos.
- [ ] Fase 3: propostas de resolução com fontes.
- [ ] Fase 4: revisão humana e trilha de decisões.
- [ ] Fase 5: avaliação e testes de segurança do agente.
- [ ] Fase 6: demonstração pública com dados fictícios e métricas.

Este repositório é público e usa somente exemplos fictícios. Não adicione dados de clientes, senhas, tokens nem instruções internas de trabalho.
