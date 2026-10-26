const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(process.argv[2],'utf8');
const script=html.slice(html.indexOf('<script>')+8,html.lastIndexOf('</script>'));
new vm.Script(script);
const own={replaceChildren(...children){this.children=children;}};
const fail=()=>{throw Error('Retired view attempted networking, submission or a timer');};
const ctx=vm.createContext({own,el:(tag,cls,text)=>({tag,text}),fetch:fail,api:fail,change:fail,setTimeout:fail});
const navigation=html.slice(html.indexOf('const OWN ='),html.indexOf('function show('));
assert(!navigation.includes("label: 'Report bug'"));
assert(!navigation.includes("label: 'Dashboard'"));
assert(!navigation.includes("label: 'Swarm savings'"));
(async()=>{
 for(const name of ['reportNotice','renderBugTracker','openReports','openDashboard','openSwarmSavings']){
  const match=html.match(new RegExp('async function '+name+'\\([^\\n]*\\) \\{[^\\n]*\\}'));
  assert(match,'Missing safe legacy route '+name);vm.runInContext(match[0],ctx);await ctx[name]();
  if(name.startsWith('open'))assert(own.children[0].text.includes('retired'));
 }
 console.log('Standalone retirement routes make no network calls or timers; full panel JS syntax valid.');
})().catch(e=>{console.error(e);process.exitCode=1;});
