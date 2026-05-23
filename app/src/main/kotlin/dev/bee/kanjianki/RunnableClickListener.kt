package dev.bee.kanjianki

import android.view.View

internal class RunnableClickListener(
    private val action: Runnable?,
) : View.OnClickListener {
    override fun onClick(view: View?) {
        action!!.run()
    }
}
