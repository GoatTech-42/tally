package dev.doughbay.fabric;

import dev.doughbay.storage.StaffActivityStore;
import java.time.*;
import java.util.*;

/** Observation coverage, not an inferred home timezone or a guarantee of future availability. */
public final class StaffActivityMetrics {
    public record Summary(String name,int intervals,long observedMillis,long firstSeen,long lastSeen,String usualHours) {
        public Summary(String name,int intervals,long observedMillis,long lastSeen,String usualHours){this(name,intervals,observedMillis,lastSeen,lastSeen,usualHours);}
    }
    private StaffActivityMetrics() {}
    public static List<StaffActivityStore.Interval> union(Collection<StaffActivityStore.Interval> intervals) {
        var sorted=intervals.stream().filter(i->i.to()>=i.from()).sorted(Comparator.comparingLong(StaffActivityStore.Interval::from)).toList();
        var merged=new ArrayList<StaffActivityStore.Interval>();
        for(var interval:sorted) {
            if(merged.isEmpty() || interval.from()>merged.getLast().to()) merged.add(interval);
            else {var last=merged.removeLast();merged.add(new StaffActivityStore.Interval(last.from(),Math.max(last.to(),interval.to())));}
        }
        return List.copyOf(merged);
    }
    public static long duration(Collection<StaffActivityStore.Interval> intervals) {return union(intervals).stream().mapToLong(i->i.to()-i.from()).sum();}
    public static List<Summary> summarize(StaffActivityStore.Report report,ZoneId zone) {
        var grouped=new TreeMap<String,List<StaffActivityStore.Session>>(String.CASE_INSENSITIVE_ORDER);
        for(var session:report.sessions()) grouped.computeIfAbsent(session.name(),k->new ArrayList<>()).add(session);
        var covered=union(report.coverage());var summaries=new ArrayList<Summary>();
        for(var group:grouped.entrySet()) {
            var seen=union(group.getValue().stream().map(s->new StaffActivityStore.Interval(s.from(),s.to())).toList());
            long last=seen.stream().mapToLong(StaffActivityStore.Interval::to).max().orElse(0);
            summaries.add(new Summary(group.getKey(),seen.size(),duration(seen),seen.stream().mapToLong(StaffActivityStore.Interval::from).min().orElse(0),last,usualHours(covered,seen,zone)));
        }
        return summaries.stream().sorted(Comparator.comparingLong(Summary::lastSeen).reversed()).toList();
    }
    private static String usualHours(List<StaffActivityStore.Interval> covered,List<StaffActivityStore.Interval> seen,ZoneId zone) {
        long[] denominator=new long[24],numerator=new long[24];
        List<Set<LocalDate>> days=new ArrayList<>();for(int i=0;i<24;i++)days.add(new HashSet<>());
        split(covered,zone,denominator,days);split(seen,zone,numerator,null);
        List<Integer> eligible=new ArrayList<>();
        for(int hour=0;hour<24;hour++) if(days.get(hour).size()>=3 && denominator[hour]>=3_600_000 && numerator[hour]>0) eligible.add(hour);
        eligible.sort(Comparator.<Integer>comparingDouble(h->(double)numerator[h]/denominator[h]).reversed());
        if(eligible.isEmpty()) return "Insufficient coverage (needs 3 days and 1h watched in an hour band)";
        return eligible.stream().limit(3).map(h->String.format(Locale.ROOT,"%02d:00 (%d%% observed)",h,Math.min(100,Math.round(100.0*numerator[h]/denominator[h]))))
            .collect(java.util.stream.Collectors.joining(", "));
    }
    private static void split(List<StaffActivityStore.Interval> intervals,ZoneId zone,long[] totals,List<Set<LocalDate>> days) {
        for(var interval:intervals) {
            long at=interval.from();
            while(at<interval.to()) {
                var local=Instant.ofEpochMilli(at).atZone(zone);
                long boundary=local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli();
                long end=Math.min(interval.to(),boundary);
                if(end<=at) break;
                totals[local.getHour()]+=end-at;if(days!=null)days.get(local.getHour()).add(local.toLocalDate());at=end;
            }
        }
    }
    public static String durationLabel(long millis) {
        long minutes=Math.max(0,millis)/60_000;
        return minutes>=60?minutes/60+"h "+minutes%60+"m":minutes+"m "+Math.max(0,millis)/1000%60+"s";
    }
}
