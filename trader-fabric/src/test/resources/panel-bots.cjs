const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf('async function openBots()'),html.indexOf('async function act('));
async function scenario(newPanel,kind='normal'){
 const nodes=[],changes=[];
 function el(tag,cls,text=''){
  const n={tag,className:cls,textContent:text,children:[],attrs:{},value:'',isConnected:true,
   append(...c){this.children.push(...c);},appendChild(c){this.children.push(c);},replaceChildren(...c){this.children=c;},setAttribute(k,v){this.attrs[k]=v;}};
  Object.defineProperty(n,'innerHTML',{set(){throw Error('Names and commands must be text');}});nodes.push(n);return n;
 }
 const own=el('main'),rows=[{id:'main',label:'Main',sameHive:!newPanel,self:!newPanel,hiveSize:2,commands:[]},{id:'second',label:'Second',sameHive:!newPanel,hiveSize:2,commands:[]},{id:'third',label:'Third',sameHive:!!newPanel,self:!!newPanel,hiveSize:1,commands:[]}];
 if(kind==='shared')for(const row of rows){row.sameHive=true;row.hiveSize=3;row.group='one';}
 if(kind==='single'){rows.splice(1);rows[0].hiveSize=1;}
 if(kind==='empty')rows.splice(0);
 const context=vm.createContext({own,el,JSON,view:'bots',setTimeout:()=>{},toast:()=>{},failed:()=>{throw Error('Rendering failed');},
  button:(cls,text,click)=>Object.assign(el('button',cls,text),{click}),api:async()=>({self:newPanel?'third':'main',bots:rows}),
  change:async(name,form)=>{changes.push({name,form});return form.action==='review'?{ok:true,review:'one-use',bot:'Main',command:'/pay ExamplePlayer 123'}:{ok:true};}});
 vm.runInContext(source,context);await context.openBots();
 const find=label=>nodes.find(n=>n.attrs['aria-label']===label);
 const membership=find('Hive membership');assert(membership,'Actual panel must explain membership explicitly');
 if(kind!=='normal'){
  assert.match(membership.textContent,kind==='shared'?/Same Hive confirmed: 3/:kind==='single'?/Only one running bot detected/:/No running bots detected/);
  const join=nodes.find(n=>n.textContent==='Add selected bot to Hive');assert.equal(join.disabled,true);
  await join.click();assert.equal(changes.length,0,'Already linked or missing bots must never queue a join');return;
 }
 assert.match(membership.textContent,newPanel?/Separate instances detected/:/Same Hive confirmed: 2.*1 other running/);
 assert.equal(find('Bot to add').value,'third');assert.equal(find('Existing Hive').value,'main');
 await nodes.find(n=>n.textContent==='Add selected bot to Hive').click();
 assert.equal(changes.at(-1).form.bot,'third');assert.equal(changes.at(-1).form.hive,'main');
 if(newPanel)return;
 const input=find('Manual command');input.value='/pay ExamplePlayer 123';await nodes.find(n=>n.textContent==='Review command').click();
 assert.equal(changes.at(-1).form.bot,'main');assert(nodes.some(n=>n.tag==='pre'&&n.textContent==='/pay ExamplePlayer 123'));
 const submit=nodes.find(n=>n.textContent==='Confirm and queue once');await submit.click();await submit.click();
 assert.equal(changes.filter(c=>c.form.action==='send').length,1,'A repeated click must not resubmit a payment');
 assert.equal(input.value,'/pay ExamplePlayer 123','Polling must preserve the command draft');
 await nodes.find(n=>n.textContent==='Review command').click();find('Command target bot').value='second';find('Command target bot').onchange();
 await nodes.filter(n=>n.textContent==='Confirm and queue once').at(-1).click();assert.equal(changes.filter(c=>c.form.action==='send').length,1,'Changing target invalidates the old review');
}
(async()=>{await scenario(false);await scenario(true);await scenario(false,'shared');await scenario(false,'single');await scenario(false,'empty');})().catch(e=>{console.error(e);process.exitCode=1;});
