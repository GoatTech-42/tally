package dev.doughbay.fabric;

/** Same release/build identity for manual and automatic report grouping. */
final class BugReportBuild {
    private BugReportBuild() {}
    static String current() {
        String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("doughbay")
            .orElseThrow().getMetadata().getVersion().getFriendlyString();
        try(var input=BugReportBuild.class.getResourceAsStream("/doughbay-build.properties")) {
            if(input!=null) {
                var properties=new java.util.Properties();properties.load(input);
                String stamp=properties.getProperty("built_at","");
                if(stamp.matches("[0-9]{13}") && version.length()+14<=40) version+="-"+stamp;
            }
        } catch(java.io.IOException ignored) {}
        return version;
    }
}
