package dev.bee.kanjianki

import android.app.Application

class KanjiAnkiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
