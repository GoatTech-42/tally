package dev.doughbay.fabric.automation;

/** A large retail lot must not monopolize the loop while the order desk waits. */
final class RetailBatchScheduler {
    static final int SLICE = 4;
    static final long MAX_DESK_WAIT_MILLIS = 15_000;
    private int singles;
    private long yieldStarted = -1;

    void listed(boolean retailSingle) {
        if(retailSingle)singles=Math.min(SLICE,singles+1);
        else reset();
    }
    boolean yieldToDesk(boolean onlyKnownRetailStockQueued,long now) {
        if(!onlyKnownRetailStockQueued){reset();return false;}
        if(singles<SLICE)return false;
        if(yieldStarted<0)yieldStarted=now;
        if(now-yieldStarted>=MAX_DESK_WAIT_MILLIS){reset();return false;}
        return true;
    }
    void deskActionStarted(){reset();}
    private void reset(){singles=0;yieldStarted=-1;}
}
