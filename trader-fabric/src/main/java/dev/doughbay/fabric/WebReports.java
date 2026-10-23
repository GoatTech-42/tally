package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Install-local panel drafts. No screen, session, or trading controls are touched here. */
public final class WebReports {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BugReportTransport TRANSPORT = new BugReportTransport();
    private static final AutomaticReportDetector DETECTOR = new AutomaticReportDetector();
    private static AutomaticReportMetadata incident;
    private static String dismissed = "";
    private static Draft draft;
    record Prepared(BugReportPayload payload, String log) {}
    interface Preparation { Prepared capture(String description, String expected, boolean includeLog) throws Exception; }
    interface Delivery { CompletableFuture<BugReportTransport.Result> send(BugReportPayload payload, String key, String log); }
    public static synchronized void sent(String fingerprint) {
        if (incident != null && incident.fingerprint().equals(fingerprint)) dismissed = fingerprint;
    }
    private static final class Draft {
        final String id = UUID.randomUUID().toString();
        final String formKey;
        final int relatedIssue;
        Draft(String key,int related) { formKey = key; relatedIssue=related; }
        final long created = System.currentTimeMillis();
        BugReportPayload payload;
        String log = "", note = "Preparing locally; nothing uploaded.", url = "";
        boolean busy = true;
        long retryAt;
    }
    private WebReports() {}

    public static synchronized void observe(AutomaticReportDetector.Observation o, long now, String build) {
        // Local failure detection does not grant permission to upload.
        var local = new AutomaticReportDetector.Observation(true, o.connected(), o.tradingRequested(),
            o.intentionalRecovery(), o.persistenceReady(), o.refusedWrite(), o.writerFailed(),
            o.signature(), o.automaticRecoveryStalled());
        var candidate = DETECTOR.observe(local, now);
        if (candidate.isPresent()) incident = AutomaticReportMetadata.of(build, "26.2", candidate.get());
    }

    public static synchronized String data() {
        expire();
        var root = JSON.createObjectNode();
        boolean visible = incident != null && !incident.fingerprint().equals(dismissed);
        root.put("alert", visible);
        root.put("incident", incident == null ? "" : incident.fingerprint());
        root.put("description", incident == null ? "" : incident.description());
        root.put("expected", incident == null ? "" : incident.expected());
        root.put("notice", visible ? "An error occurred. A sanitized report is ready to prepare, review and send." : "");
        if (draft != null) {
            root.put("id", draft.id); root.put("busy", draft.busy); root.put("note", draft.note);
            root.put("relatedIssue",draft.relatedIssue);
            root.put("preview", draft.payload == null ? "" : (draft.relatedIssue>0?"Attach to existing bug #"+draft.relatedIssue+"; no new issue will be created.\n\n":"")+draft.payload.preview() + "\n\n" + BugReporterKey.NOTICE + "\n" + SwarmReportLinks.NOTICE + "\n\nSanitized diagnostic excerpt:\n" + draft.log);
            root.put("issueUrl", draft.url); root.put("retryAt", draft.retryAt);
        }
        return root.toString();
    }

