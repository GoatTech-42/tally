package dev.doughbay.fabric.staffwatch.module;
import java.util.*;import dev.doughbay.fabric.staffwatch.module.impl.misc.*;
public final class ModuleRegistry {
 private static final Map<Class<?>,ClientModule> modules=new LinkedHashMap<>();
 public static void initialize(){modules.put(StaffListModule.class,new StaffListModule());modules.put(AntiVanishModule.class,new AntiVanishModule());}
 public static <T extends ClientModule> T get(Class<T> type){return type.cast(modules.get(type));}
 public static Collection<ClientModule> all(){return modules.values();}
}
