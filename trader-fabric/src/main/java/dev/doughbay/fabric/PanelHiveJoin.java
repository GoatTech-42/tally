package dev.doughbay.fabric;

import java.io.IOException;
import java.nio.file.Path;

/** Fresh-member setup advances through native checks before leaving only the requesting world. */
final class PanelHiveJoin {
    interface Client {
        boolean safe(); boolean active(); boolean cursorEmpty(); boolean connected();
        void pause(); void disconnect();
    }
    private final Path game,local;private final String account;private boolean disconnectStarted;
    private String status="Waiting for the existing Hive to register safely";
    PanelHiveJoin(Path game,Path local,String account){this.game=game;this.local=local;this.account=account;}
    String status(){return status;}
    boolean step(HiveSetup.Membership target,Client client)throws IOException {
        if(HiveSetup.configured(game)!=null)throw new IOException("This bot already has a Hive link; it was kept");
        HiveSetup.requireLocalLedger(local);HiveSetup.requireEmpty(local);
        if(!client.safe()){status="Waiting for this new bot's safe checkpoint; existing Hive members can continue";return false;}
        if(client.active()){client.pause();status="Pausing only this fresh bot before linking";return false;}
        if(target==null){status="Waiting for the existing Hive to register safely";return false;}
        if(!client.cursorEmpty()){status="Waiting for the new bot's cursor to be empty";return false;}
        if(client.connected()){status="Leaving only this fresh bot's world before linking";if(!disconnectStarted){disconnectStarted=true;client.disconnect();}return false;}
        HiveSetup.joinFresh(game,local,HiveSetup.code(target),account);
        status="Linked. Restart ONLY this new Minecraft instance normally. Existing Hive members can keep running; trading was not started";
        return true;
    }
}
