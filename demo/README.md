# Demonstração pública — fase 6

Esta página estática apresenta o fluxo do projeto com casos e respostas fictícios
pré-definidos. Funciona sem backend, banco, Ollama ou credenciais. As decisões são
mantidas só na memória do navegador; recarregar ou reiniciar apaga a sessão.
O isolamento entre organizações nesta página é ilustrativo. As garantias reais
estão nos testes de integração do backend.

## Executar e testar

Na raiz: `python3 -m http.server 8765`. Abra `http://localhost:8765/demo/`.
Sem `metrics.json`, a página informa que as métricas estão indisponíveis.
Nunca preencha métricas manualmente para representar testes não executados.

```bash
node --test demo/model.test.mjs
python3 -m unittest discover -s demo -p 'test_*.py'
```

A CI também testa aprovação, edição, rejeição, troca de organização, reinício,
falha no carregamento das métricas e layout de 390 px com Playwright.

## Origem das métricas

O job `demo-package` depende do sucesso do backend, frontend e demonstração.
Baixa `synthetic-evaluation-summary` **da mesma execução**, valida o esquema,
gera `metrics.json` com SHA e link da execução e publica o artefato `public-demo`.
São seis cenários com modelos falsos. Não são resultados semânticos ou de latência
do Ollama. Nenhuma métrica de modelo real é publicada nesta fase sem uma avaliação
real documentada. O artefato não contém credenciais nem dados do banco.

## Publicação no GitHub Pages

1. Em Settings → Pages deste repositório, selecione **GitHub Actions** como source.
2. Em Settings → Secrets and variables → Actions → Variables, crie a variável
   **DEMO_PUBLISH** com valor **true** (não é um segredo).
3. Depois de integrar as alterações na `main`, execute novamente a CI ou faça
   um novo push. A publicação só ocorre após todos os testes passarem.
4. Confira o job `deploy-demo` e use a URL retornada pela implantação.

Desabilitar `DEMO_PUBLISH` impede novas publicações, mas não remove uma página
já publicada. A publicação fica desabilitada por padrão até configurar o Pages.
Não exponha a API de demonstração ou as contas locais como parte deste deploy.

## Roteiro de apresentação (3 minutos)

- Abra a garantia Aurora, mostre a proposta, confira [S1] e aprove.
- Reinicie; edite a resposta com justificativa mantendo [S1].
- Abra o prazo sem documentação: a proposta se abstém; rejeite com justificativa.
- Troque para Horizonte e mostre seu caso separado.
- Apresente as métricas, o link da CI e as limitações dos modelos simulados.
- Para demonstrar RAG real, execute a aplicação Java/Angular local conforme
  o README principal, com Ollama e somente documentos fictícios.
