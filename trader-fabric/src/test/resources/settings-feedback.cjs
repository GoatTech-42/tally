const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
function node(tag,text=''){return {tag,text,children:[],classList:{contains:()=>false},append(...v){this.children.push(...v);},replaceChildren(...v){this.children=v;}};}
let drawn=0,shown=[];
const S={data:{groups:[],lists:[{key:'buy.focus_items',label:'Trading focus items',section:'ITEMS'}]}};
const ctx=vm.createContext({S,view:'orders',el:(tag,cls,text)=>node(tag,text),button:(cls,text,click)=>Object.assign(node('button',text),{click}),own:{querySelector:()=>null},show:name=>{shown.push(name);ctx.view=name;},drawSettings:()=>drawn++});
vm.runInContext(html.slice(html.indexOf('function noticeCards('),html.indexOf('function draftFeedback(')),ctx);
const target=node('aside');ctx.noticeCards([{level:'warning',title:'Focus has no items',effect:'No new purchases',keys:['buy.focus_items']}],target);
const link=target.children[0].children.find(n=>n.tag==='button');assert(link);link.click();
assert.deepEqual(shown,['settings'],'Cross-category warnings must leave the Order settings shortcut');assert.equal(S.section,'ITEMS');assert.equal(S.open,'buy.focus_items');assert.equal(drawn,0,'Do not draw item controls under an Order settings heading');
link.click();assert.equal(drawn,1,'Already on Settings: render the selected category directly');

// Simple / Advanced are presentation state only: do not call the mutation transport.
let writes=0;ctx.change=()=>{writes++;throw Error('View switch attempted a settings write');};
const before=JSON.stringify(S.data);
vm.runInContext(html.slice(html.indexOf('function settingsModeBar('),html.indexOf('function drawSimpleSettings(')),ctx);
for(let i=0;i<5;i++){const bar=ctx.settingsModeBar();bar.children[0].click();assert.equal(S.simple,true);bar.children[1].click();assert.equal(S.simple,false);}
assert.equal(writes,0);assert.equal(JSON.stringify(S.data),before,'View switching preserves loaded custom values');
