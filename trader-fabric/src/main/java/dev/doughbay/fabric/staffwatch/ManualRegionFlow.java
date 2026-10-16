package dev.doughbay.fabric.staffwatch;

import java.util.List;
import java.util.function.LongSupplier;

/** One explicit operator request. Never reconnects, retries a teleport or starts a stopped session. */
public final class ManualRegionFlow {
    public static final List<String> DESTINATIONS = List.of("west", "east", "asia", "eu central", "eu west", "oceania");
    public interface Client {
        boolean available();
        boolean prepare();
        boolean checkpointReady();
        void send(String destination);
        boolean arrived();
        void release();
        void resumeOwnedPause();
    }
    private final Client client;
    private final LongSupplier clock;
    private String destination = "";
    private long deadline;
    private boolean prepared, sent;
    private String status = "Choose a region, then Switch region";
    public ManualRegionFlow(Client client, LongSupplier clock) { this.client=client; this.clock=clock; }
    public boolean active() { return !destination.isEmpty(); }
    public String status() { return status; }
    public boolean request(String region) {
        if (!DESTINATIONS.contains(region)) { if(!active())status="Unknown region"; return false; }
        if (active()) return false;
        if (!client.available()) { status="Connect to DonutSMP and clear other connection actions first"; return false; }
        destination=region; deadline=clock.getAsLong()+30_000; prepared=false; sent=false;
        status="Waiting for a safe checkpoint to switch to "+region;
        return true;
    }
    public void tick() {
        if (!active()) return;
        if (!client.available()) { cancel("Region switch cancelled; trading was not resumed"); return; }
        if (clock.getAsLong()>=deadline) { cancel("Region switch timed out; check connection and resume manually if paused"); return; }
        if (!prepared) { prepared=client.prepare(); if(!prepared)return; }
        if (!sent && client.checkpointReady()) {
            sent=true; deadline=clock.getAsLong()+30_000; client.send(destination); status="Switching to "+destination+"; waiting for server confirmation";
        }
        if (sent && client.arrived()) {
            String completed=destination;
            client.release(); destination=""; prepared=false; sent=false;
            client.resumeOwnedPause(); status="Transfer confirmed: "+completed;
        }
    }
    public void cancel(String reason) {
        if(!active())return;
        client.release(); destination=""; prepared=false; sent=false; status=reason;
    }
}
