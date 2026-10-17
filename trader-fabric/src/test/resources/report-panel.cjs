const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
const html = fs.readFileSync(process.argv[2], 'utf8');
const source = html.slice(html.indexOf('async function renderBugTracker('), html.indexOf('async function openProxies()'));
assert(source.includes('function poll()'));
async function scenario(reply) {
  const nodes = [], calls = [];
  const own = { replaceChildren(...children) { this.children = children; } };
  const el = (tag, cls, text = '') => {
    const node = { tag, style: {}, textContent: text, isConnected: true, value: '', disabled: false,
      children:[], attrs:{}, replaceChildren(...children){this.children=children;},append(...children){this.children.push(...children);},
      appendChild(child){this.children.push(child);},setAttribute(k,v){this.attrs[k]=v;},contains(){return false;} };
    nodes.push(node); return node;
  };
  const context = vm.createContext({ view: 'reports', reportTimer: 0, reportData: {}, own, el,
    reportNotice: async () => {}, api: async () => ({}),
    change: async (name, fields) => { calls.push({name, fields}); return reply; },
    button: (cls, text, click) => Object.assign(el('button', cls, text), {click}),
    setTimeout: () => 0, clearTimeout: () => {}, Date, AbortSignal, document:{activeElement:null},
    fetch: async (url, options) => { assert.equal(options.credentials,'omit');return {ok:true,text:async()=>JSON.stringify({available:true,generatedAt:Date.now(),snapshot:{stats:{last24h:{newReports:1,patchAvailable:0,confirmedResolved:0,unresolved:1},totals:{unresolved:1}},items:[{issue:92,summary:'Second order stalls',status:'needs_info',cause:'unknown',patch:'none',affected:{people:2,installations:1}}]}})}; } });
  vm.runInContext(source, context);
  await context.openReports(); await Promise.resolve();
  for(let i=0;i<10;i++)await Promise.resolve();
  assert(own.children, 'Report form must render without a ReferenceError');
  const inputs = nodes.filter(n => n.tag === 'textarea');
  assert.equal(inputs.length, 2); inputs[0].value = '[test] example failure'; inputs[1].value = 'Expected behavior';
  const row=nodes.find(n=>n.tag==='div'&&n.style.position==='relative');
  assert(row,'Tracker must render a compact existing-bug row: '+nodes.map(n=>n.textContent).join(' | '));
  const tip=row.children.at(-1);assert.equal(tip.hidden,true);row.onmouseenter();assert.equal(tip.hidden,false);row.onmouseleave();assert.equal(tip.hidden,true);
  assert(nodes.some(n=>n.textContent.includes('Last 24h: 1 reported')));
  inputs[0].oninput();
  const send = nodes.find(n => n.textContent === 'Send report');
  const preview = nodes.find(n => n.textContent === 'Preview (optional)');
  await send.click(); await Promise.resolve();
  assert.equal(calls[0].name, 'reports'); assert.equal(calls[0].fields.action, 'submit');
  assert.equal(calls[0].fields.consent, 'true'); assert.equal(calls[0].fields.description, '[test] example failure');
  if (!reply?.ok) {
    assert.equal(send.disabled, false, 'Failed send must allow retry');
    assert(nodes.some(n => n.textContent === (reply?.note || 'Report request failed. Please try again.')));
  }
  await preview.click(); await Promise.resolve();
  assert.equal(calls[1].fields.action, 'prepare');
  if (!reply?.ok) {
    assert.equal(preview.disabled, false, 'Failed preview must allow retry');
    assert(nodes.some(n => n.textContent === (reply?.note || 'Preview request failed. Please try again.')));
  }
}
(async () => {
  await scenario(null);
  await scenario({ok:false,note:'Please describe the failure.'});
  await scenario({ok:true,note:'Preparing locally; nothing uploaded.'});
})().catch(error => { console.error(error); process.exitCode = 1; });
