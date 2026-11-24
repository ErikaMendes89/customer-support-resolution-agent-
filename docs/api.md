# Contrato da API

[Voltar ao projeto](../README.md) · [Executar localmente](local-setup.md)

A API local usa `http://localhost:8080`. As operações em `/api/v1` exigem uma das contas de demonstração.

| Método e rota | Resultado |
| --- | --- |
| `GET /actuator/health` | Estado público da API |
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
