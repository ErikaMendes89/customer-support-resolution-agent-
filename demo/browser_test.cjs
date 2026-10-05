// Playwright is supplied by the CI test environment, not shipped to visitors.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
  const browser=await chromium.launch({headless:true});
  try{
    const page=await browser.newPage();const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto('http://127.0.0.1:8765/demo/');
    await page.getByRole('button',{name:'Mostrar proposta simulada'}).click();
    await page.getByRole('button',{name:'Registrar decisão simulada'}).click();
    await page.getByText('Decisão registrada apenas nesta sessão.').waitFor();
    assert.ok((await page.locator('#history').innerText()).includes('Aprovada'));
    await page.getByRole('button',{name:'Prazo sem documentação'}).click();
    await page.locator('#generate').click();
    assert.equal(await page.locator('#decision').inputValue(),'REJECTED');
    await page.locator('#note').fill('Documento ausente');await page.locator('#review-form button').click();
    assert.ok((await page.locator('#history').innerText()).includes('Rejeitada'));
    await page.locator('#organization').selectOption('horizonte');
    assert.equal(await page.locator('#case-list button').count(),1);
    await page.locator('#generate').click();await page.locator('#decision').selectOption('EDITED');
    await page.locator('#edited-answer').fill('Envie o comprovante [S1].');await page.locator('#note').fill('Resposta mais direta');await page.locator('#review-form button').click();
    assert.ok((await page.locator('#history').innerText()).includes('Envie o comprovante [S1].'));
    await page.locator('#reset').click();await page.locator('#generate').click();assert.equal(await page.locator('#review-form').isVisible(),true);
    await page.getByText('Métricas ainda não publicadas ou indisponíveis.',{exact:false}).waitFor();
    await page.route('**/metrics.json',route=>route.fulfill({json:{dataset:'synthetic-eval-v1',cases:6,liveModel:false,statusAccuracy:.5,sourcePresenceAccuracy:1,supportedTermAccuracy:1,provenance:{commit:'a'.repeat(40),runUrl:'https://github.com/ErikaMendes89/customer-support-resolution-agent-/actions/runs/123'}}}));
    await page.reload();await page.locator('.metric').first().waitFor();assert.equal(await page.locator('.metric strong').first().innerText(),'50%');
    await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:process.env.DEMO_SCREENSHOT||'/tmp/support-demo.png',fullPage:true});
    assert.deepEqual(errors,[]);console.log('Browser demo checks passed');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
