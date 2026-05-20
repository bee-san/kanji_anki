package dev.bee.kanjianki

import android.view.ViewGroup
import android.widget.LinearLayout

internal fun settingsPanelLayoutParams(activity: MainActivitySettings): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(0, activity.dp(8), 0, activity.dp(6))
    }
}
