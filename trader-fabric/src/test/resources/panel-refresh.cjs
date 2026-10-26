const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf('async function refresh()'),html.indexOf("if (OWN[(location.hash"));
assert(source.startsWith('async function refresh()'));
async function scenario(status){
 let notice='',parsed=0,pictures=0,drawn=0,reloaded=0;
 const context=vm.createContext({view:'',pollPending:false,navVersion:0,desiredPage:'',refreshAbort:null,AbortController,setTimeout,clearTimeout,queueWarm:()=>{},SILENT:'The game is not answering. Is it still running?',
  fetch:async()=>({ok:status===200,status,json:async()=>{parsed++;if(status!==200)throw Error('plain-text relay failure');return {page:'Status'};}}),
  put:(id,text)=>{assert.equal(id,'notice');notice=text;},draw:()=>{drawn++;notice='';},
  reportNotice:async()=>{},picture:()=>{pictures++;},location:{reload:()=>{reloaded++;}}});
 vm.runInContext(source,context);await context.refresh();
 return {notice,parsed,pictures,drawn,reloaded};
}
(async()=>{
 let release,requests=0;
 const single=vm.createContext({view:'',pollPending:false,navVersion:0,desiredPage:'',refreshAbort:null,AbortController,setTimeout,clearTimeout,queueWarm:()=>{},
  fetch:()=>{requests++;return new Promise(r=>release=r);},put:()=>{},draw:()=>{},reportNotice:async()=>{},picture:()=>{},location:{reload:()=>{}}});
 vm.runInContext(source,single);const first=single.refresh();await single.refresh();assert.equal(requests,1,'Only one status refresh may run at a time');
 release({ok:true,status:200,json:async()=>({page:'Status'})});await first;
 const busy=await scenario(429);assert.match(busy.notice,/busy.*retry/i);assert.equal(busy.parsed,0);assert.equal(busy.pictures,0);
 const timeout=await scenario(504);assert.match(timeout.notice,/timed out.*retry/i);assert.equal(timeout.parsed,0);
 const offline=await scenario(503);assert.match(offline.notice,/link.*Minecraft.*retry/i);
 const large=await scenario(413);assert.match(large.notice,/too large.*local error details/i);
 const denied=await scenario(401);assert.equal(denied.reloaded,1);assert.equal(denied.drawn,0);
 const okay=await scenario(200);assert.equal(okay.drawn,1);assert.equal(okay.pictures,1);assert.equal(okay.notice,'');
})().catch(e=>{console.error(e);process.exitCode=1;});
