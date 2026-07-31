package dev.bee.kanjianki.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.bee.kanjianki.presentation.EffectQueue
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.PendingEffect
import dev.bee.kanjianki.presentation.UiTextResolver

const val SHELL_CONFIRM_DIALOG_TEST_TAG: String = "kani-shell-confirm"
const val SHELL_CONFIRM_ACCEPT_TEST_TAG: String = "kani-shell-confirm-accept"
const val SHELL_CONFIRM_DISMISS_TEST_TAG: String = "kani-shell-confirm-dismiss"

/**
 * What the host must do for the effects the shell cannot perform itself.
 *
 * Opening a URL, writing the clipboard, showing a file picker, and moving focus
 * are platform actions with no portable Compose equivalent. They are an interface
 * rather than four lambdas so a host cannot silently implement three of them, and
 * so a test can assert exactly which one an effect reached.
 *
 * Every method takes the already-resolved value; nothing here needs the
 * presentation types.
 */
interface ShellEffectHandler {
    fun openUrl(url: String)

    fun copyToClipboard(text: String)

    fun pickFile(purpose: KaniEffect.PickFile)

    /**
     * Moves keyboard focus to a named target.
     *
     * Named rather than a `FocusRequester`, because the requester lives in the
     * feature composable that owns the field and the effect is raised by a
     * reducer that cannot hold one. Hosts and features register their own
     * targets; an unknown name is a no-op, not a crash, because a focus request
     * that arrives after its screen left is normal.
     */
    fun requestFocus(target: String)

    companion object {
        /**
         * A handler that does nothing.
         *
         * For tests and for a host that has not wired its adapters yet. It is
         * deliberately not the default parameter anywhere: a host that silently
         * dropped every URL and clipboard write would look like it worked.
         */
        val NoOp: ShellEffectHandler = object : ShellEffectHandler {
            override fun openUrl(url: String) = Unit

            override fun copyToClipboard(text: String) = Unit

            override fun pickFile(purpose: KaniEffect.PickFile) = Unit

            override fun requestFocus(target: String) = Unit
        }
    }
}

/**
 * Delivers the head of [queue] exactly once and acknowledges it.
 *
 * Only the head, and only when it changes: keyed on the effect's id, so a
 * recomposition cannot redeliver, and consuming the head reveals the next one on
 * the following composition. That ordering is the reason two snackbars queued
 * together are both shown rather than the first being overwritten.
 *
 * Acknowledgement is the shell's job, not the caller's. An effect stays queued
 * until [dispatch] receives its [KaniAction.Consume.Effect], so an effect
 * interrupted by a process death is redelivered rather than lost.
 */
@Composable
fun ShellEffectHost(
    queue: EffectQueue,
    snackbarHostState: SnackbarHostState,
    resolver: UiTextResolver,
    handler: ShellEffectHandler,
    dispatch: (KaniAction) -> Unit,
) {
    val head = queue.head
    // A Confirm is the one effect the user has to answer, so it is state for as
    // long as the dialog is up rather than something delivered and forgotten. It
    // is consumed when the user picks, not when it is shown.
    val confirm = head?.effect as? KaniEffect.Confirm
    if (head != null && confirm != null) {
        ShellConfirmDialog(
            pending = head,
            confirm = confirm,
            resolver = resolver,
            dispatch = dispatch,
        )
        return
    }

    LaunchedEffect(head?.id) {
        val pending = head ?: return@LaunchedEffect
        when (val effect = pending.effect) {
            is KaniEffect.ShowMessage -> {
                val result = snackbarHostState.showSnackbar(
                    message = resolver.resolve(effect.message),
                    actionLabel = effect.actionLabel?.let(resolver::resolve),
                    // An error stays until dismissed; a confirmation does not
                    // need to be read to be believed.
                    duration = if (effect.isError) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    },
                )
                // The action fires only on the action, not on a timeout or a
                // swipe — a snackbar that performs its action when ignored is a
                // write the user did not ask for.
                if (result == SnackbarResult.ActionPerformed) {
                    effect.action?.let(dispatch)
                }
            }

            is KaniEffect.OpenUrl -> handler.openUrl(effect.url)

            is KaniEffect.CopyToClipboard -> {
                handler.copyToClipboard(effect.text)
                val confirmation = resolver.resolve(effect.confirmation)
                if (confirmation.isNotBlank()) {
                    snackbarHostState.showSnackbar(confirmation)
                }
            }

            is KaniEffect.PickFile -> handler.pickFile(effect)

            is KaniEffect.RequestFocus -> handler.requestFocus(effect.target)

            // Handled above, before this effect ran: a Confirm must not be
            // acknowledged until the user answers it.
            is KaniEffect.Confirm -> return@LaunchedEffect
        }
        dispatch(KaniAction.Consume.Effect(pending.id))
    }
}

@Composable
private fun ShellConfirmDialog(
    pending: PendingEffect,
    confirm: KaniEffect.Confirm,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    // Answering consumes the effect and then dispatches the answer. That order
    // matters: dispatching first can produce a state where the confirmed action's
    // own effect is queued behind a Confirm that is still head, and the dialog
    // re-shows over its own result.
    fun answer(action: KaniAction?) {
        dispatch(KaniAction.Consume.Effect(pending.id))
        action?.let(dispatch)
    }

    AlertDialog(
        onDismissRequest = { answer(null) },
        modifier = Modifier.testTag(SHELL_CONFIRM_DIALOG_TEST_TAG),
        confirmButton = {
            TextButton(
                onClick = { answer(confirm.confirm) },
                modifier = Modifier.testTag(SHELL_CONFIRM_ACCEPT_TEST_TAG),
            ) {
                Text(text = resolver.resolve(confirm.confirmLabel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { answer(null) },
                modifier = Modifier.testTag(SHELL_CONFIRM_DISMISS_TEST_TAG),
            ) {
                Text(text = resolver.resolve(confirm.dismissLabel))
            }
        },
        title = { Text(text = resolver.resolve(confirm.title)) },
        text = { Text(text = resolver.resolve(confirm.body)) },
    )
}
