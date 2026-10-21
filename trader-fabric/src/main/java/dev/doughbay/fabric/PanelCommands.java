package dev.doughbay.fabric;

import net.minecraft.client.Minecraft;

/** Client-thread adapter to the existing external-action FIFO and command pacer. */
final class PanelCommands {
    static final PanelCommandQueue QUEUE=new PanelCommandQueue();
    private PanelCommands() {}
    static PanelCommandQueue.Port port(Minecraft mc) {
        return new PanelCommandQueue.Port(){
            public Object connection(){return mc==null?null:mc.getConnection();}
            public String blocker(){return DoughBayClient.automationSessionController().panelCommandBlocker(mc);}
            public void queue(String id,String command,long expires){DoughBayClient.executionDriver().queuePanelCommand(mc,id,command,expires);}
            public String status(String id){return DoughBayClient.executionDriver().panelCommandStatus(id);}
            public boolean cancel(String id){return DoughBayClient.executionDriver().cancelPanelCommand(id);}
        };
    }
    static void tick(Minecraft mc){QUEUE.tick(port(mc),System.currentTimeMillis());}
}
