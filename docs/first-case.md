# Primeiro caso guiado

[Voltar ao projeto](../README.md) · [Executar localmente](local-setup.md)

## Exemplo de garantia

Imagine um atendimento em que a pessoa pergunta quanto tempo dura a garantia de um produto com defeito de fabricação. O projeto usa um exemplo fictício do [corpus de avaliação](../backend/src/test/resources/evaluation/scenarios.json).

| Etapa | Exemplo |
| --- | --- |
| Pergunta | Qual é o prazo da garantia por falha de fabricação? |
| Fonte recuperada | A garantia cobre falha de fabricação por doze meses com comprovante de compra. |
| Rascunho ilustrativo | A garantia cobre falhas de fabricação por doze meses com comprovante de compra `[S1]`. |
| Decisão humana | Conferir `[S1]` e aprovar, editar ou rejeitar o rascunho. |

Este é o ciclo de RAG: recuperar um trecho, usar esse trecho para propor uma resposta e mostrar a fonte para revisão.

## Experimento local

Use as contas fictícias em um [ambiente local](local-setup.md). Para o primeiro passo, escolha uma organização com a base vazia.

1. Em **Casos**, crie um caso com o título `Garantia por falha de fabricação` e a descrição `Por quanto tempo a garantia cobre defeitos de fabricação e qual documento é necessário?`. Abra o caso e clique em **Gerar proposta**. Com a base vazia, o resultado esperado é **Evidências insuficientes**.
2. Com o Ollama ativo, abra **Conhecimento**. Use o título `Política de garantia fictícia`, cole `A garantia cobre falha de fabricação por doze meses com comprovante de compra.` e clique em **Indexar documento**.
3. Volte ao mesmo caso e clique em **Gerar proposta** de novo. Confira se aparece **Aguardando revisão humana** com uma fonte para o prazo e o documento necessário. A redação e o resultado podem variar conforme o modelo, a similaridade e os documentos disponíveis. Se a proposta sinalizar evidência insuficiente, use **Buscar trechos** para verificar se o documento é recuperado.
4. Compare as afirmações com o trecho citado. Escolha **Aprovar como está**, **Salvar edição e aprovar** ou **Rejeitar proposta**. Para editar ou rejeitar, informe a justificativa. Se editar, preserve uma citação a uma das fontes. A interface registra uma decisão por proposta e mantém o estado do caso.
5. Entre com a segunda conta de demonstração e abra **Casos** e **Conhecimento**. O caso e o documento criados na primeira organização ficam visíveis apenas na organização que os criou.

Se quiser entender onde cada decisão acontece, siga os [links para o código](architecture.md#roteiro-de-leitura).
