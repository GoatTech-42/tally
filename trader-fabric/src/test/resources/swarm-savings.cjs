const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf('async function openSwarmSavings()'),html.indexOf('async function openDashboard()'));
const nodes=[];
function el(tag,cls,text=''){
 const n={tag,className:cls,textContent:text,children:[],value:'',setAttribute(){},replaceChildren(...c){this.children=c;if(tag==='select')this.value=c[0]?.value||'';}};
 Object.defineProperty(n,'innerHTML',{set(){throw Error('Must render metric text safely');}});nodes.push(n);return n;
}
let rows=[{id:'a',label:'A',available:true,community:{readSavings:{attempts:4,actualSkippedReads:2,fallbackReads:2,lookupWaitMs:3000,estimateAvailable:true,estimateComplete:true,estimatedNetSavedMs:-1000,calibratedSkippedReads:2,estimatedMatchedReadSpeedRatio:.5}}},
 {id:'b',label:'B',available:true,community:{readSavings:{attempts:1,actualSkippedReads:1}}}];
const hive={reportingAccounts:2,knownAccounts:3,hasMeasurements:true,actualSkippedReads:3,attempts:5,fallbackReads:2,lookupWaitMs:5000,estimatedNetSavedMs:-2000,estimateAvailable:true,estimateComplete:false,calibratedSkippedReads:2,uncalibratedSkippedReads:1};
const c=vm.createContext({el,own:el('main'),view:'savings',document:{getElementById:id=>nodes.find(n=>n.id===id)},api:async()=>({accounts:rows,hiveReadSavings:hive}),failed:()=>{throw Error('Panel failed');}});
vm.runInContext(source,c);
(async()=>{
 assert.match(c.readSavingsLines({}).join(' '),/unavailable/);
 assert.match(c.readSavingsLines({attempts:1}).join(' '),/not calibrated/);
 await c.openSwarmSavings();const result=nodes.find(n=>n.id==='savings-result');
 const master=nodes.find(n=>n.id==='savings-hive-total');assert.match(master.children.map(n=>n.textContent).join(' '),/2 \/ 3 accounts reporting.*Scans skipped: 3.*-2.0s/);
 assert(nodes.some(n=>n.tag==='details'&&n.children.some(c=>c.tag==='summary'&&c.textContent==='Per-account details')));
 assert.match(result.children.map(n=>n.textContent).join(' '),/-1.0s.*0.50x/);
 const select=nodes.find(n=>n.id==='savings-account');select.value='b';await select.onchange();
 assert.match(result.children.map(n=>n.textContent).join(' '),/not calibrated/);
 rows[1].available=false;await c.openSwarmSavings();assert.match(result.children[0].textContent,/offline or its status is stale/);
 assert(html.includes("savings: { label: 'Swarm savings'"),'Both hosted and local panels need a standalone route');
 assert(html.includes("mine('savings')"),'The savings route must be discoverable in the navigation');
})().catch(e=>{console.error(e);process.exitCode=1;});
