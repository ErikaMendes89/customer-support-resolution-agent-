// Authored fixtures illustrate the workflow; they are not model outputs or metrics.
export const cases = Object.freeze([
  {id:'aurora-warranty',organization:'aurora',title:'Garantia de um produto',description:'Meu produto apresentou falha de fabricação. Como funciona a garantia?',answer:'A garantia cobre falha de fabricação por doze meses, mediante comprovante de compra [S1]. Encaminhe o comprovante para a equipe avaliar a solicitação.',source:'A garantia cobre falha de fabricação por doze meses com comprovante de compra.'},
  {id:'aurora-missing',organization:'aurora',title:'Prazo sem documentação',description:'Qual é o prazo de entrega deste produto?',answer:'Não há evidências suficientes nos documentos deste exemplo. Solicite a política de entrega antes de orientar o cliente.',source:null},
  {id:'aurora-isolation',organization:'aurora',title:'Informação de outra organização',description:'Posso consultar a política exclusiva da Horizonte?',answer:'Nenhuma fonte autorizada está disponível neste exemplo da Aurora. Documentos da Horizonte não devem fundamentar esta resposta.',source:null},
  {id:'horizonte-warranty',organization:'horizonte',title:'Garantia Horizonte',description:'Qual comprovante é necessário para solicitar a garantia?',answer:'Apresente o comprovante de compra para a análise da garantia de doze meses [S1]. A equipe deverá revisar o caso antes de concluir o atendimento.',source:'Na organização fictícia Horizonte, a garantia de doze meses exige comprovante de compra.'}
]);
export function visibleCases(organization){return cases.filter(c=>c.organization===organization);}
export function decide(item,previous,decision,answer,note){
  if(previous)throw new Error('Este exemplo já tem uma decisão. Reinicie a sessão para tentar novamente.');
  if(!['APPROVED','EDITED','REJECTED'].includes(decision))throw new Error('Selecione uma decisão válida.');
  if(!item.source && decision!=='REJECTED')throw new Error('Uma proposta sem evidências só pode ser rejeitada.');
  answer=answer.trim();note=note.trim();
  if(decision!=='APPROVED'&&!note)throw new Error('Informe a justificativa da edição ou rejeição.');
  if(decision==='EDITED'&&(answer===item.answer||!answer||!answer.includes('[S1]')||/\[S(?!1\])\d+\]/.test(answer)))throw new Error('Edite o texto e preserve a citação [S1], sem adicionar fontes desconhecidas.');
  return {decision,answer:decision==='EDITED'?answer:item.answer,note};
}
