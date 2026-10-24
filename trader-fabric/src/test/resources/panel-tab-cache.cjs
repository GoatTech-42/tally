const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf('// Memory-only per-tab/account frames.'),html.indexOf('let toastTimer'));
const nodes=new Map(),messages={},timers=[];let previewRequests=0,commands=0;
function node(id){if(!nodes.has(id))nodes.set(id,{id,hidden:false,textContent:'',replaceWith(){},replaceChildren(){}});return nodes.get(id);}
class Frame{async decode(){}}
let chunkMode=false;
const context=vm.createContext({Image:Frame,shot:node('shot'),$:node,Date,Map,Promise,view:'',controls:node('controls'),last:'',
 document:{hidden:false,querySelectorAll:()=>[]},put:(id,text)=>messages[id]=text,readableReport:r=>messages.report=r,
 setTimeout:fn=>{timers.push(fn);return timers.length;},
 api:async(name,q)=>{previewRequests++;assert.equal(name,'panelpreview');if(chunkMode)return {scope:q.scope,pageId:q.page,frame:chunkMode==='mixed'?'wrong':'full',chunk:Number(q.chunk),data:Number(q.chunk)===0?'original':'pixels'};return {scope:q.scope,pageId:q.page,title:q.page,image:'data:image/png;base64,fixture',ageMillis:5000,capturedAt:123,report:{example:q.scope}};},
 fetch:async()=>{commands++;return {ok:true};},URLSearchParams,refresh:()=>{}});
vm.runInContext(source,context);
vm.runInContext(html.slice(html.indexOf('async function act(id,value,text)'),html.indexOf('function press(id)')),context);
(async()=>{
 vm.runInContext("cacheScope='A';currentPage='MONEY'",context);
 await context.warmNext();assert.equal(previewRequests,1);assert.equal(commands,0,'Prefetch must not issue page/account/trading commands');
 assert.equal(vm.runInContext("tabFrames.get('A|MONEY').image.src",context),'data:image/png;base64,fixture');
 assert.equal(context.showSavedFrame('MONEY'),true);assert.equal(context.shot.hidden,false);assert.match(node('image-status').textContent,/5s ago/);
 vm.runInContext("cacheScope='B'",context);assert.equal(context.showSavedFrame('MONEY'),false);assert.equal(context.shot.hidden,true,'Never display account A as B');
 vm.runInContext("cacheScope='A'",context);context.showSavedFrame('MONEY');assert.equal(context.shot.hidden,false);
 const first=context.act('page:pick','ORDERS'),last=context.act('page:pick','MONEY');await Promise.all([first,last]);
 assert.equal(commands,1,'Skip obsolete navigation before it is sent');assert.equal(messages.page,'MONEY');
 vm.runInContext("desiredPage='';cacheScope='A';currentPage='MONEY'",context);await context.picture();assert.equal(context.shot.hidden,false);assert.equal(commands,1,'Refreshing a cached image is read only');
 vm.runInContext("imageScopeChanged('B|STATUS')",context);assert.equal(context.shot.hidden,true);
 chunkMode=true;
 const packet={scope:'A',pageId:'ORDERS',frame:'full',chunks:2,imageChars:14,capturedAt:999,ageMillis:2000,title:'Orders'};
 await context.acceptPreview(packet,'A','ORDERS');assert.equal(vm.runInContext("tabFrames.get('A|ORDERS').image.src",context),'data:image/png;base64,originalpixels','Chunks must preserve original encoded pixels');
 chunkMode='mixed';await assert.rejects(context.acceptPreview({...packet,capturedAt:1000},'A','ORDERS'),/Image changed/);
 assert.equal(vm.runInContext("tabFrames.get('A|ORDERS').sourceAt",context),999,'Failed transfer retains the complete previous image');
 for(let i=0;i<12;i++)context.saveFrame('scope'+i,'MONEY',{});assert.equal(vm.runInContext('tabFrames.size',context),8);
})().catch(e=>{console.error(e);process.exitCode=1;});
