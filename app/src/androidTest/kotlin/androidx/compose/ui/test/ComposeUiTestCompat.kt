package androidx.compose.ui.test

fun SemanticsNodeInteraction.assertExists(): SemanticsNodeInteraction {
    fetchSemanticsNode()
    return this
}
