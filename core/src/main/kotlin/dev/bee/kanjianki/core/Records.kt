package dev.bee.kanjianki.core

object Records {
    @JvmStatic
    fun arg(args: Array<Any?>, index: Int, context: String): Any? {
        return RecordsBase.arg(args, index, context)
    }
}
