package dev.doughbay.fabric.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SharedPanelLeaseTest {
    @Test void twoClientsUseOnePanelAndTheSecondTakesOver(@TempDir Path directory) throws Exception {
        int port;
        try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        java.nio.file.Files.writeString(directory.resolve("web-panel.json"),"{\"port\":"+port+"}");
        var backend=new LocalPanel.Backend(){
            public byte[] png(){return new byte[0];}
            public String state(){return "{}";}
            public void press(String id,String value){}
            public void text(String id,String text){}
        };
        var first=WebPanelHost.start(org.slf4j.helpers.NOPLogger.NOP_LOGGER,directory,backend);
        WebPanelHost second=null;
        try {
            assertNull(first.setPin("5284"));assertTrue(first.running());
            second=WebPanelHost.start(org.slf4j.helpers.NOPLogger.NOP_LOGGER,directory,backend);
            assertFalse(second.running());assertTrue(second.status().contains("another hive client"));
            assertEquals(first.addresses(),second.addresses());
            // Takeover rebinds the same port; a parallel test can briefly hold it, so allow the host's own retry to recover.
            first.close();
            long deadline=System.currentTimeMillis()+15_000;
            while(!second.running()&&System.currentTimeMillis()<deadline){second.tick();Thread.sleep(25);}
            assertTrue(second.running());
        } finally {first.close();if(second!=null)second.close();}
    }
    @Test void aTakeoverWhosePortIsStillBusyIsRetriedAndRecovers(@TempDir Path directory) throws Exception {
        int port;
        try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        java.nio.file.Files.writeString(directory.resolve("web-panel.json"),"{\"port\":"+port+"}");
        var backend=new LocalPanel.Backend(){
            public byte[] png(){return new byte[0];}
            public String state(){return "{}";}
            public void press(String id,String value){}
            public void text(String id,String text){}
        };
        var first=WebPanelHost.start(org.slf4j.helpers.NOPLogger.NOP_LOGGER,directory,backend);
        WebPanelHost second=null;
        try {
            assertNull(first.setPin("5284"));assertTrue(first.running());
            second=WebPanelHost.start(org.slf4j.helpers.NOPLogger.NOP_LOGGER,directory,backend);
            assertFalse(second.running());
            first.close();
            try(var busy=new java.net.ServerSocket()) {
                busy.setReuseAddress(true);
                // A parallel process can grab the just-freed port first; reclaim it like the takeover does.
                long bindDeadline=System.currentTimeMillis()+15_000;
                for(;;){
                    try{busy.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),port));break;}
                    catch(java.net.BindException inUse){
                        if(System.currentTimeMillis()>=bindDeadline)throw inUse;
                        Thread.sleep(25);
                    }
                }
                second.tick();
                assertFalse(second.running());
            }
            long deadline=System.currentTimeMillis()+15_000;
            while(!second.running()&&System.currentTimeMillis()<deadline){second.tick();Thread.sleep(25);}
            assertTrue(second.running());
        } finally {first.close();if(second!=null)second.close();}
    }
    @Test void sharedFolderHasOneHostAndReleasesForTakeover(@TempDir Path directory) throws Exception {
        try(var first=SharedPanelLease.acquire(directory)) {
            assertNotNull(first);
            assertNull(SharedPanelLease.acquire(directory));
        }
        try(var next=SharedPanelLease.acquire(directory)) { assertNotNull(next); }
    }
}
