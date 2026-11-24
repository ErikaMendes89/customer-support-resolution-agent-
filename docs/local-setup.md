# Executar localmente

[Voltar ao projeto](../README.md) · [Fazer o primeiro caso](first-case.md)

Você precisa de Java 17, Node.js 22.12+ ou 24, npm e Docker com Compose. O repositório inclui o Maven Wrapper. Use Ollama para indexar documentos e gerar rascunhos com fontes. Com uma base vazia, você já pode criar casos e observar o resultado de evidência insuficiente com banco, API e interface.

Os comandos abaixo usam Bash. Comece na raiz do clone.

## Banco de dados

Se ainda estiver preparando o ambiente, copie o exemplo para criar seu `.env`.

```bash
cp .env.example .env
```

Abra o `.env` e defina senhas locais para `DATABASE_PASSWORD`, `DEMO_PASSWORD` e `DEMO_SECONDARY_PASSWORD`. Se estiver reaproveitando um `.env` antigo, compare as variáveis com o [modelo atualizado](../.env.example). Mantenha suas senhas locais fora do Git.

```bash
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps postgres
```

Aguarde o banco ficar saudável. O Flyway prepara o esquema e a extensão `vector` na inicialização da API. O usuário do banco local criado pelo Compose pode instalar essa extensão.

## API

No terminal da raiz, exporte as variáveis e inicie o backend.

```bash
set -a
. ./.env
set +a
cd backend
./mvnw spring-boot:run
```

Em outro terminal, confira a saúde da API e a organização da conta de demonstração.

```bash
curl http://localhost:8080/actuator/health
curl --user demo http://localhost:8080/api/v1/me
```

O health check deve indicar `UP`. O segundo comando pede a senha local. Use a conta de `DEMO_USERNAME` se você trocou o nome `demo`.

## Interface

Em outro terminal, a partir da raiz do clone, inicie o Angular.

```bash
cd frontend
npm ci
npm start
```

Abra `http://localhost:4200` e entre com uma das duas contas do `.env`. Durante o desenvolvimento, o Angular encaminha `/api` para a porta `8080`.

## Modelos de IA

Para experimentar RAG com documentos, inicie o Ollama local e deixe o servidor ativo. Se já estiver rodando, siga direto para os comandos `pull`.

```bash
ollama serve
```

Em outro terminal, baixe os modelos padrão.

```bash
ollama pull nomic-embed-text:v1.5
ollama pull qwen3:1.7b
ollama list
```

Se usar outro endereço, ajuste `OLLAMA_BASE_URL` no `.env` e reinicie a API com as variáveis atualizadas. O modelo de embeddings configurado precisa gerar **768 dimensões**. Ao trocar `OLLAMA_EMBEDDING_MODEL`, reimporte os documentos que deseja consultar com o novo modelo.

Siga o [primeiro caso guiado](first-case.md#experimento-local) para conferir as duas saídas.
