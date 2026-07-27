package dev.bee.kanjianki

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface BrowseSelectionMutation {
    val suspended: Boolean
    val changedAtMillis: Long

    data class Single(
        val kanji: String,
        override val suspended: Boolean,
        override val changedAtMillis: Long,
    ) : BrowseSelectionMutation

    data class Bulk(
        val kanji: List<String>,
        override val suspended: Boolean,
        override val changedAtMillis: Long,
    ) : BrowseSelectionMutation
}

internal data class BrowseSelectionWriteCompletion(
    val writeId: Long,
    val browseRoute: HomeRouteRestoration,
)

private data class BrowseSelectionWriteRequest(
    val writeId: Long,
    val browseRoute: HomeRouteRestoration,
    val mutation: BrowseSelectionMutation,
)

/**
 * Serializes Browse selection writes outside the Activity-owned executor/store.
 *
 * The ViewModel and application context survive configuration changes, while the
 * replayed completion lets the recreated Activity refresh a Browse load that raced
 * the commit. Requests contain no Activity callbacks or references.
 */
internal class BrowseSelectionWriteViewModel @JvmOverloads constructor(
    application: Application,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persist: (Context, BrowseSelectionMutation) -> Unit = ::persistBrowseSelection,
) : AndroidViewModel(application) {
    private val nextWriteId = AtomicLong(0L)
    private val requests = Channel<BrowseSelectionWriteRequest>(Channel.UNLIMITED)
    private val _latestCompletion = MutableStateFlow<BrowseSelectionWriteCompletion?>(null)
    private var draftRoute: HomeRouteRestoration? = null
    private var draftText: String = ""

    val latestCompletion: StateFlow<BrowseSelectionWriteCompletion?> = _latestCompletion.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            for (request in requests) {
                try {
                    persist(getApplication<Application>().applicationContext, request.mutation)
                    _latestCompletion.value = BrowseSelectionWriteCompletion(
                        writeId = request.writeId,
                        browseRoute = request.browseRoute,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppDebugLog.logError("browse selection write failed", error)
                }
            }
        }
    }

    fun submit(
        browseRoute: HomeRouteRestoration,
        mutation: BrowseSelectionMutation,
    ): Boolean {
        require(browseRoute.destination == HomeRouteRestoration.Destination.BROWSE)
        val request = BrowseSelectionWriteRequest(
            writeId = nextWriteId.incrementAndGet(),
            browseRoute = browseRoute,
            mutation = mutation,
        )
        return requests.trySend(request).isSuccess
    }

    fun draftFor(browseRoute: HomeRouteRestoration, defaultText: String): String {
        require(browseRoute.destination == HomeRouteRestoration.Destination.BROWSE)
        if (draftRoute != browseRoute) {
            draftRoute = browseRoute
            draftText = defaultText
        }
        return draftText
    }

    fun updateDraft(browseRoute: HomeRouteRestoration, text: String) {
        require(browseRoute.destination == HomeRouteRestoration.Destination.BROWSE)
        draftRoute = browseRoute
        draftText = text
    }

    override fun onCleared() {
        requests.close()
        super.onCleared()
    }
}

private fun persistBrowseSelection(
    context: Context,
    mutation: BrowseSelectionMutation,
) {
    context.requireKaniContainer().openLocalStore().use { store ->
        when (mutation) {
            is BrowseSelectionMutation.Single -> store.setKanjiLocallySuspended(
                mutation.kanji,
                mutation.suspended,
                mutation.changedAtMillis,
            )

            is BrowseSelectionMutation.Bulk -> store.setKanjiLocallySuspendedForKanji(
                mutation.kanji,
                mutation.suspended,
                mutation.changedAtMillis,
            )
        }
    }
}
