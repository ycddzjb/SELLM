package com.sellm.qa;

public enum Intent {
    GENERAL(null, null),
    ASSESSMENT("assessment", "/assessment"),
    TEACHING("teaching", "/teaching"),
    AIDS("aids", "/aids"),
    RESEARCH("research", "/research");

    private final String routeTo;
    private final String deepLink;

    Intent(String routeTo, String deepLink) {
        this.routeTo = routeTo;
        this.deepLink = deepLink;
    }

    public String getRouteTo() { return routeTo; }
    public String getDeepLink() { return deepLink; }
}
