package dev.doughbay.fabric.staffwatch;
import com.google.gson.*;import java.nio.file.*;import java.io.*;
import net.fabricmc.loader.api.FabricLoader;import dev.doughbay.fabric.staffwatch.module.ModuleRegistry;
public final class WatchConfig {
 private static WatchConfig instance=new WatchConfig();
 public boolean staffList=true,antiVanish=true,regionAlert=true,autoDisconnect=true,showHud=true;
 public boolean enabled=true;
 public boolean alertSounds=false;
 public boolean regionSwitch=true;
 public static final String SERVER_ADDRESS="donutsmp.net";
 public static final java.util.List<String> SERVER_ADDRESSES=java.util.List.of(SERVER_ADDRESS,"east.donutsmp.net","west.donutsmp.net");
 public String serverAddress=SERVER_ADDRESS;
 public static WatchConfig get(){return instance;}
 public static Path path(){return dev.doughbay.fabric.HiveSetup.sharedDirectoryOr(FabricLoader.getInstance().getConfigDir().resolve("doughbay")).resolve("staffwatch.json");}
 public static void load(){if(Files.isRegularFile(path()))try(var reader=Files.newBufferedReader(path())){var loaded=new Gson().fromJson(reader,WatchConfig.class);if(loaded!=null)instance=loaded;}catch(Exception e){System.err.println("[Staff Watch] Invalid config; using defaults: "+e.getMessage());}}
 public String reconnectAddress(){return SERVER_ADDRESSES.contains(serverAddress)?serverAddress:SERVER_ADDRESS;}
 public void save() throws IOException {serverAddress=reconnectAddress();Files.createDirectories(path().getParent());var tmp=path().resolveSibling("staffwatch.json.tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(this));try{Files.move(tmp,path(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path(),StandardCopyOption.REPLACE_EXISTING);}apply();}
 public void apply(){for(var m:ModuleRegistry.all()){for(var s:m.settings())if(s.id().equals("server-alert"))s.set(regionAlert);m.setEnabled(enabled&&(m.id().equals("staff-list")?staffList:antiVanish));}if(!enabled||!autoDisconnect)ReconnectController.INSTANCE.cancel();}
}
