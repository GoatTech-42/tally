const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const source=html.slice(html.indexOf("let imageScope="),html.indexOf('let toastTimer'));
const frames=[],shot={src:'',hidden:true},status={textContent:''};
class Frame {constructor(){frames.push(this);}async decode(){}}
const context=vm.createContext({Image:Frame,shot,$:()=>status,Date,desiredPage:'',cacheScope:'',currentPage:'MONEY',cachePages:new Set(),displayImage:(image,at)=>{shot.src=image.src;shot.hidden=false;status.textContent='';vm.runInContext('lastImageAt='+at,context);},saveFrame:()=>{}});vm.runInContext(source,context);
(async()=>{
 context.imageScopeChanged('Money|A');context.picture();context.picture();assert.equal(frames.length,1,'Only one image refresh may be in flight');
 frames[0].src='valid-A';await frames[0].onload();assert.equal(shot.src,'valid-A');assert.equal(shot.hidden,false);
 context.picture();frames[1].onerror();assert.equal(shot.src,'valid-A');assert.equal(shot.hidden,false);assert.match(status.textContent,/last successful summary/);
 context.picture();context.imageScopeChanged('Money|B');context.picture();assert.equal(shot.hidden,true,'Never show account A as account B');
 frames[2].src='late-A';await frames[2].onload();assert.equal(shot.hidden,true);
 frames[3].src='valid-B';await frames[3].onload();assert.equal(shot.src,'valid-B');assert.equal(shot.hidden,false);assert.equal(status.textContent,'');
})().catch(e=>{console.error(e);process.exitCode=1;});
