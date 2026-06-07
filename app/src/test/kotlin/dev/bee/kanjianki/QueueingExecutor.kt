package dev.bee.kanjianki

import java.util.ArrayDeque
import java.util.concurrent.Executor

internal class QueueingExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.add(command)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun isEmpty(): Boolean {
        return tasks.isEmpty()
    }
}
