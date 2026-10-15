package dev.doughbay.fabric.proxy;

import net.fabricmc.loader.api.FabricLoader;

public final class ProxyAccess {
    private ProxyAccess() {}
    private static final class Holder {
        static final ProxySettings SETTINGS = new ProxySettings(FabricLoader.getInstance().getGameDir());
    }
    public static ProxySettings settings() { return Holder.SETTINGS; }
    public static String redact(String input) {
        if(input == null) return "";
        var values = new java.util.TreeSet<String>(java.util.Comparator.comparingInt(String::length).reversed().thenComparing(java.util.Comparator.naturalOrder()));
        for(var profile:settings().state().profiles())
            for(String value:new String[]{profile.username(),profile.password(),profile.host()})
                if(!value.isEmpty())values.add(value);
        if(!values.isEmpty()) input=java.util.regex.Pattern.compile(values.stream().map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining("|")))
            .matcher(input).replaceAll("[proxy detail removed]");
        return input;
    }
}
