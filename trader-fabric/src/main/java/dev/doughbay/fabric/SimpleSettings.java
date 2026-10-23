package dev.doughbay.fabric;
import java.util.*;

/** Presentation profiles over the same tuning values. Reading or changing views never writes. */
public final class SimpleSettings {
    public record Option(String id,String label,String effect,Map<String,Double> values) { }
    public record Group(String id,String label,String description,List<Option> options) { }
    public record Change(String key,String label,double before,double after,String beforeShown,String afterShown) { }
    public record Preview(String group,String option,String effect,String expected,List<Change> changes,List<SettingsFeedback.Notice> notices) { }
    public record View(String id,String label,String description,String current,String currentId,String expected,List<Preview> previews,List<Option> options) { }
    public static final List<Group> GROUPS=List.of(
        new Group("orders","Order management","Place and manage buy orders; existing safety and spending limits still apply.",List.of(
            option("managed","Managed orders","Enables order-book reads, new bids, collecting whole stacks and margin-limited bid chasing. Leaves prices, limits and direct delivery unchanged.","orders.enabled",1,"orders.bid",1,"orders.collect",1,"orders.collect_whole_stacks",1,"orders.chase",1),
            option("pause","No new bids","Stops new buy orders only. Existing stock and order-management settings are preserved.","orders.bid",0))),
        new Group("style","Trading style","Choose bulk stock or permit individual-item strategies.",List.of(
            option("stacks","Whole stacks","Disables premium single splitting and copied rival orders; keeps sale-aware stack sizing. Existing inventory, orders and cost records are preserved. Older retail lots may wait.","list.singles",0,"underdog.enabled",0,"orders.size_by_sale",1),
            option("mixed","Mixed lots","Allows premium single splitting and rival-copy orders, including one-item lots, with sale-aware sizing. This is not a stack-only strategy.","list.singles",1,"underdog.enabled",1,"orders.size_by_sale",1))),
        new Group("pace","Action pace","Change optional waits without changing server pacing or safety gates.",List.of(
            option("standard","Standard pace","Uses normal listing waits and human step pauses. Existing timing ranges are kept.","pace.fast",0,"pace.human_steps",1),
            option("fast","Fast pace","Uses short listing waits and removes optional human step pauses. Native server gaps and trading guards remain active.","pace.fast",1,"pace.human_steps",0))),
        new Group("batch","Order batch ceiling","A maximum, not a guaranteed fill or order size. Market depth and safety caps still apply.",List.of(
            option("4","Up to 4 stacks","Changes only the batch ceiling. Existing depth, price and spending rules stay in effect.","orders.bid_stacks",4),
            option("8","Up to 8 stacks","Changes only the batch ceiling. Existing depth, price and spending rules stay in effect.","orders.bid_stacks",8),
            option("16","Up to 16 stacks","Changes only the batch ceiling. Current ordinary 64-item bids remain capped at15 stacks for exact-count recognition; other limits can reduce this further.","orders.bid_stacks",16)))
    );
    private SimpleSettings(){}
    private static Option option(String id,String label,String effect,Object... pairs){var values=new LinkedHashMap<String,Double>();for(int i=0;i<pairs.length;i+=2)values.put((String)pairs[i],((Number)pairs[i+1]).doubleValue());return new Option(id,label,effect,Collections.unmodifiableMap(values));}
    public static Group group(String id){return GROUPS.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown simple control"));}
    public static Option option(String group,String id){return group(group).options().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown simple choice"));}
    public static String current(Group group,Map<String,Double> numbers){return group.options().stream().filter(o->o.values().entrySet().stream().allMatch(e->Math.abs(numbers.getOrDefault(e.getKey(),Double.NaN)-e.getValue())<1e-9)).map(Option::id).findFirst().orElse("");}
    public static String fingerprint(Group group,Map<String,Double> numbers){var keys=new TreeSet<String>();group.options().forEach(o->keys.addAll(o.values().keySet()));var s=new StringBuilder();for(String key:keys)s.append(key).append('=').append(numbers.get(key)).append(';');return Integer.toHexString(s.toString().hashCode());}
    public static Preview preview(String groupId,String optionId,Map<String,Double> numbers,Map<String,String> texts){var group=group(groupId);var option=option(groupId,optionId);var proposed=new LinkedHashMap<>(numbers);var changes=new ArrayList<Change>();for(var e:option.values().entrySet()){var setting=Tuning.setting(e.getKey());double before=numbers.getOrDefault(e.getKey(),setting.defaultValue()),after=setting.clamp(e.getValue());proposed.put(e.getKey(),after);if(Math.abs(before-after)>1e-9)changes.add(new Change(e.getKey(),setting.label(),before,after,setting.format(before),setting.format(after)));}return new Preview(groupId,optionId,option.effect(),fingerprint(group,numbers),List.copyOf(changes),SettingsFeedback.evaluate(proposed,texts));}
    public static Preview preview(String group,String option){return preview(group,option,SettingsFeedback.numbers(),SettingsFeedback.texts());}
    public static List<View> current(){var n=SettingsFeedback.numbers();var t=SettingsFeedback.texts();return GROUPS.stream().map(g->{String id=current(g,n);String label=g.options().stream().filter(o->o.id().equals(id)).map(Option::label).findFirst().orElse("Custom — preserved");return new View(g.id(),g.label(),g.description(),label,id,fingerprint(g,n),g.options().stream().map(o->preview(g.id(),o.id(),n,t)).toList(),g.options());}).toList();}
    public static int apply(String group,String option,String expected){var preview=preview(group,option);if(!Objects.equals(expected,preview.expected()))throw new IllegalArgumentException("These settings changed since the preview. Review the new values before applying.");for(var c:preview.changes())Tuning.setting(c.key());for(var c:preview.changes())Tuning.set(c.key(),c.after());return preview.changes().size();}
}
