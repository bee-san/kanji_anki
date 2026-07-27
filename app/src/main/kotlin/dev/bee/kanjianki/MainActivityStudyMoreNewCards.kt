package dev.bee.kanjianki

import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy
import dev.bee.kanjianki.core.RecordsStudyModels
import kotlinx.coroutines.runBlocking

internal class MainActivityStudyMoreNewCards(private val study: MainActivityStudy) {
    fun availableStudyMoreNewCards(): Int {
        return study.doneActions.availableStudyMoreNewCards()
    }

    fun showStudyMoreNewCardsDialog(availableAtOpen: Int) {
        study.doneActions.showStudyMoreNewCardsDialog(availableAtOpen)
    }

    fun applyStudyMoreNewCardsRequest(countInput: EditText): Boolean {
        return study.doneActions.applyStudyMoreNewCardsRequest(countInput)
    }

    fun requestedStudyMoreNewCards(countInput: EditText): Int {
        return requestedStudyMoreNewCards(countInput.text.toString())
    }

    fun requestedStudyMoreNewCards(requestText: String): Int {
        val decision = StudyMoreNewCardsPolicy.requestedCount(requestText)
        if (!decision.accepted()) {
            Toast.makeText(study, decision.message(), Toast.LENGTH_SHORT).show()
            return -1
        }
        return decision.requestedCount()
    }

    fun startStudyMoreNewCards(requestedCount: Int): Boolean {
        val now = System.currentTimeMillis()
        val queue = runBlocking { study.studyUseCases.loadQueue(now) }
        val loadData = resolveStudyMoreNewCardsLoadData(
            study.doneActions.studyMoreNewCardsSnapshot(),
            loadRows = { queue.availableRows },
            loadExisting = { kanji ->
                runBlocking { study.studyUseCases.loadItems(kanji) }
            },
        )
        if (loadData == null) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.noNewCardsAvailableMessage(), Toast.LENGTH_SHORT).show()
            return false
        }
        val rows = loadData.rows
        val existing = loadData.existing
        val result = BridgeScheduler.withWeights(queue.schedulerFsrsWeights?.toDoubleArray()).seedExtraNewCards(
            rows,
            existing,
            queue.syncSettings,
            now,
            study.startOfDay(now),
            requestedCount,
            queue.studyLadder,
        )
        if (!result.admittedAny()) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.noNewCardsAvailableMessage(), Toast.LENGTH_SHORT).show()
            return false
        }
        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            object : StudyMoreNewCardActions.StudyItemWriter {
                override fun annotateSimilarKanjiAvailability(
                    items: List<RecordsStudyModels.StudyItem>,
                ): List<RecordsStudyModels.StudyItem> {
                    return runBlocking { study.studyUseCases.annotateCapabilities(items) }
                }

                override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
                    runBlocking { study.studyUseCases.replaceQueue(items) }
                }
            },
            study.studyMoreNewCardKanji,
            study::resetStudyRunProgress,
            study.studySessionTracker::setTargetCount
        )
        study.doneActions.clearRetainedStudyDone()
        study.continueAllKanjiSession = false
        if (admission.admittedCount < requestedCount) {
            Toast.makeText(study, StudyMoreNewCardsPolicy.partialAvailabilityMessage(admission.admittedCount), Toast.LENGTH_SHORT).show()
        }
        study.renderStudy()
        return true
    }
}

internal data class StudyMoreNewCardsLoadData(
    val rows: List<RecordsImportModels.DashboardRow>,
    val existing: List<RecordsStudyModels.StudyItem>,
)

internal data class StudyMoreNewCardsAvailability(
    val loadData: StudyMoreNewCardsLoadData,
    val availableCount: Int,
)

internal fun resolveStudyMoreNewCardsLoadData(
    snapshot: MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot?,
    loadRows: () -> List<RecordsImportModels.DashboardRow>,
    loadExisting: (List<String>) -> List<RecordsStudyModels.StudyItem>,
): StudyMoreNewCardsLoadData? {
    val rows = snapshot?.rows ?: loadRows()
    if (rows.isEmpty()) {
        return null
    }
    val existing = snapshot?.existing ?: loadExisting(rows.map { it.kanji })
    return StudyMoreNewCardsLoadData(rows, existing)
}

internal fun resolveStudyMoreNewCardsAvailability(
    snapshot: MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot?,
    cachedAvailableCount: Int?,
    loadRows: () -> List<RecordsImportModels.DashboardRow>,
    loadExisting: (List<String>) -> List<RecordsStudyModels.StudyItem>,
    countAvailable: (StudyMoreNewCardsLoadData) -> Int,
): StudyMoreNewCardsAvailability? {
    val loadData = resolveStudyMoreNewCardsLoadData(snapshot, loadRows, loadExisting) ?: return null
    if (cachedAvailableCount != null) {
        return StudyMoreNewCardsAvailability(loadData, cachedAvailableCount)
    }
    return StudyMoreNewCardsAvailability(loadData, countAvailable(loadData))
}