    public static synchronized String change(Map<String, String> form) {
        int related;
        try {related=form.getOrDefault("action","").equals("send")&&draft!=null?draft.relatedIssue:relatedIssue(form);}
        catch(IllegalArgumentException e){return reply(false,"Invalid related bug number.");}
        return change(form, WebReports::capture, (payload, key, log) -> TRANSPORT.sendRelated(payload, key, log,related));
    }
    static synchronized String change(Map<String, String> form, Preparation preparation, Delivery delivery) {
        expire();
        String action = form.getOrDefault("action", "");
        if (action.equals("dismiss")) {
            if (incident != null && incident.fingerprint().equals(form.get("incident"))) dismissed = incident.fingerprint();
            return reply(true, "Notice dismissed. Trading continues.");
        }
        if (action.equals("prepare") || action.equals("submit")) {
            boolean submit = action.equals("submit");
            int related;
            try {related=relatedIssue(form);}catch(IllegalArgumentException e){return reply(false,"Invalid related bug number.");}
            if(submit&&related>0)return reply(false,"Preview the existing-bug attachment first, then explicitly attach reviewed diagnostics.");
            if (submit && !"true".equals(form.get("consent"))) return reply(false, "Sending requires permission to publish this report.");
            if (draft != null && draft.busy) return reply(false, "A report operation is already running.");
            String description = form.getOrDefault("description", ""), expected = form.getOrDefault("expected", "");
            if (description.length() > 3000 || expected.length() > 3000) return reply(false, "Use at most 1500 characters per field.");
            boolean includeLog = true; // Old callers cannot turn diagnostics off.
            String key = description + "\0" + expected + "\0" + includeLog+"\0"+related;
            try { key = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            if (draft != null && draft.formKey.equals(key) && draft.payload != null) {
                if (!draft.url.isEmpty()) return reply(true, "Already received. Open the existing issue.");
                return submit ? change(Map.of("action","send","id",draft.id,"consent","true"),preparation,delivery) : reply(true,"Preview ready; nothing uploaded.");
            }
            var d = new Draft(key,related); draft = d;
            CompletableFuture.runAsync(() -> prepare(d, description, expected, includeLog, preparation, submit, delivery));
            return reply(true, "Preparing locally; nothing uploaded.");
        }
        if (!action.equals("send")) return reply(false, "Unknown report action.");
        if (draft == null || !draft.id.equals(form.get("id"))) return reply(false, "Report expired or changed. Prepare again.");
        var d = draft;
        if(d.relatedIssue>0&&!"true".equals(form.get("reviewed")))return reply(false,"Review the diagnostics before attaching them to this bug.");
        if (!"true".equals(form.get("consent")) && !"true".equals(form.get("reviewed"))) return reply(false, "Sending requires permission to publish this report.");
        if (d.busy || d.payload == null || !d.url.isEmpty() || System.currentTimeMillis() < d.retryAt)
            return reply(false, "This report cannot be sent yet.");
        d.busy = true; d.note = "Sending sanitized report...";
        delivery.send(d.payload, d.id, d.log).whenComplete((result, error) -> {
            synchronized (WebReports.class) {
                d.busy = false;
                if (error != null) { d.note = "Connection failed. Retry the same report."; return; }
                d.note = result.message(); d.retryAt = result.retryAtMillis(); d.url = result.issueUrl();
                if (result.filed() && incident != null && d.payload.description().equals(incident.description())) dismissed = incident.fingerprint();
            }
        });
        return reply(true, "Sending sanitized report...");
    }

    private static void prepare(Draft d, String description, String expected, boolean includeLog, Preparation preparation, boolean submit, Delivery delivery) {
        try {
            Prepared result = preparation.capture(description, expected, includeLog);
            result.payload().json(result.log());
            synchronized (WebReports.class) {
                d.payload = result.payload(); d.log = result.log(); d.busy = false;
                d.note = d.log.contains(BugReportLog.UNAVAILABLE)?"Historical log unavailable; current controller evidence and the availability notice are included. Nothing uploaded.":"Preview ready; sanitized diagnostics included automatically.";
                if (submit && draft == d)
                    change(Map.of("action","send","id",d.id,"consent","true"),preparation,delivery);
            }
        } catch (Exception e) {
            synchronized (WebReports.class) { d.busy = false; d.note = "Could not prepare. Check the text and receiver availability; nothing uploaded."; }
        }
    }
    private static Prepared capture(String description, String expected, boolean includeLog) throws Exception {
            var config = DoughBayClient.activeConfig();
            if (config != null && !config.apiKey().isBlank()) {
                description = description.replace(config.apiKey(), "[credential removed]");
                expected = expected.replace(config.apiKey(), "[credential removed]");
            }
            var path = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("logs/latest.log");
            var payload = new BugReportPayload(BugReportBuild.current(), "26.2", BugReportPrivacy.clean(dev.doughbay.fabric.proxy.ProxyAccess.redact(description)),
                BugReportPrivacy.clean(dev.doughbay.fabric.proxy.ProxyAccess.redact(expected)), BugReportDiagnostics.readCountersForReport(path));
            // Preview is local and must work during a reporting-service outage.
            // Delivery validates capabilities later and retains the frozen draft.
            String log = BugReportEvidence.readForReport(path,payload.description());
            payload.json(log);
            return new Prepared(payload, log);
    }
    private static void expire() {
        if (draft != null && !draft.busy && System.currentTimeMillis() - draft.created > 3_600_000) draft = null;
    }
    private static int relatedIssue(Map<String,String> form) {
        String value=form.getOrDefault("relatedIssue","0");
        if(!value.matches("0|[1-9][0-9]{0,8}"))throw new IllegalArgumentException();
        return Integer.parseInt(value);
    }
    private static String reply(boolean ok, String note) { return JSON.createObjectNode().put("ok", ok).put("note", note).toString(); }
}
