const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
// Exercise the real navigation builder for local and hosted shells.
for(const hostedPanel of [false,true]) {
 const node=(tag,text='')=>({tag,text,children:[],attrs:{},replaceChildren(){this.children=[];},appendChild(n){this.children.push(n);},append(...nodes){this.children.push(...nodes);},setAttribute(k,v){this.attrs[k]=v;}});
 const pages=node('nav');
 const ownMap={bots:{label:'Hive & commands'},savings:{label:'Swarm savings'},reports:{label:'Report bug'},proxies:{label:'Proxies'},dashboard:{label:'Dashboard'},analyzer:{label:'Analyzer'},settings:{label:'Settings'},auction:{label:'Auction settings'},orders:{label:'Order settings'},items:{label:'Items'}};
 const mobile=node('select');
 const nav=vm.createContext({hostedPanel,pages,$:()=>mobile,mobileActions:new Map(),mobileRoutes:new Map(),OWN:ownMap,view:'settings',lastPages:'',JSON,show:()=>{},el:(tag,cls,text)=>node(tag,text),button:(cls,text,click)=>Object.assign(node('button',text),{click})});
 vm.runInContext(html.slice(html.indexOf('function tabs(pick)'),html.indexOf('function draw(state)')),nav);
 nav.tabs(null);
 const flatten=n=>[n,...n.children.flatMap(flatten)],all=flatten(pages);
 assert.equal(pages.children.length,5,'Navigation is divided into five work areas');
 assert.equal(mobile.children.length,5,'Mobile selector retains every work area');
 assert(all.some(n=>n.text==='Analyzer'),'Analyzer must be reachable in both sidebars');
 assert(all.some(n=>n.text==='Swarm savings'),'Swarm savings must be reachable in both sidebars');
 assert(all.some(n=>n.text==='Order settings'),'Settings are reachable even before the native page picker arrives');
 assert(pages.children.find(n=>n.children[0].text==='Settings').open,'Current work area is expanded');
 const dashboard=all.find(n=>n.text==='Dashboard');assert(dashboard);
 if(hostedPanel)assert.equal(dashboard.href,'https://gonuts.at/dashboard/');else assert.equal(typeof dashboard.click,'function');
}
const source=html.slice(html.indexOf('async function openSavedPresets()'),html.indexOf('async function openSettings()'));
const nodes=[];function el(tag,cls,text=''){const n={tag,className:cls,textContent:text,children:[],dataset:{},attrs:{},style:{},appendChild(c){this.children.push(c);},replaceChildren(...c){this.children=c;},setAttribute(k,v){this.attrs[k]=v;},addEventListener(t,f){this[t]=f;},select(){}};Object.defineProperty(n,'innerHTML',{set(){throw Error('User labels must remain text');}});nodes.push(n);return n;}
const own=el('main'),changes=[];
const presets={ok:true,presets:[{id:'a',name:'<img src=x onerror=alert(1)>',code:'DB1-test',savedAt:1000}]};
const report={baseline:['24h wall-clock average'],findings:[{title:'Weak market',evidence:'10 losing sales',suggestion:'Trial one replacement',estimate:'profit/hour unmeasured'}],numbers:{'buy.scale_with_bank':1},lists:{},proposedCode:'DB1-proposal'};
report.capital={at:1000000,bank:100,orders:200,awaitingCollection:50,unlisted:25,listed:75,unresolved:10,asking:9999,bankAt:1000000,ordersAt:1000000,stockAt:1000000,notes:['Observed only']};report.capitalHistory=[{...report.capital,at:700000},{...report.capital,at:1000000}];
const context=vm.createContext({document:{createElementNS:(ns,tag)=>el(tag)},own,el,Intl,Number,Math,view:'analyzer',Date,JSON,Object,confirm:()=>true,prompt:()=> 'Trial',navigator:{clipboard:{writeText:async()=>{}}},setTimeout:()=>{},toast:()=>{},failed:()=>{throw Error('Render failed');},button:(cls,text,click)=>Object.assign(el('button',cls,text),{click}),api:async name=>name==='savedpresets'?presets:{ok:true,note:'Ready',report},change:async(name,form)=>{changes.push({name,form});return {ok:true};},openSettings:()=>{}});
vm.runInContext(source,context);
(async()=>{await context.openSavedPresets();assert(nodes.some(n=>n.textContent===presets.presets[0].name));
 const rename=nodes.find(n=>n.textContent==='Rename');await rename.click();assert.equal(changes.at(-1).form.action,'rename');assert.equal(changes.at(-1).form.id,'a');
 await context.openAnalyzer();assert(nodes.some(n=>n.textContent==='What we observed'));assert(nodes.some(n=>n.textContent==='10 losing sales'));assert(nodes.some(n=>n.textContent==='What you can try'));assert(nodes.some(n=>n.tag==='details'),'Baseline can be expanded without hiding findings');assert(nodes.some(n=>n.textContent.includes('profit/hour unmeasured')));assert(nodes.some(n=>n.tag==='title'&&n.textContent.includes('21.7%')),'Asking prices must not enter the allocation denominator');assert(nodes.some(n=>n.tag==='title'&&n.textContent.includes('Snapshot')),'History bars need timestamp hover details');
 const save=nodes.find(n=>n.textContent==='Save suggestion for review');await save.click();assert.equal(changes.at(-1).form.source,'proposal');assert.equal(changes.at(-1).form.action,'save');assert(!changes.some(c=>c.form.action==='apply'),'Saving a proposal must not apply it');
})().catch(e=>{console.error(e);process.exitCode=1;});
