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
 assert.match(c.hiveDataUseLines({}).join(' '),/unavailable.*unmeasured/);
 const actual=c.hiveDataUseLines({reportingAccounts:3,knownAccounts:3,cloudAccepted:{marketBatch:120,ordersLastRead:2},hintsConsumed:{cloudAggregate:42,followTrade:20,priceLevel:30},historyImported:{sales:50},hiveLocalReuse:{reads:3}}).join(' ');
 assert.match(actual,/122.*market batches: 120.*decisions: 42/);
 assert.match(actual,/follow activity 20.*price levels 30.*can overlap/);
 assert.match(actual,/does not prove a scan or time was saved/);
 assert.match(c.readSavingsLines({}).join(' '),/unavailable/);
 assert.match(c.readSavingsLines({attempts:1}).join(' '),/not calibrated/);
 c.api=()=>{throw Error('Retired route must not query any data source');};
 await c.openSwarmSavings();assert.match(c.own.children[0].textContent,/retired/);
 assert(!html.includes("savings: { label: 'Swarm savings'"));
 assert(!html.includes("mine('savings')"));
})().catch(e=>{console.error(e);process.exitCode=1;});
