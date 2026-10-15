package dev.doughbay.fabric.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-install routing settings, deliberately outside the shared hive configuration. */
public final class ProxySettings {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Profile(String id, String name, String type, String host, int port, String username, String password) {
        public Profile {
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}") || name == null || name.isBlank() || name.length() > 48
                || !Set.of("SOCKS5", "SOCKS4").contains(type == null ? "" : type)
                || host == null || host.length() > 253 || !host.matches("[A-Za-z0-9._:-]+") || port < 1 || port > 65535
                || username == null || password == null || username.getBytes(StandardCharsets.UTF_8).length > 255
                || password.getBytes(StandardCharsets.UTF_8).length > 255 || username.indexOf(0) >= 0 || password.indexOf(0) >= 0
                || !password.isEmpty() && username.isEmpty() || type.equals("SOCKS4") && !password.isEmpty())
                throw new IllegalArgumentException("Invalid proxy fields. Use SOCKS4/5, a host, port 1–65535, and supported credentials.");
        }
        @Override public String toString() { return "Proxy profile [details hidden]"; }
    }
    public record State(boolean enabled, String selected, List<Profile> profiles) {
        public State { profiles = List.copyOf(profiles); }
    }
    private final Path file;
    private State state = new State(false, "", List.of());
    private boolean unreadable;
    public ProxySettings(Path root) {
        file = root.resolve("gonuts-proxies.json");
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 131072) throw new IllegalArgumentException();
            state = JSON.readValue(Files.readAllBytes(file), State.class);
            if (state.profiles().size() > 100 || state.profiles().stream().map(Profile::id).distinct().count() != state.profiles().size()
                || state.selected() == null || state.enabled() && selected() == null) throw new IllegalArgumentException();
        } catch (Exception e) { state = new State(true, "", List.of()); unreadable = true; }
    }
    public synchronized State state() { return state; }
    public synchronized Profile selected() { return state.profiles().stream().filter(p -> p.id().equals(state.selected())).findFirst().orElse(null); }
    public synchronized boolean blocked() { return unreadable || state.enabled() && selected() == null; }
    public synchronized String publicJson() {
        var root = JSON.createObjectNode(); root.put("enabled",state.enabled()); root.put("blocked",blocked()); root.put("selected",state.selected());
        root.put("note", blocked() ? "Proxy settings could not be loaded. Connections are blocked until you fix or explicitly disable proxy routing."
            : "For this client only. Changes apply on the next connection, including bot reconnects. Credentials stay local; reports exclude this file. A proxy does not guarantee server access.");
        var rows = root.putArray("profiles");
        for (var p: state.profiles()) { var row = rows.addObject(); row.put("id",p.id());row.put("name",p.name());row.put("type",p.type());row.put("host",p.host());row.put("port",p.port());row.put("hasUsername",!p.username().isEmpty());row.put("hasPassword",!p.password().isEmpty()); }
        return root.toString();
    }
    public synchronized String change(Map<String,String> form) {
        try {
            var list = new ArrayList<>(state.profiles()); String id = form.getOrDefault("id", "");
            var old = list.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
            State next;
            switch(form.getOrDefault("action", "")) {
                case "save" -> {
                    if (!id.isEmpty() && old == null) throw new IllegalArgumentException("That profile no longer exists.");
                    if (old == null && list.size() >= 100) throw new IllegalArgumentException("At most 100 profiles can be saved.");
                    boolean clear = "true".equals(form.get("clearCredentials"));
                    String user = form.getOrDefault("username", ""), pass = form.getOrDefault("password", "");
                    if (!clear && old != null) { if(user.isEmpty())user=old.username(); if(pass.isEmpty())pass=old.password(); }
                    if(clear) { user="";pass=""; }
                    var profile = new Profile(old == null ? UUID.randomUUID().toString() : old.id(), form.getOrDefault("name", "").trim(), form.getOrDefault("type","SOCKS5"), form.getOrDefault("host", "").trim(), Integer.parseInt(form.getOrDefault("port", "0")), user, pass);
                    if(old!=null)list.set(list.indexOf(old), profile);else list.add(profile);
                    next = new State(state.enabled() && !unreadable, old==null && state.selected().isEmpty()?profile.id():state.selected(), list);
                }
                case "enable" -> { if(old==null)throw new IllegalArgumentException("Save and choose a valid profile first."); next=new State(true,old.id(),list); }
                case "disable" -> next=new State(false,state.selected(),list);
                case "delete" -> {
                    if(old==null)throw new IllegalArgumentException("Choose a profile first.");
                    if(state.enabled() && state.selected().equals(id))throw new IllegalArgumentException("Disable this proxy before deleting it.");
                    list.remove(old); next=new State(state.enabled(),state.selected().equals(id)?"":state.selected(),list);
                }
                default -> throw new IllegalArgumentException("Unknown proxy action.");
            }
            persist(next); state=next; unreadable=false;
            return JSON.createObjectNode().put("ok",true).put("note","Saved for this client. Applies on its next connection.").toString();
        } catch (IllegalArgumentException e) {
            // No invalid input or parser exception is reflected into logs or the UI.
            return JSON.createObjectNode().put("ok",false).put("note","Could not save. Check the profile, host, port and credentials. Disable an active profile before deleting it.").toString();
        } catch (Exception e) { return JSON.createObjectNode().put("ok",false).put("note","Could not save local proxy settings. Current routing is unchanged.").toString(); }
    }
    private void persist(State next) throws Exception {
        Files.createDirectories(file.getParent()); Path temp = Files.createTempFile(file.getParent(),".gonuts-proxy-", ".tmp");
        try {
            var acl = Files.getFileAttributeView(temp,AclFileAttributeView.class);
            if(acl!=null) acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(temp)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            var posix=Files.getFileAttributeView(temp,PosixFileAttributeView.class);
            if(posix!=null)posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            Files.write(temp,JSON.writeValueAsBytes(next));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
