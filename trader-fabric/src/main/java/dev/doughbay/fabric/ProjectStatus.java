package dev.doughbay.fabric;

/** Public infrastructure was retired. This is a release policy, not a user setting. */
public final class ProjectStatus {
    public static final String NOTICE = "After seven weeks of development, GoNuts is no longer publicly maintained or supported. "
            + "The source and existing releases remain available. Fork the repository to make changes or maintain your own version.";
    public static final String RETIRED = "Public services retired; local trading remains available";
    public static final String SOURCE = "https://github.com/cody-raves/Gonuts";
    public static final String PAGES = "https://cody-raves.github.io/Gonuts/";
    private ProjectStatus() {}
    public static boolean publicServicesEnabled() { return false; }
}
