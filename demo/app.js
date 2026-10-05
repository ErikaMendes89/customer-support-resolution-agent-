import { visibleCases, decide } from './model.mjs';
const $=id=>document.getElementById(id);
const decisions=new Map();let current;
const labels={APPROVED:'Aprovada',EDITED:'Editada',REJECTED:'Rejeitada'};
function selectCase(item){
  current=item;
  for(const button of $('case-list').children)button.setAttribute('aria-pressed',String(button.dataset.id===item.id));
  $('case-id').textContent=item.id.toUpperCase();$('case-title').textContent=item.title;$('case-description').textContent=item.description;
  $('proposal').hidden=true;$('generate').disabled=false;$('feedback').textContent='';
}
function list(){
  const items=visibleCases($('organization').value);$('case-list').replaceChildren();
  for(const item of items){const b=document.createElement('button');b.type='button';b.dataset.id=item.id;b.textContent=item.title;b.addEventListener('click',()=>selectCase(item));$('case-list').append(b);}
  selectCase(items[0]);
}
function fields(){const d=$('decision').value;$('edit-fields').hidden=d!=='EDITED';$('note-fields').hidden=d==='APPROVED';$('note').required=d!=='APPROVED';$('edited-answer').required=d==='EDITED';}
function show(){
  $('proposal').hidden=false;$('generate').disabled=true;$('answer').textContent=current.answer;$('proposal-status').textContent=current.source?'Pronta para revisão':'Evidência insuficiente';
  $('sources').replaceChildren();const s=document.createElement(current.source?'blockquote':'p');s.textContent=current.source?'[S1] Manual fictício · '+current.source:'Nenhuma fonte autorizada neste cenário.';$('sources').append(s);
  $('review-form').reset();$('edited-answer').value=current.answer;
  for(const o of $('decision').options)o.disabled=!current.source&&o.value!=='REJECTED';
  $('decision').value=current.source?'APPROVED':'REJECTED';fields();
  const previous=decisions.get(current.id);$('review-form').hidden=!!previous;$('history').textContent=previous?'Decisão desta sessão: '+labels[previous.decision]+'\n'+previous.answer+(previous.note?'\nJustificativa: '+previous.note:''):'';
}
$('organization').addEventListener('change',list);$('generate').addEventListener('click',show);$('decision').addEventListener('change',fields);
$('reset').addEventListener('click',()=>{decisions.clear();list();});
$('review-form').addEventListener('submit',event=>{
  event.preventDefault();try{const result=decide(current,decisions.get(current.id),$('decision').value,$('edited-answer').value,$('note').value);decisions.set(current.id,result);show();$('feedback').textContent='Decisão registrada apenas nesta sessão.';}catch(error){$('feedback').textContent=error.message;}
});
async function metrics(){
  try{
    const response=await fetch('./metrics.json');if(!response.ok)throw new Error('Relatório indisponível');const m=await response.json();
    if(m.liveModel!==false||!m.provenance||!Number.isInteger(m.cases)||m.cases<1)throw new Error('Relatório inválido');
    const specs=[['statusAccuracy','Estados esperados'],['sourcePresenceAccuracy','Presença de fontes'],['supportedTermAccuracy','Termo esperado (1 caso)']];
    const cards=specs.map(([key,label])=>{if(typeof m[key]!=='number'||!Number.isFinite(m[key])||m[key]<0||m[key]>1)throw new Error('Métrica inválida');const card=document.createElement('div');card.className='metric';const n=document.createElement('strong');n.textContent=(m[key]*100).toFixed(0)+'%';const text=document.createElement('span');text.textContent=label;card.append(n,text);return card;});
    $('metric-cards').replaceChildren(...cards);
    const p=$('metric-provenance');p.textContent=m.cases+' cenários · '+m.dataset+' · modelos simulados · commit '+m.provenance.commit.slice(0,7)+' · ';const a=document.createElement('a');const u=new URL(m.provenance.runUrl);if(u.origin!=='https://github.com'||!u.pathname.startsWith('/ErikaMendes89/customer-support-resolution-agent-/actions/runs/'))throw new Error('Origem inválida');a.href=u.href;a.textContent='Ver execução da CI';p.append(a);
  }catch{$('metric-cards').replaceChildren();$('metric-provenance').textContent='Métricas ainda não publicadas ou indisponíveis. Execute a CI para gerar o relatório; nenhum resultado é presumido.';}
}
list();metrics();
