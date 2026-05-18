package dev.bee.kanjianki.syncdomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportRuleMatch {
    public static final String SOURCE_ACTIVE = "active";
    public static final String SOURCE_SUSPENDED = "suspended";
    public static final String SOURCE_TAGGED = "tagged";
    public static final String SOURCE_WEAK = "weak";
    public static final String SOURCE_BROWSER_QUERY = "browser_query";

    private final boolean active;
    private final boolean suspended;
    private final boolean tagged;
    private final boolean weak;
    private final boolean browserQuery;

    private ImportRuleMatch(boolean active, boolean suspended, boolean tagged, boolean weak, boolean browserQuery) {
        this.active = active;
        this.suspended = suspended;
        this.tagged = tagged;
        this.weak = weak;
        this.browserQuery = browserQuery;
    }

    public static ImportRuleMatch of(boolean active, boolean suspended, boolean tagged, boolean weak, boolean browserQuery) {
        return new ImportRuleMatch(active, suspended, tagged, weak, browserQuery);
    }

    public boolean matches() {
        return active || suspended || tagged || weak || browserQuery;
    }

    public boolean browserQuery() {
        return browserQuery;
    }

    public boolean forcePractice() {
        return suspended || tagged || weak || browserQuery;
    }

    public String sourceType(boolean cardSuspended) {
        if (cardSuspended) {
            return SOURCE_SUSPENDED;
        }
        if (browserQuery) {
            return SOURCE_BROWSER_QUERY;
        }
        return SOURCE_ACTIVE;
    }

    public List<String> ruleTypes(boolean cardSuspended) {
        List<String> rules = new ArrayList<>();
        if (active && !cardSuspended) {
            rules.add(SOURCE_ACTIVE);
        }
        if (suspended) {
            rules.add(SOURCE_SUSPENDED);
        }
        if (tagged) {
            rules.add(SOURCE_TAGGED);
        }
        if (weak) {
            rules.add(SOURCE_WEAK);
        }
        if (browserQuery) {
            rules.add(SOURCE_BROWSER_QUERY);
        }
        return Collections.unmodifiableList(rules);
    }
}
