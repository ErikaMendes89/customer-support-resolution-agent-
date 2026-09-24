# Customer Support Resolution Agent

Uma aplicação de portfólio para acompanhar casos de suporte e, nas próximas fases, propor resoluções fundamentadas em documentos, com revisão humana.

**Estado atual: fase 0 — fundação.** A API, a autenticação de demonstração, o frontend e a migração do PostgreSQL/pgvector estão implementados. Cadastro de casos, RAG, geração por modelo, organizações e revisão humana ainda não estão implementados.

## O que existe agora

- API Spring Boot com `GET /api/v1/me` protegido por HTTP Basic e `GET /actuator/health` público.
- Angular com uma página para verificar a conexão autenticada com a API.
- PostgreSQL 17 com pgvector, iniciado por Docker Compose; Flyway cria a extensão `vector` na primeira execução da API.
- CI que executa a integração do backend contra PostgreSQL/pgvector e compila o frontend.

As credenciais da fase 0 servem apenas à demonstração local. Elas são configuradas por ambiente, não ficam no repositório, e a senha é codificada em memória pelo backend. A interface conserva a senha somente durante a requisição de login. **HTTP Basic deve ser usado apenas em localhost ou sobre HTTPS.** Não há usuários persistentes, papéis completos nem isolamento por organização nesta fase.

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
| Backend | Java 17, Spring Boot 4.1.1 | API, segurança e configuração |
| Frontend | Angular 21, TypeScript | Interface e teste de conexão |
| Banco | PostgreSQL 17, pgvector 0.8.1 | Banco local e extensão vetorial |
| Migrações | Flyway | Esquema versionado |
| Build | Maven Wrapper, npm | Builds reproduzíveis |
| CI | GitHub Actions | Integração do backend e build do frontend |

Nenhum modelo de linguagem, provedor de embeddings ou dimensão de vetores foi escolhido ainda: essa escolha depende do fluxo e dos testes da fase de conhecimento.

## Arquitetura modular

O backend parte de um monólito modular. Cada capacidade terá suas próprias regras, casos de uso, API e adaptadores. A implementação atual contém o módulo `identity`, responsável pela autenticação de demonstração e pelo endpoint do usuário atual.

| Módulo | Situação | Responsabilidade planejada |
| --- | --- | --- |
| `identity` | Inicial | Autenticação; futuramente usuários, papéis e organizações |
| `cases` | Planejado | Casos, transições de estado e histórico |
| `knowledge` | Planejado | Documentos, trechos, embeddings e busca |
| `resolution` | Planejado | Tentativas, orquestração e propostas |
| `review` | Planejado | Aprovação, edição e rejeição humanas |
| `audit` | Planejado | Registro de decisões relevantes |

As regras de cada módulo devem permanecer fora de controllers e componentes Angular. Módulos futuros se comunicarão por interfaces e casos de uso, sem depender das classes internas de outros módulos. O frontend será dividido por funcionalidades quando essas telas forem implementadas.

```text
backend/
  src/main/java/dev/erikamendes/support/
    identity/          # Segurança e usuário atual
  src/main/resources/db/migration/
    V1__enable_vector.sql
frontend/
  src/app/             # Página inicial e conexão com a API
compose.yaml           # PostgreSQL/pgvector local
.github/workflows/ci.yml
```

## Como executar localmente

Pré-requisitos: Java 17, Node.js 22.12+ ou 24, npm e Docker com Compose. O projeto inclui Maven Wrapper; não é necessário instalar Maven globalmente.

1. Copie `.env.example` para `.env` na raiz e substitua as duas senhas por valores locais. Nunca faça commit de `.env`.
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

5. Abra `http://localhost:4200` e entre com `DEMO_USERNAME` e `DEMO_PASSWORD` do seu `.env`. O servidor de desenvolvimento do Angular encaminha `/api` para `http://localhost:8080`.

Também é possível conferir a API diretamente:

```bash
curl http://localhost:8080/actuator/health
curl -u 'demo:SUA_SENHA_LOCAL' http://localhost:8080/api/v1/me
```

O valor `demo` no segundo comando deve ser substituído caso você altere `DEMO_USERNAME`.

## Testes e CI

Com o banco ativo e as variáveis de `.env` carregadas, execute `cd backend && ./mvnw verify`. Os testes de integração verificam que a migração habilitou `vector`, que a rota privada rejeita credenciais ausentes ou inválidas e que a rota de saúde é pública. O teste sobrescreve as credenciais da aplicação com valores descartáveis; não é necessário alterar a senha local para executar a suíte.

Para conferir o frontend, execute `cd frontend && npm ci && npm run build`. A CI executa ambos os builds em tarefas separadas. O banco dos testes é efêmero e não contém dados reais.

## Segurança e limites previstos

- Dados de casos e documentos deverão ser segregados por organização em todas as consultas.
- Documentos e mensagens recuperados serão tratados como dados, nunca como instruções para o agente.
- Respostas sem evidência suficiente deverão sinalizar incerteza e seguir para revisão.
- Ações sensíveis exigirão permissão específica e aprovação humana.
- Segredos serão fornecidos por ambiente; exemplos do repositório contêm apenas placeholders.
- As fases seguintes precisarão de autenticação adequada para usuários reais e estratégia de proteção para operações de escrita.

## Avaliação planejada

O conjunto de avaliação terá perguntas com respostas conhecidas, informações ausentes, documentos conflitantes e tentativas de injeção de prompt. As métricas incluirão acerto da recuperação, fidelidade das referências, taxa de respostas sem suporte, encaminhamento à revisão, isolamento entre organizações, latência e custo. Resultados só serão publicados com dataset e método de medição.

## Roadmap

- [x] Fase 0: fundação Spring Boot/Angular, PostgreSQL/pgvector, Flyway, autenticação de demonstração e CI.
- [ ] Fase 1: cadastro de casos, estados, histórico e isolamento por organização.
- [ ] Fase 2: ingestão e recuperação de documentos.
- [ ] Fase 3: propostas de resolução com fontes.
- [ ] Fase 4: revisão humana e trilha de decisões.
- [ ] Fase 5: avaliação e testes de segurança do agente.
- [ ] Fase 6: demonstração pública com dados fictícios e métricas.

Este repositório é público e usa somente exemplos fictícios. Não adicione dados de clientes, senhas, tokens nem instruções internas de trabalho.
