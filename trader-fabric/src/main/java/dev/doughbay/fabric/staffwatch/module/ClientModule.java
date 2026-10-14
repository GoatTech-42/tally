package dev.doughbay.fabric.staffwatch.module;
import java.util.*;import java.util.function.Consumer;
import dev.doughbay.fabric.staffwatch.event.*;import dev.doughbay.fabric.staffwatch.module.setting.BooleanSetting;
public class ClientModule {
 private final String id,name; private boolean enabled;
 private final List<BooleanSetting> settings=new ArrayList<>(); private final List<ListenerHandle<?>> listeners=new ArrayList<>();
 private BooleanSetting notifications;
 protected ClientModule(String id,String name,String description,ModuleCategory category){this.id=id;this.name=name;}
 protected <S extends BooleanSetting> S addSetting(String id,S setting){setting.bindId(id);settings.add(setting);return setting;}
 protected BooleanSetting usesNotifications(){return notifications=addSetting("notifications",new BooleanSetting("Notifications",true));}
 public boolean notificationsOn(){return notifications==null||notifications.get();}
 public String name(){return name;} public String id(){return id;} public boolean isEnabled(){return enabled;}
 public List<BooleanSetting> settings(){return Collections.unmodifiableList(settings);}
 public void setEnabled(boolean v){if(enabled==v)return;enabled=v;
  if(v){try{EventBus.INSTANCE.register(this);onEnable();}catch(RuntimeException e){setEnabled(false);throw e;}}
  else{try{onDisable();}finally{listeners.forEach(ListenerHandle::unregister);listeners.clear();EventBus.INSTANCE.unregister(this);}}
 }
 protected void onEnable(){} protected void onDisable(){}
 protected <E extends Event> void on(EventType<E> type,Consumer<E> cb){on(type,Stage.PRE,cb);}
 protected <E extends Event> void on(EventType<E> type,Stage stage,Consumer<E> cb){listeners.add(type.listen(this,stage,EventPriority.NORMAL,cb));}
}
