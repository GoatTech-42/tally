package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Same-user, same-machine presence only. No network, database access, or linking authority. */
final class HiveDiscovery {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static volatile HiveDiscovery current;
    private final Path registry, preferences;
    private final String instance, account, hive;
    private final Set<String> seen = new HashSet<>();
    private boolean disabled;
    private volatile String pending;
    private long pendingAt;
    private long pendingPid;
    static void start(Path game) {
        try {
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                    && Boolean.getBoolean("gonuts.hive.preview.discovery")) return;
            current = new HiveDiscovery(Path.of(System.getProperty("user.home"), ".gonuts", "local-presence"),
                    game.resolve("gonuts-hive-discovery.json"), game.toRealPath().toString(),
                    DoughBayClient.account(), HiveSetup.activeId());
            Thread worker = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try { current.refresh(System.currentTimeMillis()); }
                    catch (Exception ignored) { /* Optional discovery must never affect trading. */ }
                    try { Thread.sleep(10_000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }, "GoNuts-local-discovery");
            worker.setDaemon(true); worker.start();
        } catch (Exception ignored) { /* Fail closed: no prompt when local state cannot be read. */ }
    }
    static String takeOffer() { return current == null ? null : current.take(); }
    static void dismissAll() { if (current != null) current.disable(); }
    HiveDiscovery(Path registry, Path preferences, String instance, String account, String hive) throws Exception {
        this.registry=registry; this.preferences=preferences; this.instance=digest(instance);
        this.account=account == null || account.isBlank() ? "" : digest(account.toLowerCase(Locale.ROOT));
        this.hive=hive == null ? "" : hive;
        if (Files.exists(preferences)) {
            if (Files.size(preferences)>16_384) throw new java.io.IOException("Invalid discovery preferences");
            var j=JSON.readTree(Files.readAllBytes(preferences));
            if(j==null || j.path("v").asInt()!=1 || !j.path("disabled").isBoolean() || !j.path("seen").isArray())
                throw new java.io.IOException("Invalid discovery preferences");
            disabled=j.path("disabled").asBoolean();
            for(var key:j.path("seen")) if(key.isTextual() && key.asText().matches("[a-f0-9]{64}")) seen.add(key.asText());
        }
    }
    synchronized void refresh(long now) throws Exception {
        if(account.isEmpty()) return;
        Files.createDirectories(registry);
        var own=JSON.createObjectNode().put("v",1).put("at",now).put("instance",instance)
                .put("account",account).put("hive",hive).put("pid",ProcessHandle.current().pid());
        atomic(registry.resolve(instance+".json"),JSON.writeValueAsBytes(own));
        pending=null;
        if(disabled) return;
        // Bound all filesystem work. Untrusted/malformed records are ignored, never executed.
        try(var files=Files.list(registry)) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).limit(128).toList()) {
                try {
                    if(Files.isSymbolicLink(file) || Files.size(file)>2048) continue;
                    var j=JSON.readTree(Files.readAllBytes(file));
                    long at=j.path("at").asLong(-1),pid=j.path("pid").asLong(-1);
                    String peer=j.path("account").asText(""),peerInstance=j.path("instance").asText("");
                    if(j.path("v").asInt()!=1 || at>now || now-at>35_000 || pid<=0
                            || !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                            || !peer.matches("[a-f0-9]{64}") || !peerInstance.matches("[a-f0-9]{64}")
                            || peer.equals(account) || peerInstance.equals(instance)
                            || (!hive.isEmpty() && hive.equals(j.path("hive").asText())) || seen.contains(peer)) continue;
                    pending=peer; pendingAt=at; pendingPid=pid; break;
                } catch(Exception ignored) { /* Stale or partial peer record is not evidence. */ }
            }
        }
    }
    synchronized String take() {
        return take(System.currentTimeMillis());
    }
    synchronized String take(long now) {
        if(disabled || pending==null) return null;
        if(pendingAt>now || now-pendingAt>35_000 || !ProcessHandle.of(pendingPid).map(ProcessHandle::isAlive).orElse(false)) { pending=null; return null; }
        String key=pending; pending=null; seen.add(key);
        // Persist before offering so restarts do not repeatedly interrupt the user.
        if(!save()) { disabled=true; return null; }
        return key;
    }
    synchronized void disable() { disabled=true; pending=null; save(); }
    private boolean save() {
        try {
            var j=JSON.createObjectNode().put("v",1).put("disabled",disabled);
            var list=j.putArray("seen"); seen.stream().sorted().forEach(list::add);
            atomic(preferences,JSON.writeValueAsBytes(j)); return true;
        } catch(Exception ignored) { return false; }
    }
    private static void atomic(Path path,byte[] bytes) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(path.getParent(),"presence-",".tmp");
        try { Files.write(temp,bytes); Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
