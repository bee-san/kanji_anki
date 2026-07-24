package dev.bee.kanjianki

import android.content.Intent
import dev.bee.kanjianki.anki.AnkiDroidCollectionInventoryGateway
import dev.bee.kanjianki.anki.AnkiKanjiInventoryReader
import dev.bee.kanjianki.anki.AnkiMissingKanjiWriter
import dev.bee.kanjianki.core.AnkiKanjiInventoryProgress
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiAnalyzer
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.MissingKanjiExportReceipt
import dev.bee.kanjianki.data.MissingKanjiInventoryState
import dev.bee.kanjianki.data.MissingKanjiPreferences
import dev.bee.kanjianki.data.MissingKanjiScanStatus
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal abstract class MainActivityMissingKanji : MainActivityHome() {
    @Volatile
    private var activeMissingKanjiPreferences = MissingKanjiPreferences()

    @Volatile
    private var activeInventoryPermission: String? = null

    @Volatile
    private var activeMissingKanjiScan: MissingKanjiScanTask? = null

    @Volatile
    private var activeMissingKanjiExport: MissingKanjiExportTask? = null

    @Volatile
    private var activeMissingKanjiCandidatesByLiteral: Map<String, MissingKanjiCandidate> = emptyMap()

    @Volatile
    private var activeMissingKanjiOperationResult: MissingKanjiOperationResultModel? = null

    @Volatile
    private var activeMissingKanjiCsvFallbackLiterals: Set<String> = emptySet()

    private val missingKanjiOperationInProgress = AtomicBoolean(false)

    override fun renderMissingKanji() {
        currentHomeRouteRestoration = HomeRouteRestoration.missingKanji()
        if (isScreenshotLaunchRequested()) {
            renderScreenshotMissingKanji()
            return
        }
        activeMissingKanjiScan?.let { task ->
            renderMissingKanjiScanning(task)
            return
        }
        renderAsyncHomeRoute(
            loadingTitle = MissingKanjiTextCopy.title(),
            load = ::loadMissingKanjiScreenModel,
            render = { model ->
                renderHomeRoute(
                    backAction = Runnable { renderHome() },
                    managedScroll = true,
                ) {
                    MissingKanjiScreen(model)
                }
            },
            traceName = "missing-kanji-route",
        )
    }

    open override fun renderHome() {
        cancelMissingKanjiScan()
        cancelMissingKanjiExport()
        super.renderHome()
    }

    override fun onDestroy() {
        activeMissingKanjiScan?.cancellation?.set(true)
        activeMissingKanjiExport?.cancellation?.set(true)
        super.onDestroy()
    }

    private fun loadMissingKanjiScreenModel(): MissingKanjiScreenModel {
        val repository = store.missingKanjiStore()
        val preferences = repository.loadPreferences()
        activeMissingKanjiPreferences = preferences
        val capability = AnkiDroidCollectionInventoryGateway(this).status()
        activeInventoryPermission = capability.permission
        val availability = capability.toAvailability()
        val inventoryState = repository.inventoryState()
        val content = inventoryState.published?.let { published ->
            try {
                val dictionary = warmDictionaryLookup()
                check(dictionary.kanjiCount() > 0) {
                    "The offline dictionary contains no kanji."
                }
                val report = MissingKanjiReportLoader.load(
                    dictionary = dictionary,
                    observedKanji = published.literals,
                    range = preferences.range,
                )
                activeMissingKanjiCandidatesByLiteral = report.missing.associateBy(
                    MissingKanjiCandidate::literal,
                )
                val activeManualLiterals = repository.manualSources()
                    .mapTo(HashSet()) { source -> source.candidate.literal }
                val removableManualLiterals = repository.removableManualSourceLiterals()
                MissingKanjiContentModel.Report(
                    MissingKanjiReportUiModel(
                        reportKey = reportKey(
                            scanId = published.scan.id,
                            range = preferences.range,
                        ),
                        scan = MissingKanjiScanSummaryModel(
                            scanId = published.scan.id,
                            completedAtMillis = published.scan.completedAt,
                            notesScanned = published.scan.notesScanned,
                            uniqueAnkiKanjiCount = report.uniqueObservedKanjiCount,
                            skippedNotes = published.scan.skippedNotes,
                        ),
                        eligibleDictionaryKanjiCount = report.eligibleDictionaryKanjiCount,
                        missingKanjiCount = report.missingKanjiCount,
                        rows = missingKanjiRows(
                            candidates = report.missing,
                            activeManualLiterals = activeManualLiterals,
                            removableManualLiterals = removableManualLiterals,
                        ),
                        staleReason = staleReason(inventoryState, System.currentTimeMillis()),
                    ),
                )
            } catch (_: Exception) {
                activeMissingKanjiCandidatesByLiteral = emptyMap()
                MissingKanjiContentModel.Error(FAILURE_DICTIONARY_UNAVAILABLE)
            }
        } ?: run {
            activeMissingKanjiCandidatesByLiteral = emptyMap()
            contentWithoutPublishedInventory(
            availability = availability,
            inventoryState = inventoryState,
            )
        }
        return missingKanjiScreenModel(
            content = content,
            availability = availability,
            preferences = preferences,
            capability = capability,
        )
    }

    private fun contentWithoutPublishedInventory(
        availability: MissingKanjiProviderAvailability,
        inventoryState: MissingKanjiInventoryState,
    ): MissingKanjiContentModel {
        return when (availability) {
            MissingKanjiProviderAvailability.NOT_INSTALLED ->
                MissingKanjiContentModel.AnkiDroidMissing
            MissingKanjiProviderAvailability.PERMISSION_REQUIRED ->
                MissingKanjiContentModel.PermissionRequired
            MissingKanjiProviderAvailability.UNAVAILABLE ->
                MissingKanjiContentModel.Error(FAILURE_PROVIDER_UNAVAILABLE)
            MissingKanjiProviderAvailability.READY -> {
                val latest = inventoryState.latestAttempt
                if (latest == null || latest.status == MissingKanjiScanStatus.SUCCESS) {
                    MissingKanjiContentModel.FirstRun
                } else {
                    MissingKanjiContentModel.Error(
                        latest.failureCode.ifBlank {
                            if (latest.status == MissingKanjiScanStatus.CANCELLED) {
                                FAILURE_CANCELLED
                            } else {
                                FAILURE_PROVIDER_UNAVAILABLE
                            }
                        },
                    )
                }
            }
        }
    }

    private fun missingKanjiScreenModel(
        content: MissingKanjiContentModel,
        availability: MissingKanjiProviderAvailability,
        preferences: MissingKanjiPreferences,
        capability: AnkiDroidCollectionInventoryGateway.CapabilityStatus,
    ): MissingKanjiScreenModel {
        val primaryAction = primaryAction(content, availability)
        val hasReport = content is MissingKanjiContentModel.Report
        val exportTask = activeMissingKanjiExport
        return MissingKanjiScreenModel(
            content = content,
            providerAvailability = availability,
            frequency = preferences.toFrequencyModel(),
            primaryAction = primaryAction,
            onHome = ::renderHome,
            onPrimaryAction = {
                performMissingKanjiPrimaryAction(primaryAction, content)
            },
            onCancelScan = ::cancelMissingKanjiScan,
            onRangeApplied = ::applyMissingKanjiRange,
            onRangePreview = ::previewMissingKanjiRange,
            onSearchQueryChanged = ::persistMissingKanjiSearch,
            destinations = MissingKanjiDestinationModel(
                addToKaniEnabled = hasReport,
                createAnkiDeckEnabled = hasReport &&
                    capability.canWriteCollection &&
                    capability.providerSpecVersion >= MINIMUM_DIRECT_WRITE_SPEC,
                csvExportEnabled = hasReport,
                defaultDeckName = MissingKanjiExportPlanner.DEFAULT_DECK_NAME,
                newPerDay = settings().newPerDay,
                operationInProgress = missingKanjiOperationInProgress.get(),
                exportProgress = exportTask?.uiProgress,
                onAddToKani = ::addSelectedMissingKanjiToKani,
                onCreateAnkiDeck = ::createSelectedMissingKanjiInAnki,
                onExportCsv = ::shareSelectedMissingKanjiCsv,
                onCancelExport = ::cancelMissingKanjiExport,
                onRemoveFromKani = ::removeMissingKanjiFromKani,
            ),
            operationResult = activeMissingKanjiOperationResult,
            onDismissOperationResult = ::dismissMissingKanjiOperationResult,
            onStudyNow = ::studyAdmittedMissingKanji,
            onExportCsvFallback = ::shareFailedMissingKanjiExportAsCsv,
        )
    }

    private fun addSelectedMissingKanjiToKani(literals: Set<String>) {
        if (literals.isEmpty() || !missingKanjiOperationInProgress.compareAndSet(false, true)) {
            return
        }
        val candidates = literals.mapNotNull(activeMissingKanjiCandidatesByLiteral::get)
        activeMissingKanjiOperationResult = null
        if (isMissingKanjiRouteVisible()) {
            renderMissingKanji()
        }
        io.execute {
            val result = runCatching {
                addMissingKanjiCandidatesToKani(
                    requestedCount = literals.size,
                    candidates = candidates,
                )
            }.getOrElse {
                MissingKanjiOperationResultModel.Failed
            }
            postToMainIfActive {
                missingKanjiOperationInProgress.set(false)
                activeMissingKanjiOperationResult = result
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun addMissingKanjiCandidatesToKani(
        requestedCount: Int,
        candidates: List<MissingKanjiCandidate>,
    ): MissingKanjiOperationResultModel.KaniAdmission {
        val repository = store.missingKanjiStore()
        val requestedLiterals = candidates.map(MissingKanjiCandidate::literal)
        val existingItems = store.studyItemsForKanji(requestedLiterals)
        val activeManualLiterals = repository.manualSources()
            .mapTo(HashSet()) { source -> source.candidate.literal }
        val addition = ManualKanjiAdmissionPolicy.planAddition(
            candidates = candidates,
            existingStudyLiterals = existingItems.mapTo(HashSet()) { item -> item.kanji },
            activeManualLiterals = activeManualLiterals,
        )
        val now = System.currentTimeMillis()
        val written = repository.addManualSources(addition.candidatesToAdd, now)
        val addedLiterals = LinkedHashSet<String>().apply {
            addAll(written.addedLiterals)
            addAll(written.reactivatedLiterals)
        }

        var admittedNowCount = 0
        if (addedLiterals.isNotEmpty()) {
            runCatching {
                val rows = store.activeStudyDashboardRows()
                val currentItems = store.studyItemsForKanji(rows.map { row -> row.kanji })
                val seeded = studyQueue(
                    rows = rows,
                    now = now,
                    persist = true,
                    plan = null,
                    currentItems = currentItems,
                )
                admittedNowCount = seeded.count { item ->
                    item.kanji in addedLiterals &&
                        item.state != StudyLadderRules.STATE_RETIRED
                }
            }
        }
        val already = LinkedHashSet<String>().apply {
            addAll(addition.alreadyInKaniLiterals)
            addAll(written.alreadyActiveLiterals)
        }
        val missingMeaning = LinkedHashSet<String>().apply {
            addAll(addition.missingMeaningLiterals)
            addAll(written.missingMeaningLiterals)
        }
        val missingReading = LinkedHashSet<String>().apply {
            addAll(addition.missingReadingLiterals)
            addAll(written.missingReadingLiterals)
        }
        return MissingKanjiOperationResultModel.KaniAdmission(
            requestedCount = requestedCount,
            addedCount = addedLiterals.size,
            alreadyInKaniCount = already.size,
            skippedMissingMeaningCount = missingMeaning.size,
            skippedMissingReadingCount = missingReading.size,
            invalidCount = addition.invalidLiterals.size +
                written.invalidCount +
                (requestedCount - candidates.size).coerceAtLeast(0),
            admittedNowCount = admittedNowCount,
            deferredCount = (addedLiterals.size - admittedNowCount).coerceAtLeast(0),
        )
    }

    private fun createSelectedMissingKanjiInAnki(
        literals: Set<String>,
        deckName: String,
    ) {
        if (
            literals.isEmpty() ||
            deckName.isBlank() ||
            !missingKanjiOperationInProgress.compareAndSet(false, true)
        ) {
            return
        }
        val candidates = literals.mapNotNull(activeMissingKanjiCandidatesByLiteral::get)
        val task = MissingKanjiExportTask(
            literals = literals.toSet(),
            deckName = deckName.trim(),
        )
        activeMissingKanjiExport = task
        activeMissingKanjiCsvFallbackLiterals = emptySet()
        activeMissingKanjiOperationResult = null
        if (isMissingKanjiRouteVisible()) {
            renderMissingKanji()
        }
        io.execute {
            val result = runCatching {
                runMissingKanjiAnkiExport(task, candidates)
            }.getOrElse {
                MissingKanjiOperationResultModel.Failed
            }
            postToMainIfActive {
                if (activeMissingKanjiExport !== task) {
                    return@postToMainIfActive
                }
                activeMissingKanjiExport = null
                missingKanjiOperationInProgress.set(false)
                activeMissingKanjiOperationResult = result
                activeMissingKanjiCsvFallbackLiterals =
                    if (
                        result is MissingKanjiOperationResultModel.AnkiExport &&
                        result.csvFallbackAvailable
                    ) {
                        task.csvFallbackLiterals.get()
                    } else {
                        emptySet()
                    }
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun runMissingKanjiAnkiExport(
        task: MissingKanjiExportTask,
        candidates: List<MissingKanjiCandidate>,
    ): MissingKanjiOperationResultModel.AnkiExport {
        val repository = store.missingKanjiStore()
        val writer = AnkiMissingKanjiWriter(
            context = this,
            cancellation = AnkiMissingKanjiWriter.Cancellation {
                task.cancellation.get() || Thread.currentThread().isInterrupted
            },
        )
        val export = writer.export(
            candidates = candidates,
            deckName = task.deckName,
            progress = AnkiMissingKanjiWriter.ProgressListener { progress ->
                postToMainIfActive {
                    if (activeMissingKanjiExport === task) {
                        task.uiProgress.update(
                            totalCount = progress.totalCount,
                            processedCount = progress.processedCount,
                            createdCount = progress.createdCount,
                            alreadyPresentCount = progress.alreadyPresentCount,
                        )
                    }
                }
            },
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { destinationKey, notes ->
                val exportedAt = System.currentTimeMillis()
                repository.recordExportReceipts(
                    notes.map { note ->
                        MissingKanjiExportReceipt(
                            literal = note.literal,
                            destinationKey = destinationKey,
                            exportedAt = exportedAt,
                            externalNoteId = note.noteId,
                        )
                    },
                ) == notes.size
            },
        )
        val missingCandidates = (task.literals.size - candidates.size).coerceAtLeast(0)
        val csvFallbackLiterals = LinkedHashSet(export.unfinishedLiterals)
        task.csvFallbackLiterals.set(csvFallbackLiterals)
        return MissingKanjiOperationResultModel.AnkiExport(
            deckName = task.deckName,
            createdCount = export.createdCount,
            alreadyPresentCount = export.alreadyPresentCount,
            skippedCount = export.skippedCount + missingCandidates,
            unfinishedCount = export.unfinishedLiterals.size,
            failureCode = export.failureKind?.name?.lowercase(Locale.ROOT),
            csvFallbackAvailable = csvFallbackLiterals.isNotEmpty(),
        )
    }

    private fun cancelMissingKanjiExport() {
        val task = activeMissingKanjiExport ?: return
        task.cancellation.set(true)
        task.uiProgress.markCancelling()
    }

    private fun shareSelectedMissingKanjiCsv(literals: Set<String>) {
        if (literals.isEmpty() || !missingKanjiOperationInProgress.compareAndSet(false, true)) {
            return
        }
        val candidates = literals.mapNotNull(activeMissingKanjiCandidatesByLiteral::get)
        val range = activeMissingKanjiPreferences.range
        activeMissingKanjiOperationResult = null
        activeMissingKanjiCsvFallbackLiterals = literals.toSet()
        if (isMissingKanjiRouteVisible()) {
            renderMissingKanji()
        }
        io.execute {
            val prepared = MissingKanjiExportShare.prepare(
                context = this,
                candidates = candidates,
                range = range,
            )
            postToMainIfActive {
                missingKanjiOperationInProgress.set(false)
                if (prepared == null) {
                    activeMissingKanjiOperationResult = MissingKanjiOperationResultModel.Failed
                } else {
                    val launched = runCatching {
                        startActivity(
                            Intent.createChooser(
                                prepared.intent,
                                MissingKanjiTextCopy.exportChooserTitle(),
                            ),
                        )
                    }.isSuccess
                    activeMissingKanjiOperationResult = if (launched) {
                        MissingKanjiOperationResultModel.CsvExport(
                            exportedCount = prepared.csvResult.exportedCount,
                            skippedCount = prepared.csvResult.skippedCount +
                                (literals.size - candidates.size).coerceAtLeast(0),
                            fileName = prepared.fileName,
                        )
                    } else {
                        MissingKanjiOperationResultModel.Failed
                    }
                }
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun shareFailedMissingKanjiExportAsCsv() {
        val literals = activeMissingKanjiCsvFallbackLiterals
        activeMissingKanjiOperationResult = null
        activeMissingKanjiCsvFallbackLiterals = emptySet()
        shareSelectedMissingKanjiCsv(literals)
    }

    private fun removeMissingKanjiFromKani(literal: String) {
        if (!missingKanjiOperationInProgress.compareAndSet(false, true)) {
            return
        }
        activeMissingKanjiOperationResult = null
        if (isMissingKanjiRouteVisible()) {
            renderMissingKanji()
        }
        io.execute {
            val result = runCatching {
                val removed = store.missingKanjiStore().removeUnreviewedManualSources(
                    literals = listOf(literal),
                    nowMillis = System.currentTimeMillis(),
                )
                MissingKanjiOperationResultModel.KaniRemoval(
                    literal = literal,
                    removed = literal in removed.removedLiterals,
                    reviewed = literal in removed.reviewedLiterals,
                )
            }.getOrElse {
                MissingKanjiOperationResultModel.Failed
            }
            postToMainIfActive {
                missingKanjiOperationInProgress.set(false)
                activeMissingKanjiOperationResult = result
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun dismissMissingKanjiOperationResult() {
        activeMissingKanjiOperationResult = null
        activeMissingKanjiCsvFallbackLiterals = emptySet()
        if (isMissingKanjiRouteVisible()) {
            renderMissingKanji()
        }
    }

    private fun studyAdmittedMissingKanji() {
        activeMissingKanjiOperationResult = null
        activeMissingKanjiCsvFallbackLiterals = emptySet()
        startFocusedStudy()
    }

    private fun primaryAction(
        content: MissingKanjiContentModel,
        availability: MissingKanjiProviderAvailability,
    ): MissingKanjiPrimaryAction {
        return when (availability) {
            MissingKanjiProviderAvailability.NOT_INSTALLED ->
                MissingKanjiPrimaryAction.INSTALL_ANKIDROID
            MissingKanjiProviderAvailability.PERMISSION_REQUIRED ->
                MissingKanjiPrimaryAction.GRANT_PERMISSION
            MissingKanjiProviderAvailability.UNAVAILABLE ->
                MissingKanjiPrimaryAction.RETRY
            MissingKanjiProviderAvailability.READY -> when (content) {
                MissingKanjiContentModel.FirstRun -> MissingKanjiPrimaryAction.SCAN
                is MissingKanjiContentModel.Error -> {
                    if (content.failureCode == FAILURE_DICTIONARY_UNAVAILABLE) {
                        MissingKanjiPrimaryAction.RETRY
                    } else {
                        MissingKanjiPrimaryAction.SCAN_AGAIN
                    }
                }
                else -> MissingKanjiPrimaryAction.SCAN_AGAIN
            }
        }
    }

    private fun performMissingKanjiPrimaryAction(
        action: MissingKanjiPrimaryAction,
        content: MissingKanjiContentModel,
    ) {
        when (action) {
            MissingKanjiPrimaryAction.INSTALL_ANKIDROID -> openAnkiDroidInstallPage()
            MissingKanjiPrimaryAction.GRANT_PERMISSION -> {
                val permission = activeInventoryPermission
                if (permission.isNullOrBlank()) {
                    renderMissingKanji()
                } else {
                    launchAnkiDatabasePermission(permission)
                }
            }
            MissingKanjiPrimaryAction.RETRY -> renderMissingKanji()
            MissingKanjiPrimaryAction.SCAN,
            MissingKanjiPrimaryAction.SCAN_AGAIN -> {
                if (
                    content is MissingKanjiContentModel.Error &&
                    content.failureCode == FAILURE_DICTIONARY_UNAVAILABLE
                ) {
                    renderMissingKanji()
                } else {
                    startMissingKanjiScan()
                }
            }
        }
    }

    private fun applyMissingKanjiRange(
        preset: MissingKanjiPreset,
        range: MissingKanjiFrequencyRange,
    ) {
        if (MissingKanjiAnalyzer.validateRange(range) != null) {
            return
        }
        val next = activeMissingKanjiPreferences.copy(
            preset = preset.storedValue,
            range = range,
        )
        activeMissingKanjiPreferences = next
        io.execute {
            runCatching {
                store.missingKanjiStore().savePreferences(next)
            }
            postToMainIfActive {
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun persistMissingKanjiSearch(query: String) {
        val next = activeMissingKanjiPreferences.copy(
            searchQuery = query.take(MAX_PERSISTED_SEARCH_CHARS),
        )
        activeMissingKanjiPreferences = next
        io.execute {
            runCatching {
                store.missingKanjiStore().savePreferences(next)
            }
        }
    }

    private fun previewMissingKanjiRange(
        range: MissingKanjiFrequencyRange,
        onResult: (Int) -> Unit,
    ) {
        if (MissingKanjiAnalyzer.validateRange(range) != null) {
            return
        }
        io.execute {
            val count = runCatching {
                warmDictionaryLookup().eligibleKanjiCount(
                    DictionaryLookup.JitenRankRange(
                        minimumRank = range.minimumRank,
                        maximumRank = range.maximumRank,
                        includeUnranked = range.includeUnranked,
                    ),
                )
            }.getOrDefault(0)
            postToMainIfActive {
                onResult(count.coerceAtLeast(0))
            }
        }
    }

    private fun startMissingKanjiScan() {
        if (activeMissingKanjiScan != null) {
            return
        }
        val task = MissingKanjiScanTask(
            startedAtMillis = System.currentTimeMillis(),
        )
        activeMissingKanjiScan = task
        currentHomeRouteRestoration = HomeRouteRestoration.missingKanji()
        renderMissingKanjiScanning(task)
        io.execute {
            runMissingKanjiScan(task)
        }
    }

    private fun renderMissingKanjiScanning(task: MissingKanjiScanTask) {
        val model = MissingKanjiScreenModel(
            content = MissingKanjiContentModel.Scanning(task.uiProgress),
            providerAvailability = MissingKanjiProviderAvailability.READY,
            frequency = activeMissingKanjiPreferences.toFrequencyModel(),
            primaryAction = MissingKanjiPrimaryAction.SCAN_AGAIN,
            onHome = ::renderHome,
            onPrimaryAction = {},
            onCancelScan = ::cancelMissingKanjiScan,
            onRangeApplied = { _, _ -> },
            onRangePreview = { _, _ -> },
            onSearchQueryChanged = {},
        )
        renderHomeRoute(
            backAction = Runnable { renderHome() },
            managedScroll = true,
        ) {
            MissingKanjiScreen(model)
        }
    }

    private fun cancelMissingKanjiScan() {
        val task = activeMissingKanjiScan ?: return
        task.cancellation.set(true)
        task.uiProgress.markCancelling()
    }

    private fun runMissingKanjiScan(task: MissingKanjiScanTask) {
        var capability: AnkiDroidCollectionInventoryGateway.CapabilityStatus? = null
        try {
            val gateway = AnkiDroidCollectionInventoryGateway(
                context = this,
                cancellation = AnkiDroidCollectionInventoryGateway.Cancellation {
                    task.cancellation.get() || Thread.currentThread().isInterrupted
                },
            )
            capability = gateway.status()
            when {
                !capability.installed -> throw AnkiDroidCollectionInventoryGateway.Failure(
                    AnkiDroidCollectionInventoryGateway.FailureKind.NOT_INSTALLED,
                    "AnkiDroid is not installed.",
                )
                !capability.permissionGranted -> throw AnkiDroidCollectionInventoryGateway.Failure(
                    AnkiDroidCollectionInventoryGateway.FailureKind.PERMISSION_MISSING,
                    "AnkiDroid collection permission is missing.",
                )
                !capability.canReadCollection -> throw AnkiDroidCollectionInventoryGateway.Failure(
                    AnkiDroidCollectionInventoryGateway.FailureKind.PROVIDER_UNAVAILABLE,
                    "AnkiDroid collection reading is unavailable.",
                )
            }
            val reader = AnkiKanjiInventoryReader(gateway)
            val inventory = reader.read { progress ->
                task.latestProgress.set(progress)
                publishMissingKanjiScanProgress(task, progress)
            }
            if (task.cancellation.get() || Thread.currentThread().isInterrupted) {
                throw AnkiDroidCollectionInventoryGateway.Failure(
                    AnkiDroidCollectionInventoryGateway.FailureKind.CANCELLED,
                    "AnkiDroid inventory scan was cancelled.",
                )
            }
            store.missingKanjiStore().publishInventory(
                inventory = inventory,
                startedAt = task.startedAtMillis,
                completedAt = System.currentTimeMillis(),
                providerFingerprint = providerFingerprint(capability),
            )
        } catch (failure: AnkiDroidCollectionInventoryGateway.Failure) {
            recordUnsuccessfulScan(
                task = task,
                capability = capability,
                kind = failure.kind,
            )
        } catch (_: Exception) {
            val kind = if (task.cancellation.get() || Thread.currentThread().isInterrupted) {
                AnkiDroidCollectionInventoryGateway.FailureKind.CANCELLED
            } else {
                AnkiDroidCollectionInventoryGateway.FailureKind.PROVIDER_UNAVAILABLE
            }
            recordUnsuccessfulScan(task, capability, kind)
        } finally {
            postToMainIfActive {
                if (activeMissingKanjiScan === task) {
                    activeMissingKanjiScan = null
                }
                if (isMissingKanjiRouteVisible()) {
                    renderMissingKanji()
                }
            }
        }
    }

    private fun publishMissingKanjiScanProgress(
        task: MissingKanjiScanTask,
        progress: AnkiKanjiInventoryProgress,
    ) {
        if (
            progress.notesScanned != 0 &&
            progress.notesScanned - task.lastPublishedNoteCount < PROGRESS_NOTE_INTERVAL
        ) {
            return
        }
        task.lastPublishedNoteCount = progress.notesScanned
        postToMainIfActive {
            if (activeMissingKanjiScan === task) {
                task.uiProgress.update(
                    notesScanned = progress.notesScanned,
                    uniqueKanjiCount = progress.uniqueKanjiCount,
                    skippedNotes = progress.skippedNotes,
                )
            }
        }
    }

    private fun recordUnsuccessfulScan(
        task: MissingKanjiScanTask,
        capability: AnkiDroidCollectionInventoryGateway.CapabilityStatus?,
        kind: AnkiDroidCollectionInventoryGateway.FailureKind,
    ) {
        val progress = task.latestProgress.get()
        runCatching {
            store.missingKanjiStore().recordUnsuccessfulScan(
                status = if (kind == AnkiDroidCollectionInventoryGateway.FailureKind.CANCELLED) {
                    MissingKanjiScanStatus.CANCELLED
                } else {
                    MissingKanjiScanStatus.FAILED
                },
                startedAt = task.startedAtMillis,
                completedAt = System.currentTimeMillis(),
                notesScanned = progress.notesScanned,
                fieldsScanned = 0,
                uniqueKanjiCount = progress.uniqueKanjiCount,
                skippedNotes = progress.skippedNotes,
                modelCount = 0,
                providerFingerprint = providerFingerprint(capability),
                failureCode = kind.failureCode(),
            )
        }
    }

    private fun renderScreenshotMissingKanji() {
        val model = screenshotMissingKanjiScreenModel()
        renderHomeRoute(
            backAction = Runnable { renderHome() },
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
            managedScroll = true,
        ) {
            MissingKanjiScreen(model)
        }
    }

    private fun isMissingKanjiRouteVisible(): Boolean {
        return currentRoute == MainActivityBase.NAV_HOME_ROUTE &&
            currentHomeRouteRestoration?.destination ==
            HomeRouteRestoration.Destination.MISSING_KANJI
    }

    private data class MissingKanjiScanTask(
        val startedAtMillis: Long,
        val cancellation: AtomicBoolean = AtomicBoolean(false),
        val latestProgress: AtomicReference<AnkiKanjiInventoryProgress> = AtomicReference(
            AnkiKanjiInventoryProgress(
                notesScanned = 0,
                uniqueKanjiCount = 0,
                skippedNotes = 0,
            ),
        ),
        val uiProgress: MissingKanjiScanProgressState = MissingKanjiScanProgressState(),
        var lastPublishedNoteCount: Int = 0,
    )

    private data class MissingKanjiExportTask(
        val literals: Set<String>,
        val deckName: String,
        val cancellation: AtomicBoolean = AtomicBoolean(false),
        val uiProgress: MissingKanjiExportProgressState = MissingKanjiExportProgressState(),
        val csvFallbackLiterals: AtomicReference<Set<String>> = AtomicReference(emptySet()),
    )

    private companion object {
        const val FAILURE_CANCELLED = "cancelled"
        const val FAILURE_PROVIDER_UNAVAILABLE = "provider_unavailable"
        const val FAILURE_DICTIONARY_UNAVAILABLE = "dictionary_unavailable"
        const val MAX_PERSISTED_SEARCH_CHARS = 128
        const val PROGRESS_NOTE_INTERVAL = 25
        const val MINIMUM_DIRECT_WRITE_SPEC = 2
        const val STALE_AFTER_MILLIS = 7L * 24L * 60L * 60L * 1000L

        fun MissingKanjiPreferences.toFrequencyModel(): MissingKanjiFrequencyModel =
            MissingKanjiFrequencyModel(
                preset = MissingKanjiPreset.fromStored(preset),
                range = range,
                searchQuery = searchQuery,
            )

        fun AnkiDroidCollectionInventoryGateway.CapabilityStatus.toAvailability():
            MissingKanjiProviderAvailability = when {
                !installed -> MissingKanjiProviderAvailability.NOT_INSTALLED
                !permissionGranted -> MissingKanjiProviderAvailability.PERMISSION_REQUIRED
                canReadCollection -> MissingKanjiProviderAvailability.READY
                else -> MissingKanjiProviderAvailability.UNAVAILABLE
            }

        fun staleReason(
            inventoryState: MissingKanjiInventoryState,
            nowMillis: Long,
        ): MissingKanjiStaleReason? {
            val published = inventoryState.published ?: return null
            val latest = inventoryState.latestAttempt
            if (latest != null && latest.id != published.scan.id) {
                return if (latest.status == MissingKanjiScanStatus.CANCELLED) {
                    MissingKanjiStaleReason.CANCELLED
                } else {
                    MissingKanjiStaleReason.FAILED
                }
            }
            return if (
                nowMillis.coerceAtLeast(published.scan.completedAt) -
                published.scan.completedAt >= STALE_AFTER_MILLIS
            ) {
                MissingKanjiStaleReason.AGE
            } else {
                null
            }
        }

        fun reportKey(
            scanId: Long,
            range: MissingKanjiFrequencyRange,
        ): String = buildString {
            append(scanId)
            append(':')
            append(range.minimumRank)
            append(':')
            append(range.maximumRank)
            append(':')
            append(range.includeUnranked)
        }

        fun providerFingerprint(
            capability: AnkiDroidCollectionInventoryGateway.CapabilityStatus?,
        ): String {
            val authority = capability?.authority ?: "unknown"
            val spec = capability?.providerSpecVersion ?: -1
            return "authority=$authority;spec=$spec"
        }

        fun AnkiDroidCollectionInventoryGateway.FailureKind.failureCode(): String = when (this) {
            AnkiDroidCollectionInventoryGateway.FailureKind.NOT_INSTALLED -> "not_installed"
            AnkiDroidCollectionInventoryGateway.FailureKind.PERMISSION_MISSING -> "permission_missing"
            AnkiDroidCollectionInventoryGateway.FailureKind.PROVIDER_UNAVAILABLE -> "provider_unavailable"
            AnkiDroidCollectionInventoryGateway.FailureKind.CANCELLED -> "cancelled"
        }
    }
}
