package dev.doughbay.fabric;

/** Public infrastructure was retired. This is a release policy, not a user setting. */
public final class ProjectStatus {
    public static final String NOTICE = "GoNuts, the project Tally continues, ended public maintenance and support after seven weeks of development. "
            + "Its source and existing releases remain available. Tally is a community-maintained fork of it.";
    public static final String RETIRED = "Public services retired; local trading remains available";
    public static final String SOURCE = "https://github.com/GoatTech-42/tally";
    public static final String PAGES = "https://cody-raves.github.io/Gonuts/";
    private ProjectStatus() {}
    public static boolean publicServicesEnabled() { return false; }
}
