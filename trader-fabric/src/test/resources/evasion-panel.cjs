const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf('async function openEvasion()'),html.indexOf('async function openSettings()'));
const nodes=[],ids=new Map();
function el(tag,cls,text=''){
  const node={tag,className:cls,textContent:text,children:[],style:{},value:'',
    append(...children){this.children.push(...children);},prepend(child){this.children.unshift(child);},appendChild(child){this.children.push(child);},
    replaceChildren(...children){this.children=children;},setAttribute(){}};
  Object.defineProperty(node,'id',{set(value){ids.set(value,node);},get(){return [...ids].find(([,n])=>n===node)?.[0];}});
  Object.defineProperty(node,'innerHTML',{set(){throw Error('History text must not become HTML');}});
  nodes.push(node);return node;
}
const own=el('main');let calls=0,fail=false;
const data={enabled:false,guardStatus:'off',pauseRing:128,escapeRing:120,onContact:'teleport away',homes:['base'],
  groups:[{name:'Evasion',settings:[{key:'evasion.radius_blocks'}]}],history:{available:true,triggered24h:2,
    mostSeen7d:{who:'Alex',count:3},events:[{at:1000,client:'main',who:'Alex',distance:12,action:'paused'},
      {at:900,client:'alt',who:'<img src=x onerror=alert(1)>',distance:0,action:'Staff Watch: disconnected'}]}};
const messages=[];
const actions=[];
const context=vm.createContext({view:'evasion',own,el,$:id=>ids.get(id),Date,
  api:async()=>{calls++;if(fail)throw Error('offline');return data;},change:async()=>({ok:true}),
  act:async(id,value)=>{actions.push({id,value});return true;},
  settingRow:()=>el('div','setting','Radius setting'),failed:()=>{throw Error('Evasion page did not render');},
  toast:text=>messages.push(text),button:(cls,text,click)=>Object.assign(el('button',cls,text),{click})});
vm.runInContext(source,context);
(async()=>{
  await context.openEvasion();assert(ids.has('evasion-history'));assert.equal(ids.get('evasion-guard-toggle').textContent,'Guard: OFF');
  assert(ids.get('evasion-shot').src.startsWith('/panel.png?t='));
  assert.deepEqual(actions,[{id:'page:pick',value:'EVASION'}],'Opening the image must only select its display page');
  assert(nodes.some(n=>n.tag==='td'&&n.textContent==='<img src=x onerror=alert(1)>'));
  assert(nodes.some(n=>n.tag==='td'&&n.textContent==='main'));assert(nodes.some(n=>n.tag==='td'&&n.textContent==='alt'));
  assert(nodes.some(n=>n.textContent==='Radius setting'),'Settings must remain alongside history');
  const homes=nodes.find(n=>n.tag==='input');homes.value='unfinished edit';const original=own.children;
  data.enabled=true;data.guardStatus='on, 128 blocks';await context.refreshEvasionHistory();
  assert.equal(ids.get('evasion-guard-toggle').textContent,'Guard: ON');assert.equal(homes.value,'unfinished edit');
  assert.equal(own.children,original,'Polling must not replace settings');
  fail=true;await context.refreshEvasionHistory();assert(messages.some(m=>m.includes('settings have been kept')));assert.equal(homes.value,'unfinished edit');
  context.view='settings';const before=calls;await context.refreshEvasionHistory();assert.equal(calls,before);
})().catch(error=>{console.error(error);process.exitCode=1;});
