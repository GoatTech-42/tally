package dev.doughbay.fabric;

import java.util.*;

/** Closed validation for frozen on-disk diagnostic context, not free-form log text. */
final class ReportContextLine {
    private static final Set<String> BOOL=Set.of(("own_orders_complete armed historical_counters_available only_price_list pause_resumable paused_by_player pending_controller_checkpoint policy_ready recovered_session recovery_applied session_open uncertain_active write_refused driver_watch").split(" "));
    private static final Set<String> NUM=Set.of(("own_read_unparsed_count accepted_generation captured_at_ms committed_spend driver_cheapest_visible driver_phase_age_ms driver_target_ceiling driver_target_count driver_target_price driver_watch_age_ms durable_generation fault_age_ms fault_count last_desk_gate_age_ms last_flip_gate_age_ms last_resume_gate_age_ms own_orders_age_ms own_orders_count parked_listings pending_age_ms prior_inventory_blocker_age_ms purchase_cap queued_steps recovered_exposure_count sale_notice_age_ms session_elapsed_ms spend_cap state_age_ms trades_started").split(" "));
    static boolean valid(String line,Set<String> seen) {
        if(!line.startsWith("ctx ")||line.length()>500)return false;
        String[] pairs=line.substring(4).split(" ");if(pairs.length>12)return false;
        var keys=new HashSet<String>();
        try {
            for(String pair:pairs){
                String[] p=pair.split("=",-1);if(p.length!=2||!keys.add(p[0])||seen.contains(p[0]))return false;
                String k=p[0],v=p[1];
                if(BOOL.contains(k)){if(!v.equals("true")&&!v.equals("false"))return false;}
                else if(NUM.contains(k)||k.matches("flip[1-6]_(total|budget|bought|spent)")){if(!v.matches("-?\\d{1,19}")||Long.parseLong(v)< -1)return false;}
                else if(!code(k,v))return false;
            }
            seen.addAll(keys);return true;
        }catch(IllegalArgumentException e){return false;}
    }
    private static boolean code(String k,String v) {
        return switch(k){
            case "driver_phase" -> Set.of("IDLE","WAITING_FOR_LISTING","WAITING_FOR_CONFIRMATION","WAITING_FOR_COMPLETION","ABORTED").contains(v);
            case "driver_action" -> {AutomatedExecutionDriver.OperationIntent.valueOf(v);yield true;}
            case "state" -> {dev.doughbay.fabric.automation.AutomationSessionController.State.valueOf(v);yield true;}
            case "mode" -> Set.of("CONTINUOUS","SINGLE").contains(v);
            case "desk_action","queued_action" -> Set.of("CANCEL","COLLECT","NONE","PLACE","QUOTE","READ","SWEEP").contains(v);
            case "fault_stage" -> {FaultTrace.Stage.valueOf(v);yield true;}
            case "fault_type" -> Set.of("NONE","OTHER_EXCEPTION","ConnectException","IOException","IllegalArgumentException","IllegalStateException","NullPointerException","SQLException","SQLiteException","SocketException","TimeoutException").contains(v);
            case "last_desk_gate","last_flip_gate","last_resume_gate","reason_category" -> ActionTrace.knownReason(v)||v.equals("NOT_ATTEMPTED");
            case "persistence_state" -> Set.of("CLOSED","FAILED","LOADING","NOT_STARTED","READY","UNAVAILABLE").contains(v);
            case "inventory_blocker","prior_inventory_blocker" -> Set.of("CLEAR","CURSOR","GONUTS_PANEL","NOT_CAPTURED","NO_WORLD","PLAYER_SCREEN","REPORT_SCREEN","SERVER_CONTAINER").contains(v);
            case "tracked_status" -> Set.of("CANCELLED","EXPIRED","LIKELY_SOLD","LISTED","MISSED_BEFORE_PURCHASE","NONE","PURCHASED","SIGNAL","SIMULATED_LISTED","SIMULATED_PURCHASED","SOLD","UNRESOLVED").contains(v);
            default -> false;
        };
    }
}
