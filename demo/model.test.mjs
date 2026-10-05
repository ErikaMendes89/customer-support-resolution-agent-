import test from 'node:test';
import assert from 'node:assert/strict';
import {visibleCases,decide} from './model.mjs';
test('organization selection never includes another organization',()=>{
  for(const org of ['aurora','horizonte'])assert.ok(visibleCases(org).every(c=>c.organization===org));
  assert.equal(visibleCases('unknown').length,0);
});
test('unsupported proposals cannot be approved or edited',()=>{
  const item=visibleCases('aurora').find(c=>!c.source);
  for(const d of ['APPROVED','EDITED'])assert.throws(()=>decide(item,null,d,'Resposta [S1]','Motivo'));
  assert.throws(()=>decide(item,null,'REJECTED','',' '));
  assert.equal(decide(item,null,'REJECTED','','Faltam evidências').decision,'REJECTED');
});
test('edits require a changed cited answer and a reason; decisions are unique',()=>{
  const item=visibleCases('aurora')[0];
  for(const [answer,note] of [[item.answer,'Motivo'],['Novo texto','Motivo'],['Novo [S1] [S99]','Motivo'],['Novo [S1]','']])assert.throws(()=>decide(item,null,'EDITED',answer,note));
  const result=decide(item,null,'EDITED','Enviar comprovante [S1].','Clarificação');
  assert.equal(result.answer,'Enviar comprovante [S1].');
  assert.throws(()=>decide(item,result,'APPROVED','',''));
});
