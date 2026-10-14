package dev.doughbay.fabric.staffwatch.module.setting;
import java.util.*;
public final class BooleanSetting {
 private String id; private final String name; private boolean value;
 private final List<Runnable> listeners=new ArrayList<>();
 public BooleanSetting(String name,boolean value){this.name=name;this.value=value;}
 public String id(){return id;} public void bindId(String id){this.id=id;}
 public String name(){return name;} public boolean get(){return value;}
 public void set(boolean v){if(value!=v){value=v;listeners.forEach(Runnable::run);}}
 public void onChange(Runnable r){listeners.add(r);}
}
