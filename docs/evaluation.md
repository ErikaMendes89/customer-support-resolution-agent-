# Testes e avaliação

[Voltar ao projeto](../README.md) · [Entender os limites de segurança](security.md)

## Testes automatizados

Suba o PostgreSQL e carregue as variáveis do `.env` conforme a [configuração local](local-setup.md). Na raiz do repositório, execute o backend e o build da interface em terminais separados.

```bash
cd backend
./mvnw verify
```

```bash
cd frontend
npm ci
npm run build
```

Os testes de integração usam o banco e substituem as chamadas ao Ollama por vetores e respostas controlados. Eles cobrem migrações, autenticação, CSRF, estados e histórico de casos, busca vetorial, revisão humana e isolamento entre organizações. O [workflow de CI](../.github/workflows/ci.yml) roda o backend com um banco efêmero e compila o frontend em outro job.

## Corpus sintético na CI

O [corpus versionado](../backend/src/test/resources/evaluation/scenarios.json) contém seis cenários fictícios. O [teste de avaliação](../backend/src/test/java/dev/erikamendes/support/evaluation/AgentEvaluationTests.java) verifica resultados como rascunho fundamentado, abstenção quando falta evidência e isolamento entre organizações.

A CI publica o resumo `backend/target/evaluation-summary.json` como artefato `synthetic-evaluation-summary`. As métricas `statusAccuracy`, `sourcePresenceAccuracy` e `supportedTermAccuracy` medem acertos esperados nos modelos controlados desse corpus. Para medir qualidade semântica, confira as respostas do modelo real e compare as afirmações com as fontes.

## Avaliação com o Ollama

Para testar o Ollama real, use uma instância local com **banco descartável e sem documentos** nas duas organizações, com API e ambos os modelos ativos. A avaliação cria casos que permanecem no banco, exclui os documentos que criou ao final e grava um relatório local (ignorado pelo Git):

```bash
set -a
. ./.env
set +a
EVAL_ALLOW_LIVE=1 python3 evaluation/run_live.py
```

O runner recusa um banco com documentos existentes. Ele verifica abstenção em perguntas sem apoio, citações e termo esperado em um caso positivo, ausência de uma instrução maliciosa no texto final e isolamento entre organizações; também registra latência por caso e mediana/máximo no relatório. As checagens são regras aproximadas; leia cada resposta e fonte antes de afirmar que o modelo está fundamentado. Resultados com Ollama variam conforme modelo, configuração e hardware. Use o [runner](../evaluation/run_live.py) com `EVAL_BASE_URL` apontando para a API local.

## Próximas avaliações

Avaliar documentos conflitantes, fidelidade semântica das afirmações, distribuição de latência em diferentes ambientes e custo por modelo. Ao publicar resultados reais, informe o dataset, a configuração dos modelos, o ambiente e o método de medição.
