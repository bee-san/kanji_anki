package dev.bee.kanjianki.syncdomain

import java.util.Collections

class ImportRuleMatch private constructor(
    private val active: Boolean,
    private val suspended: Boolean,
    private val tagged: Boolean,
    private val weak: Boolean,
    private val browserQuery: Boolean,
) {
    fun matches(): Boolean = active || suspended || tagged || weak || browserQuery

    fun browserQuery(): Boolean = browserQuery

    fun forcePractice(): Boolean = suspended || tagged || weak || browserQuery

    fun sourceType(cardSuspended: Boolean): String {
        if (cardSuspended) {
            return SOURCE_SUSPENDED
        }
        if (browserQuery) {
            return SOURCE_BROWSER_QUERY
        }
        return SOURCE_ACTIVE
    }

    fun ruleTypes(cardSuspended: Boolean): List<String> {
        val rules = ArrayList<String>()
        if (active && !cardSuspended) {
            rules.add(SOURCE_ACTIVE)
        }
        if (suspended) {
            rules.add(SOURCE_SUSPENDED)
        }
        if (tagged) {
            rules.add(SOURCE_TAGGED)
        }
        if (weak) {
            rules.add(SOURCE_WEAK)
        }
        if (browserQuery) {
            rules.add(SOURCE_BROWSER_QUERY)
        }
        return Collections.unmodifiableList(rules)
    }

    companion object {
        const val SOURCE_ACTIVE: String = "active"
        const val SOURCE_SUSPENDED: String = "suspended"
        const val SOURCE_TAGGED: String = "tagged"
        const val SOURCE_WEAK: String = "weak"
        const val SOURCE_BROWSER_QUERY: String = "browser_query"

        @JvmStatic
        fun of(
            active: Boolean,
            suspended: Boolean,
            tagged: Boolean,
            weak: Boolean,
            browserQuery: Boolean,
        ): ImportRuleMatch {
            return ImportRuleMatch(active, suspended, tagged, weak, browserQuery)
        }
    }
}
