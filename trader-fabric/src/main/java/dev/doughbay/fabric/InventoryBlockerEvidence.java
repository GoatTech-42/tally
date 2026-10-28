package dev.doughbay.fabric;

/** Observed UI categories only. Reporting screens must not erase the prior trading context. */
public final class InventoryBlockerEvidence {
    public enum Code {NOT_CAPTURED,NO_WORLD,GONUTS_PANEL,REPORT_SCREEN,PLAYER_SCREEN,SERVER_CONTAINER,CURSOR,CLEAR}
    private Code current=Code.NOT_CAPTURED,prior=Code.NOT_CAPTURED;
    private long priorAt;
    public void observe(Code code,long now){
        current=code;
        if(code!=Code.REPORT_SCREEN&&code!=Code.GONUTS_PANEL){prior=code;priorAt=now;}
    }
    public String report(long now){return "ctx inventory_blocker="+current+" prior_inventory_blocker="+prior
            +" prior_inventory_blocker_age_ms="+(priorAt<=0||now<priorAt?-1:now-priorAt)+"\n";}
    public String blockerText(){return switch(current){
        case NO_WORLD->"no connected player inventory is available";
        case GONUTS_PANEL->"close the Tally panel to continue";
        case REPORT_SCREEN->"close the bug report screen to continue";
        case PLAYER_SCREEN->"close chat, inventory or the player screen to continue";
        case SERVER_CONTAINER->"a server container is still open";
        case CURSOR->"put down the item held on the cursor";
        default->null;
    };}
}
