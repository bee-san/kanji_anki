const els = {
  navDashboard: document.getElementById("nav-dashboard"),
  navStudy: document.getElementById("nav-study"),
  healthPill: document.getElementById("health-pill"),
  statusBanner: document.getElementById("status-banner"),
  viewDashboard: document.getElementById("view-dashboard"),
  viewDetail: document.getElementById("view-detail"),
  viewStudy: document.getElementById("view-study"),
  dashboardCopy: document.getElementById("dashboard-copy"),
  refreshDashboard: document.getElementById("refresh-dashboard"),
  syncCollection: document.getElementById("sync-collection"),
  refreshSeeds: document.getElementById("refresh-seeds"),
  healthDetail: document.getElementById("health-detail"),
  statusSyncChip: document.getElementById("status-sync-chip"),
  statusSeedsChip: document.getElementById("status-seeds-chip"),
  statusSourceChip: document.getElementById("status-source-chip"),
  settingsForm: document.getElementById("settings-form"),
  settingsEditor: document.getElementById("settings-editor"),
  settingAnkiConnectUrl: document.getElementById("setting-ankiconnect-url"),
  settingNoteModels: document.getElementById("setting-note-models"),
  settingExpressionField: document.getElementById("setting-expression-field"),
  settingReadingField: document.getElementById("setting-reading-field"),
  settingMeaningField: document.getElementById("setting-meaning-field"),
  settingMatureDays: document.getElementById("setting-mature-days"),
  settingSupportThreshold: document.getElementById("setting-support-threshold"),
  settingPollingEnabled: document.getElementById("setting-polling-enabled"),
  settingPollingIntervalSeconds: document.getElementById("setting-polling-interval-seconds"),
  settingJitenCacheTtlHours: document.getElementById("setting-jiten-cache-ttl-hours"),
  settingJitenTimeoutSeconds: document.getElementById("setting-jiten-timeout-seconds"),
  reloadSettings: document.getElementById("reload-settings"),
  saveSettings: document.getElementById("save-settings"),
  settingsState: document.getElementById("settings-state"),
  metricSuspended: document.getElementById("metric-suspended"),
  metricAverageRank: document.getElementById("metric-average-rank"),
  metricTotalKanji: document.getElementById("metric-total-kanji"),
  metricUnknown: document.getElementById("metric-unknown"),
  dashboardAverageRankNote: document.getElementById("dashboard-average-rank-note"),
  dashboardSupportThreshold: document.getElementById("dashboard-support-threshold"),
  dashboardWarnings: document.getElementById("dashboard-warnings"),
  dashboardTbody: document.getElementById("dashboard-tbody"),
  dashboardEmpty: document.getElementById("dashboard-empty"),
  dashboardStartNew: document.getElementById("dashboard-start-new"),
  dashboardStartReview: document.getElementById("dashboard-start-review"),
  dashboardStartMixed: document.getElementById("dashboard-start-mixed"),
  dashboardOpenStudy: document.getElementById("dashboard-open-study"),
  dashboardStudyDue: document.getElementById("dashboard-study-due"),
  dashboardStudyNew: document.getElementById("dashboard-study-new"),
  dashboardStudyActive: document.getElementById("dashboard-study-active"),
  dashboardStudyTarget: document.getElementById("dashboard-study-target"),
  dashboardStudyNext: document.getElementById("dashboard-study-next"),
  dashboardStudyPreview: document.getElementById("dashboard-study-preview"),
  dashboardStudyEmpty: document.getElementById("dashboard-study-empty"),
  dashboardSnapshotSeeds: document.getElementById("dashboard-snapshot-seeds"),
  dashboardSnapshotTarget: document.getElementById("dashboard-snapshot-target"),
  dashboardSnapshotActive: document.getElementById("dashboard-snapshot-active"),
  dashboardSnapshotDue: document.getElementById("dashboard-snapshot-due"),
  dashboardSnapshotNext: document.getElementById("dashboard-snapshot-next"),
  detailBack: document.getElementById("detail-back"),
  detailKanji: document.getElementById("detail-kanji"),
  detailHeadline: document.getElementById("detail-headline"),
  detailSubtitle: document.getElementById("detail-subtitle"),
  detailRank: document.getElementById("detail-rank"),
  detailDeficit: document.getElementById("detail-deficit"),
  detailSuspended: document.getElementById("detail-suspended"),
  detailMature: document.getElementById("detail-mature"),
  detailWritingMeta: document.getElementById("detail-writing-meta"),
  detailStrokeImage: document.getElementById("detail-stroke-image"),
  detailStrokeNote: document.getElementById("detail-stroke-note"),
  detailMeaningList: document.getElementById("detail-meaning-list"),
  detailReadingList: document.getElementById("detail-reading-list"),
  detailStructureList: document.getElementById("detail-structure-list"),
  detailBrowserSearch: document.getElementById("detail-browser-search"),
  detailCollectionList: document.getElementById("detail-collection-list"),
  detailSupportList: document.getElementById("detail-support-list"),
  detailNoteList: document.getElementById("detail-note-list"),
  detailSourceList: document.getElementById("detail-source-list"),
  studyRefresh: document.getElementById("study-refresh"),
  studyRefreshSeeds: document.getElementById("study-refresh-seeds"),
  studyDue: document.getElementById("study-due"),
  studyNew: document.getElementById("study-new"),
  studyActive: document.getElementById("study-active"),
  studyTarget: document.getElementById("study-target"),
  studyNext: document.getElementById("study-next"),
  studyPreview: document.getElementById("study-preview"),
  studyEmpty: document.getElementById("study-empty"),
  studyAttribution: document.getElementById("study-attribution"),
  startMixed: document.getElementById("start-mixed"),
  startReview: document.getElementById("start-review"),
  startNew: document.getElementById("start-new"),
  studySessionEmpty: document.getElementById("study-session-empty"),
  studySessionContent: document.getElementById("study-session-content"),
  sessionLabel: document.getElementById("session-label"),
  sessionTitle: document.getElementById("session-title"),
  sessionCopy: document.getElementById("session-copy"),
  sessionBadges: document.getElementById("session-badges"),
  sessionPromptKanji: document.getElementById("session-prompt-kanji"),
  sessionPromptKeyword: document.getElementById("session-prompt-keyword"),
  sessionContext: document.getElementById("session-context"),
  sessionInstruction: document.getElementById("session-instruction"),
  sessionReveal: document.getElementById("session-reveal"),
  sessionAnswerPanel: document.getElementById("session-answer-panel"),
  sessionAnswerKanji: document.getElementById("session-answer-kanji"),
  sessionAnswerSummary: document.getElementById("session-answer-summary"),
  sessionAnswerFacts: document.getElementById("session-answer-facts"),
  sessionStrokeImage: document.getElementById("session-stroke-image"),
  sessionStrokeNote: document.getElementById("session-stroke-note"),
  sessionEnd: document.getElementById("session-end"),
  handwritingPanel: document.getElementById("handwriting-panel"),
  handwritingCopy: document.getElementById("handwriting-copy"),
  handwritingGuide: document.getElementById("handwriting-guide"),
  handwritingCanvas: document.getElementById("handwriting-canvas"),
  canvasUndo: document.getElementById("canvas-undo"),
  canvasClear: document.getElementById("canvas-clear"),
  sessionHint: document.getElementById("session-hint"),
  handwritingEvaluate: document.getElementById("handwriting-evaluate"),
  handwritingPass: document.getElementById("handwriting-pass"),
  handwritingFail: document.getElementById("handwriting-fail"),
  handwritingStatus: document.getElementById("handwriting-status"),
  handwritingMetrics: document.getElementById("handwriting-metrics"),
  handwritingHintWrap: document.getElementById("handwriting-hint-wrap"),
  handwritingHintImage: document.getElementById("handwriting-hint-image"),
  handwritingHintNote: document.getElementById("handwriting-hint-note"),
  ratingHelp: document.getElementById("rating-help"),
  ratingButtons: Array.from(document.querySelectorAll(".rating-button[data-rating]"))
};

const state = {
  route: { name: "dashboard", key: "dashboard" },
  health: null,
  healthError: "",
  settings: {},
  settingsText: "{}",
  settingsLoaded: false,
  settingsDirty: false,
  settingsError: "",
  dashboard: null,
  dashboardError: "",
  kanjiCache: new Map(),
  studyOverview: null,
  studyOverviewError: "",
  currentSession: null,
  currentSessionMode: "mixed",
  sessionUi: null,
  pendingRouteKey: ""
};

const GUIDE_MODE_BY_LEVEL = {
  0: "trace",
  1: "outline",
  2: "minimal-hints",
  3: "blind-recall"
};
const RATING_ORDER = ["again", "hard", "good", "easy"];
const BINARY_RATING_ORDER = ["again", "good"];

function parseRoute() {
  const rawHash = String(window.location.hash || "#/").replace(/^#/, "") || "/";
  if (rawHash === "/" || rawHash === "") {
    return { name: "dashboard", key: "dashboard" };
  }
  if (rawHash === "/study") {
    return { name: "study", key: "study" };
  }
  if (rawHash.startsWith("/kanji/")) {
    const kanji = safeDecode(rawHash.slice("/kanji/".length));
    return { name: "detail", kanji, key: `detail:${kanji}` };
  }
  return { name: "dashboard", key: "dashboard" };
}

function safeDecode(value) {
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
}

function setActiveNav() {
  const onStudy = state.route.name === "study";
  els.navDashboard.classList.toggle("is-active", !onStudy);
  els.navStudy.classList.toggle("is-active", onStudy);
}

function showStatus(message, tone = "info") {
  if (!message) {
    clearStatus();
    return;
  }
  els.statusBanner.textContent = message;
  els.statusBanner.dataset.tone = tone;
  els.statusBanner.classList.remove("is-hidden");
}

function clearStatus() {
  els.statusBanner.textContent = "";
  els.statusBanner.dataset.tone = "";
  els.statusBanner.classList.add("is-hidden");
}

function setView(name) {
  els.viewDashboard.classList.toggle("is-hidden", name !== "dashboard");
  els.viewDetail.classList.toggle("is-hidden", name !== "detail");
  els.viewStudy.classList.toggle("is-hidden", name !== "study");
}

function formatCount(value) {
  return Number(value || 0).toLocaleString();
}

function formatRank(value) {
  if (value == null || value === "") {
    return "Unranked";
  }
  const number = Number(value);
  if (Number.isFinite(number)) {
    return Number.isInteger(number)
      ? number.toLocaleString()
      : number.toLocaleString(undefined, { maximumFractionDigits: 1 });
  }
  return String(value);
}

function formatPercent(value) {
  if (value == null || value === "") {
    return "-";
  }
  return `${Math.round(Number(value) * 100)}%`;
}

function formatRatingLabel(value) {
  const rating = safeText(value).toLowerCase();
  if (!rating) {
    return "";
  }
  if (rating === "again") {
    return "Fail";
  }
  if (rating === "good") {
    return "Pass";
  }
  return rating.charAt(0).toUpperCase() + rating.slice(1);
}

function formatDateTime(value) {
  if (!value) {
    return "Not scheduled";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return String(value);
  }
  return parsed.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit"
  });
}

function pluralize(count, singular, plural = `${singular}s`) {
  return `${formatCount(count)} ${count === 1 ? singular : plural}`;
}

function formatPollingInterval(seconds) {
  const normalized = asPositiveInt(seconds, 0);
  if (!normalized) {
    return "";
  }
  if (normalized % 3600 === 0) {
    return pluralize(normalized / 3600, "hour");
  }
  if (normalized % 60 === 0) {
    return pluralize(normalized / 60, "minute");
  }
  return pluralize(normalized, "second");
}

function formatSourceKind(value) {
  const sourceKind = safeText(value, "none").toLowerCase();
  if (sourceKind === "none") {
    return "No rank source";
  }
  if (sourceKind === "fixture") {
    return "Fixture ranks";
  }
  return sourceKind
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function safeText(value, fallback = "") {
  if (value == null) {
    return fallback;
  }
  const text = String(value).trim();
  return text || fallback;
}

function asObject(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function asNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function asBoolean(value) {
  return Boolean(value);
}

function clamp(value, minimum = 0, maximum = 1) {
  return Math.min(maximum, Math.max(minimum, value));
}

function asPositiveInt(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.round(number) : fallback;
}

function stringList(value) {
  return asArray(value)
    .map((entry) => safeText(entry))
    .filter(Boolean);
}

function splitListInput(value) {
  return String(value || "")
    .split(/[\n,]+/)
    .map((entry) => safeText(entry))
    .filter(Boolean);
}

function uniqueList(items) {
  return Array.from(new Set(items.filter(Boolean)));
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function strokeAssetUrl(kanji) {
  return `/api/assets/stroke-order/${encodeURIComponent(kanji)}.svg`;
}

async function requestJson(path, options = {}) {
  const method = options.method || "GET";
  const requestOptions = {
    method,
    cache: "no-store",
    headers: options.body === undefined ? undefined : { "Content-Type": "application/json" },
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  };
  let response;
  try {
    response = await fetch(path, requestOptions);
  } catch (error) {
    throw new Error(`Network error while requesting ${path}.`);
  }
  const text = await response.text();
  let payload = {};
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch (error) {
      payload = {};
    }
  }
  if (!response.ok) {
    throw new Error(extractMessage(payload, `Request failed with status ${response.status}.`));
  }
  return asObject(payload);
}

function extractMessage(payload, fallback) {
  const object = asObject(payload);
  return safeText(object.error || object.message || object.detail, fallback);
}

function normalizeSourceEntries(value) {
  if (Array.isArray(value)) {
    return value.map((entry) => {
      const item = asObject(entry);
      return {
        name: safeText(item.name || item.label || item.id, "Source"),
        role: safeText(item.role),
        description: safeText(item.description || item.value)
      };
    });
  }

  const payload = asObject(value);
  const normalized = [];
  if (safeText(payload.dictionary)) {
    normalized.push({
      name: "KANJIDIC2",
      role: "Dictionary cache",
      description: safeText(payload.dictionary)
    });
  }
  if (safeText(payload.strokeOrder)) {
    normalized.push({
      name: "KanjiVG",
      role: "Stroke order cache",
      description: safeText(payload.strokeOrder)
    });
  }
  stringList(payload.warnings).forEach((warning) => {
    normalized.push({
      name: "Data warning",
      role: "Fallback",
      description: warning
    });
  });
  return normalized;
}

function normalizeDashboard(payload) {
  const summary = asObject(payload.summary);
  const rows = asArray(payload.rows).map((row) => {
    const item = asObject(row);
    return {
      kanji: safeText(item.kanji, "?"),
      jitenRank: item.jitenRank ?? item.rank ?? null,
      collectionExpressionCount: asNumber(item.collectionExpressionCount),
      suspendedExpressionCount: asNumber(item.suspendedExpressionCount),
      activeRecurringExpressionCount: asNumber(item.activeRecurringExpressionCount),
      matureSupportCount: asNumber(item.matureSupportCount),
      supportDeficit: asNumber(item.supportDeficit),
      isUnknown: asBoolean(item.isUnknown),
      browserSearch: safeText(item.browserSearch)
    };
  });
  return {
    summary: {
      analyzedSuspendedCardCount: asNumber(summary.analyzedSuspendedCardCount ?? summary.suspendedCardCount),
      analyzedSuspendedExpressionCount: asNumber(
        summary.analyzedSuspendedExpressionCount
      ),
      averageKanjiRank: summary.averageKanjiRank ?? null,
      rankedKanjiCount: asNumber(summary.rankedKanjiCount),
      totalKanjiCount: asNumber(summary.totalKanjiCount, rows.length),
      unknownKanjiCount: asNumber(summary.unknownKanjiCount, rows.filter((row) => row.isUnknown).length),
      matureSupportThreshold: asNumber(summary.matureSupportThreshold),
      problemSeedCount: asNumber(payload.problemSeedCount)
    },
    rows,
    warnings: stringList(payload.warnings),
    jitenSourceKind: safeText(payload.jitenSourceKind, "none")
  };
}

function normalizeDetail(payload, requestedKanji) {
  const classification = asObject(payload.classification);
  const readings = asObject(payload.readings);
  const meanings = asObject(payload.meanings);
  const structure = asObject(payload.structure);
  const writing = asObject(payload.writing);
  const collection = asObject(payload.collection);
  const counts = asObject(collection.counts);
  const relatedVocabulary = asObject(payload.relatedVocabulary);
  const collectionExpressions = stringList(
    collection.collectionExpressions || payload.collectionExpressions
  );
  return {
    kanji: safeText(payload.kanji, requestedKanji),
    classification: {
      grade: safeText(classification.gradedJoyo || classification.grade),
      joyo: safeText(classification.joyoOrJinmeiyoStatus),
      legacyJlpt: safeText(classification.legacyJlpt),
      kanjidicFreq: safeText(classification.kanjidicFreq),
      jitenRank: payload.jitenRank ?? classification.jitenRank ?? null
    },
    readings: {
      on: stringList(readings.on),
      kun: stringList(readings.kun),
      nanori: stringList(readings.nanori)
    },
    meanings: {
      en: stringList(meanings.en || payload.meanings)
    },
    structure: {
      classicalRadical: safeText(structure.classicalRadical),
      nelsonRadical: safeText(structure.nelsonRadical),
      radicalNames: stringList(structure.radicalNames),
      kradComponents: stringList(structure.kradComponents),
      kanjiVgElements: stringList(structure.kanjiVgElements),
      componentHint: safeText(structure.componentHint),
      variants: stringList(structure.variants),
      skip: safeText(asObject(structure.queryCodes).skip),
      fourCorner: safeText(asObject(structure.queryCodes).fourCorner),
      shDesc: safeText(asObject(structure.queryCodes).shDesc)
    },
    writing: {
      strokeCount: safeText(writing.strokeCount),
      altStrokeCounts: stringList(writing.altStrokeCounts)
    },
    collection: {
      browserSearch: safeText(collection.browserSearch || payload.browserSearch),
      counts: {
        collectionExpressionCount: asNumber(counts.collectionExpressionCount ?? payload.collectionExpressions),
        suspendedExpressionCount: asNumber(counts.suspendedExpressionCount, asArray(payload.affectedSuspendedExpressions).length),
        activeRecurringExpressionCount: asNumber(counts.activeRecurringExpressionCount, asArray(payload.activeRecurringExpressions).length),
        matureSupportCount: asNumber(counts.matureSupportCount, asArray(payload.matureSupportingExpressions).length),
        supportDeficit: asNumber(counts.supportDeficit ?? payload.supportDeficit),
        isUnknown: asBoolean(counts.isUnknown ?? payload.isUnknown)
      },
      collectionExpressions,
      suspendedExpressions: stringList(collection.suspendedExpressions || payload.affectedSuspendedExpressions),
      activeRecurringExpressions: stringList(collection.activeRecurringExpressions || payload.activeRecurringExpressions),
      matureSupportingExpressions: stringList(collection.matureSupportingExpressions || payload.matureSupportingExpressions),
      matchingNotes: asArray(collection.matchingNotes || payload.matchingNotes)
    },
    relatedVocabulary: {
      collectionExamples: stringList(
        relatedVocabulary.collectionExamples
        || relatedVocabulary.examples
        || collectionExpressions
      ),
      painExamples: stringList(relatedVocabulary.painExamples || payload.affectedSuspendedExpressions),
      bridgeExamples: stringList(relatedVocabulary.bridgeExamples || payload.activeRecurringExpressions),
      matureExamples: stringList(relatedVocabulary.matureExamples || payload.matureSupportingExpressions),
      jmdictExamples: asArray(relatedVocabulary.jmdictExamples)
    },
    sources: normalizeSourceEntries(payload.sources)
  };
}

function normalizeStudyOverview(payload) {
  return {
    dueCount: asNumber(payload.dueCount),
    newCount: asNumber(payload.newCount),
    activeQueueCount: asNumber(payload.activeQueueCount),
    inactiveCount: asNumber(payload.inactiveCount),
    currentProblemSeedCount: asNumber(payload.currentProblemSeedCount),
    retentionTarget: payload.retentionTarget ?? null,
    nextDueAt: safeText(payload.nextDueAt),
    queuePreview: asArray(payload.queuePreview).map((row) => {
      const item = asObject(row);
      return {
        kanji: safeText(item.kanji, "?"),
        itemStatus: safeText(item.itemStatus, "pending"),
        dueAt: safeText(item.dueAt),
        dueNow: asBoolean(item.dueNow),
        guideLevel: asNumber(item.guideLevel),
        guideLevelLabel: safeText(item.guideLevelLabel, "Guide"),
        supportDeficit: asNumber(item.supportDeficit),
        suspendedExpressionCount: asNumber(item.suspendedExpressionCount),
        activeRecurringExpressionCount: asNumber(item.activeRecurringExpressionCount),
        isProblemSeed: asBoolean(item.isProblemSeed)
      };
    }),
    attribution: asObject(payload.attribution)
  };
}

function normalizeSessionEnvelope(payload) {
  const candidate = asObject(payload.session);
  const session = Object.keys(candidate).length ? candidate : asObject(payload);
  const available = payload.available !== undefined
    ? Boolean(payload.available)
    : Boolean(session.reviewToken || session.kanji);
  return {
    available,
    message: safeText(payload.message),
    session: available ? normalizeSession(session) : null
  };
}

function normalizeSession(payload) {
  const prompts = asObject(payload.prompts);
  const recognition = asObject(prompts.recognition);
  const production = asObject(prompts.production);
  const answer = asObject(payload.answer);
  const content = asObject(payload.content);
  const strokeOrder = asObject(content.strokeOrder);
  const support = asObject(payload.support);
  const handwritingPolicy = asObject(payload.handwritingPolicy);
  const promptType = safeText(payload.promptType, "recognition").toLowerCase() === "production"
    ? "production"
    : "recognition";
  const guideLevel = asNumber(payload.guideLevel);
  const defaultGuideMode = GUIDE_MODE_BY_LEVEL[guideLevel] || GUIDE_MODE_BY_LEVEL[3];
  return {
    kanji: safeText(payload.kanji, "?"),
    itemStatus: safeText(payload.itemStatus, "new"),
    guideLevel,
    dueAt: safeText(payload.dueAt),
    dueNow: asBoolean(payload.dueNow),
    guideLevelLabel: safeText(payload.guideLevelLabel, "Guide"),
    sessionKind: safeText(payload.sessionKind, "learn"),
    outcomeMode: safeText(payload.outcomeMode, "legacy"),
    allowedOutcomes: stringList(payload.allowedOutcomes),
    reviewToken: safeText(payload.reviewToken),
    promptType,
    promptLabel: safeText(payload.promptLabel, promptType === "production" ? "Production" : "Recognition"),
    requiresWriting: asBoolean(payload.requiresWriting),
    prompts: {
      recognition: {
        kanji: safeText(recognition.kanji, safeText(payload.kanji, "?")),
        context: stringList(recognition.context),
        instruction: safeText(recognition.instruction, "Recall the meaning and reading before revealing the answer.")
      },
      production: {
        keyword: safeText(production.keyword, safeText(content.keyword, safeText(answer.keyword))),
        context: stringList(production.context),
        instruction: safeText(production.instruction, "Recall the kanji before revealing the answer.")
      }
    },
    answer: {
      kanji: safeText(answer.kanji, safeText(payload.kanji, "?")),
      keyword: safeText(answer.keyword, safeText(content.keyword)),
      meanings: stringList(answer.meanings),
      primaryReadings: stringList(answer.primaryReadings),
      readings: stringList(answer.readings),
      painExample: safeText(answer.painExample),
      bridgeExample: safeText(answer.bridgeExample),
      matureExample: safeText(answer.matureExample),
      components: stringList(answer.components),
      componentHint: safeText(answer.componentHint)
    },
    content: {
      keyword: safeText(content.keyword),
      fontVariant: safeText(content.fontVariant, "canonical"),
      fontVariantLabel: safeText(content.fontVariantLabel, "Canonical print"),
      fontFamily: safeText(content.fontFamily),
      strokeOrder: {
        available: asBoolean(strokeOrder.available),
        strokeCount: asNumber(strokeOrder.strokeCount),
        paths: stringList(strokeOrder.paths),
        guideLevel: asNumber(strokeOrder.guideLevel, asNumber(payload.guideLevel)),
        guideLevelLabel: safeText(
          strokeOrder.guideLevelLabel,
          safeText(payload.guideLevelLabel, "Guide")
        )
      }
    },
    handwritingPolicy: {
      guideMode: safeText(handwritingPolicy.guideMode, defaultGuideMode),
      guideLevel,
      guideLevelLabel: safeText(
        handwritingPolicy.guideLevelLabel,
        safeText(payload.guideLevelLabel, "Guide")
      ),
      required: asBoolean(handwritingPolicy.required ?? payload.requiresWriting),
      allowManualOverride: handwritingPolicy.allowManualOverride !== false,
      guidedEvaluationAvailable: handwritingPolicy.guidedEvaluationAvailable !== false,
      manualOnlyWithoutGeometry: asBoolean(handwritingPolicy.manualOnlyWithoutGeometry),
      allowedRatingsOnFailure: stringList(handwritingPolicy.allowedRatingsOnFailure).length
        ? stringList(handwritingPolicy.allowedRatingsOnFailure)
        : ["again", "hard"],
      blockedRatingsOnFailure: stringList(handwritingPolicy.blockedRatingsOnFailure)
    },
    support: {
      whyInQueue: safeText(support.whyInQueue),
      supportDeficit: asNumber(support.supportDeficit),
      suspendedExpressionCount: asNumber(support.suspendedExpressionCount),
      activeRecurringExpressionCount: asNumber(support.activeRecurringExpressionCount),
      matureSupportCount: asNumber(support.matureSupportCount),
      browserSearch: safeText(support.browserSearch),
      collectionExamples: stringList(support.collectionExamples),
      painExamples: stringList(support.painExamples),
      bridgeExamples: stringList(support.bridgeExamples),
      matureExamples: stringList(support.matureExamples)
    }
  };
}

function prepareReferenceGuideData(session) {
  const strokeOrder = asObject(asObject(session).content).strokeOrder;
  const referencePaths = stringList(asObject(strokeOrder).paths);
  if (!referencePaths.length) {
    return null;
  }
  const rawStrokes = referencePaths
    .map((path) => sampleReferencePath(path))
    .filter((stroke) => stroke.length);
  if (!rawStrokes.length) {
    return null;
  }
  return {
    rawStrokes,
    guideStrokes: normalizeStrokeSets(rawStrokes),
    shapeStrokes: rawStrokes.map((stroke) => normalizeSingleStroke(stroke))
  };
}

function createSessionUi(session = state.currentSession) {
  return {
    revealed: false,
    hintsUsed: 0,
    hintVisible: false,
    handwriting: {
      strokes: [],
      currentStroke: [],
      pointerId: null,
      result: null,
      phase: "prewrite",
      referenceGuideData: prepareReferenceGuideData(session)
    }
  };
}

function populateSettingsForm(settings) {
  const payload = asObject(settings);
  els.settingAnkiConnectUrl.value = safeText(payload.ankiConnectUrl, "http://127.0.0.1:8765");
  els.settingNoteModels.value = stringList(payload.noteModels).join("\n") || "Kiku";
  els.settingExpressionField.value = safeText(payload.expressionField, "Expression");
  els.settingReadingField.value = safeText(payload.readingField, "Reading");
  els.settingMeaningField.value = safeText(payload.meaningField, "Meaning");
  els.settingMatureDays.value = String(asPositiveInt(payload.matureDays, 21));
  els.settingSupportThreshold.value = String(asPositiveInt(payload.kanjiSupportThreshold, 3));
  els.settingPollingEnabled.value = String(Boolean(payload.pollingEnabled));
  els.settingPollingIntervalSeconds.value = String(asPositiveInt(payload.pollingIntervalSeconds, 900));
  els.settingJitenCacheTtlHours.value = String(asPositiveInt(payload.jitenCacheTtlHours, 24));
  els.settingJitenTimeoutSeconds.value = String(asPositiveInt(payload.jitenRequestTimeoutSeconds, 10));
  state.settingsText = JSON.stringify(buildSettingsPayload(), null, 2) || "{}";
  els.settingsEditor.value = state.settingsText;
}

function buildSettingsPayload() {
  return {
    ankiConnectUrl: safeText(els.settingAnkiConnectUrl.value, "http://127.0.0.1:8765"),
    noteModels: splitListInput(els.settingNoteModels.value),
    expressionField: safeText(els.settingExpressionField.value, "Expression"),
    readingField: safeText(els.settingReadingField.value, "Reading"),
    meaningField: safeText(els.settingMeaningField.value, "Meaning"),
    matureDays: asPositiveInt(els.settingMatureDays.value, 21),
    kanjiSupportThreshold: asPositiveInt(els.settingSupportThreshold.value, 3),
    pollingEnabled: els.settingPollingEnabled.value === "true",
    pollingIntervalSeconds: asPositiveInt(els.settingPollingIntervalSeconds.value, 900),
    jitenCacheTtlHours: asPositiveInt(els.settingJitenCacheTtlHours.value, 24),
    jitenRequestTimeoutSeconds: asPositiveInt(els.settingJitenTimeoutSeconds.value, 10)
  };
}

function syncSettingsPreviewFromForm() {
  state.settingsText = JSON.stringify(buildSettingsPayload(), null, 2) || "{}";
  els.settingsEditor.value = state.settingsText;
}

async function loadHealth(force = false) {
  if (state.health && !force) {
    return state.health;
  }
  try {
    const payload = await requestJson("/api/health");
    const ready = payload.ready !== undefined
      ? Boolean(payload.ready)
      : payload.ok !== undefined
        ? Boolean(payload.ok)
        : payload.healthy !== undefined
          ? Boolean(payload.healthy)
          : true;
    state.health = {
      ready,
      label: safeText(payload.status, ready ? "Healthy" : "Attention"),
      message: safeText(payload.message),
      version: safeText(payload.version)
    };
    state.healthError = "";
    return state.health;
  } catch (error) {
    state.health = null;
    state.healthError = error.message;
    throw error;
  }
}

async function loadSettings(force = false) {
  if (!force && state.settingsLoaded) {
    return state.settings;
  }
  try {
    const payload = await requestJson("/api/settings");
    state.settings = payload;
    state.settingsText = JSON.stringify(payload, null, 2) || "{}";
    state.settingsLoaded = true;
    state.settingsError = "";
    if (!state.settingsDirty) {
      populateSettingsForm(payload);
    }
    return state.settings;
  } catch (error) {
    state.settingsError = error.message;
    throw error;
  }
}

async function loadDashboard(force = false) {
  if (state.dashboard && !force) {
    return state.dashboard;
  }
  try {
    const payload = await requestJson("/api/dashboard");
    state.dashboard = normalizeDashboard(payload);
    state.dashboardError = "";
    return state.dashboard;
  } catch (error) {
    state.dashboardError = error.message;
    throw error;
  }
}

async function loadDetail(kanji, force = false) {
  if (state.kanjiCache.has(kanji) && !force) {
    return state.kanjiCache.get(kanji);
  }
  const payload = await requestJson(`/api/kanji/${encodeURIComponent(kanji)}`);
  const detail = normalizeDetail(payload, kanji);
  state.kanjiCache.set(kanji, detail);
  return detail;
}

async function loadStudyOverview(force = false) {
  if (state.studyOverview && !force) {
    return state.studyOverview;
  }
  try {
    const payload = await requestJson("/api/study/overview");
    state.studyOverview = normalizeStudyOverview(payload);
    state.studyOverviewError = "";
    return state.studyOverview;
  } catch (error) {
    state.studyOverviewError = error.message;
    throw error;
  }
}

function renderHealthPill() {
  if (state.health) {
    els.healthPill.textContent = state.health.version
      ? `${state.health.label} · ${state.health.version}`
      : state.health.label;
    els.healthPill.dataset.tone = state.health.ready ? "ready" : "warn";
    return;
  }
  els.healthPill.textContent = state.healthError || "Health unavailable";
  els.healthPill.dataset.tone = "error";
}

function renderStatusChips() {
  if (state.settingsLoaded) {
    els.statusSyncChip.textContent = state.settings.pollingEnabled
      ? `Auto sync every ${formatPollingInterval(state.settings.pollingIntervalSeconds)}`
      : "Manual sync only";
  } else {
    els.statusSyncChip.textContent = state.settingsError || "Checking sync mode...";
  }

  if (state.studyOverview) {
    const seedCount = asNumber(state.studyOverview.currentProblemSeedCount);
    els.statusSeedsChip.textContent = seedCount
      ? `${pluralize(seedCount, "seed")} ready`
      : "No seeds ready";
  } else {
    els.statusSeedsChip.textContent = state.studyOverviewError || "Checking study seeds...";
  }

  if (state.dashboard) {
    els.statusSourceChip.textContent = `Rank source: ${formatSourceKind(state.dashboard.jitenSourceKind)}`;
    return;
  }
  els.statusSourceChip.textContent = state.dashboardError || "Checking rank source...";
}

function renderDashboardLoading() {
  setView("dashboard");
  els.dashboardCopy.textContent = "Loading dashboard overview...";
  els.healthDetail.textContent = "Checking API health...";
  els.statusSyncChip.textContent = "Checking sync mode...";
  els.statusSeedsChip.textContent = "Checking study seeds...";
  els.statusSourceChip.textContent = "Checking rank source...";
  els.metricSuspended.textContent = "-";
  els.metricAverageRank.textContent = "-";
  els.metricTotalKanji.textContent = "-";
  els.metricUnknown.textContent = "-";
  els.dashboardAverageRankNote.textContent = "Loading dashboard metrics...";
  els.dashboardSupportThreshold.textContent = "Waiting for threshold...";
  els.dashboardStudyDue.textContent = "-";
  els.dashboardStudyNew.textContent = "-";
  els.dashboardStudyActive.textContent = "-";
  els.dashboardStudyTarget.textContent = "-";
  els.dashboardStudyNext.textContent = "Loading study overview...";
  els.dashboardStudyPreview.innerHTML = "";
  els.dashboardStudyEmpty.classList.add("is-hidden");
  els.dashboardSnapshotSeeds.textContent = "-";
  els.dashboardSnapshotTarget.textContent = "-";
  els.dashboardSnapshotActive.textContent = "-";
  els.dashboardSnapshotDue.textContent = "-";
  els.dashboardSnapshotNext.textContent = "Waiting for next due review...";
  els.dashboardWarnings.innerHTML = "";
  els.dashboardTbody.innerHTML = "";
  els.dashboardEmpty.classList.add("is-hidden");
}

function renderDashboard() {
  renderHealthPill();
  renderStatusChips();
  setView("dashboard");

  const dashboard = state.dashboard || normalizeDashboard({});
  const summary = dashboard.summary;
  const rowCount = dashboard.rows.length;
  els.dashboardCopy.textContent = rowCount
    ? `Study smarter, remember longer. ${formatCount(rowCount)} kanji still look fragile in the current collection snapshot.`
    : "Study smarter, remember longer. No fragile kanji are surfacing right now, but sync and seeds are ready when the collection changes.";
  els.healthDetail.textContent = state.health
    ? state.health.message || "API responded successfully to the health check."
    : state.healthError || "Health endpoint did not return usable data.";
  els.metricSuspended.textContent = formatCount(summary.analyzedSuspendedExpressionCount);
  els.metricAverageRank.textContent = formatRank(summary.averageKanjiRank);
  els.metricTotalKanji.textContent = formatCount(summary.totalKanjiCount);
  els.metricUnknown.textContent = formatCount(summary.unknownKanjiCount);
  els.dashboardAverageRankNote.textContent = rowCount
    ? `${pluralize(summary.analyzedSuspendedCardCount, "suspended card")} across ${pluralize(summary.analyzedSuspendedExpressionCount, "suspended expression")}. ${pluralize(summary.rankedKanjiCount, "kanji")} currently have a usable frequency rank, and ${pluralize(summary.problemSeedCount, "problem seed")} are feeding the study queue.`
    : "The dashboard still tracks support targets, queue health, and settings even when the current problem set is empty.";
  els.dashboardSupportThreshold.textContent = summary.matureSupportThreshold
    ? `${formatCount(summary.matureSupportThreshold)} mature cards target`
    : "Support target unavailable";

  els.dashboardWarnings.innerHTML = dashboard.warnings
    .map((warning) => `<div class="warning-chip">${escapeHtml(warning)}</div>`)
    .join("");
  renderDashboardStudyPanel();

  if (!state.settingsDirty) {
    populateSettingsForm(state.settings);
  }
  els.settingsState.textContent = state.settingsError
    ? `Settings could not be loaded: ${state.settingsError}`
    : "Edit the common settings directly here, then save them back to the server.";

  if (!dashboard.rows.length) {
    els.dashboardTbody.innerHTML = "";
    els.dashboardEmpty.classList.remove("is-hidden");
    return;
  }

  els.dashboardEmpty.classList.add("is-hidden");
  els.dashboardTbody.innerHTML = dashboard.rows
    .map((row) => `
      <tr>
        <td data-label="Kanji">
          <span class="link-cell">
            <button type="button" class="link-button" data-kanji-link="${escapeHtml(row.kanji)}">
              <strong>${escapeHtml(row.kanji)}</strong>
            </button>
            ${row.isUnknown ? '<span class="pill" data-tone="signal">Unknown</span>' : ""}
          </span>
        </td>
        <td data-label="Rank">${escapeHtml(formatRank(row.jitenRank))}</td>
        <td data-label="Suspended">${escapeHtml(formatCount(row.suspendedExpressionCount))}</td>
        <td data-label="Bridge">${escapeHtml(formatCount(row.activeRecurringExpressionCount))}</td>
        <td data-label="Mature">${escapeHtml(formatCount(row.matureSupportCount))}</td>
        <td data-label="Deficit">${escapeHtml(formatCount(row.supportDeficit))}</td>
      </tr>
    `)
    .join("");
}

function renderDefinitionRows(target, rows) {
  if (!rows.length) {
    target.innerHTML = '<div class="definition-row"><span class="definition-key">Status</span><span class="definition-value">No data returned.</span></div>';
    return;
  }
  target.innerHTML = rows
    .map((row) => `
      <div class="definition-row">
        <span class="definition-key">${escapeHtml(row.label)}</span>
        <span class="definition-value">${escapeHtml(row.value)}</span>
      </div>
    `)
    .join("");
}

function renderTokens(target, items, emptyLabel) {
  if (!items.length) {
    target.innerHTML = `<div class="token">${escapeHtml(emptyLabel)}</div>`;
    return;
  }
  target.innerHTML = items.map((item) => `<div class="token">${escapeHtml(item)}</div>`).join("");
}

function renderStackItems(target, rows, emptyLabel) {
  if (!rows.length) {
    target.innerHTML = `<div class="stack-item">${escapeHtml(emptyLabel)}</div>`;
    return;
  }
  target.innerHTML = rows
    .map((row) => `<div class="stack-item">${row}</div>`)
    .join("");
}

function buildStudyOverviewMessage(overview) {
  return overview.nextDueAt
    ? `Next due review: ${formatDateTime(overview.nextDueAt)}. ${formatCount(overview.currentProblemSeedCount)} current problem-child seeds are available for study.`
    : `${formatCount(overview.currentProblemSeedCount)} current problem-child seeds are available. No due review is scheduled yet.`;
}

function buildQueuePreviewMarkup(items, limit = items.length) {
  const previewItems = limit > 0 ? items.slice(0, limit) : items;
  return previewItems
    .map((item) => `
      <article class="queue-item">
        <div class="queue-item-head">
          <div>
            <h4>
              <button type="button" class="link-button" data-kanji-link="${escapeHtml(item.kanji)}">${escapeHtml(item.kanji)}</button>
            </h4>
            <p>${escapeHtml(item.guideLevelLabel)} • ${escapeHtml(item.itemStatus)}${item.dueNow ? " • due now" : item.dueAt ? ` • ${escapeHtml(formatDateTime(item.dueAt))}` : ""}</p>
          </div>
          <span class="pill" data-tone="${item.isProblemSeed ? "signal" : "accent"}">${item.isProblemSeed ? "Seed" : "Queued"}</span>
        </div>
        <p>Deficit ${escapeHtml(formatCount(item.supportDeficit))} • Suspended ${escapeHtml(formatCount(item.suspendedExpressionCount))} • Bridge ${escapeHtml(formatCount(item.activeRecurringExpressionCount))}</p>
      </article>
    `)
    .join("");
}

function renderQueuePreview(target, emptyTarget, items, limit = 0) {
  if (!items.length) {
    target.innerHTML = "";
    emptyTarget.classList.remove("is-hidden");
    return;
  }
  emptyTarget.classList.add("is-hidden");
  target.innerHTML = buildQueuePreviewMarkup(items, limit);
}

function renderDashboardStudyPanel() {
  const overview = state.studyOverview || normalizeStudyOverview({});
  els.dashboardStudyDue.textContent = formatCount(overview.dueCount);
  els.dashboardStudyNew.textContent = formatCount(overview.newCount);
  els.dashboardStudyActive.textContent = formatCount(overview.activeQueueCount);
  els.dashboardStudyTarget.textContent = formatPercent(overview.retentionTarget);
  els.dashboardStudyNext.textContent = overview.nextDueAt
    ? `Next due review ${formatDateTime(overview.nextDueAt)}.`
    : "No due review is scheduled yet.";
  renderQueuePreview(
    els.dashboardStudyPreview,
    els.dashboardStudyEmpty,
    overview.queuePreview,
    3
  );
  els.dashboardSnapshotSeeds.textContent = formatCount(overview.currentProblemSeedCount);
  els.dashboardSnapshotTarget.textContent = formatPercent(overview.retentionTarget);
  els.dashboardSnapshotActive.textContent = pluralize(overview.activeQueueCount, "item");
  els.dashboardSnapshotDue.textContent = pluralize(overview.dueCount, "review");
  els.dashboardSnapshotNext.textContent = buildStudyOverviewMessage(overview);
}

function summarizeNote(note) {
  const item = asObject(note);
  const primary = safeText(item.expression || item.word || item.text || item.fieldValue || item.front || item.note, "Collection note");
  const secondary = [
    safeText(item.reading),
    safeText(item.meaning),
    safeText(item.deckName),
    item.noteId != null ? `note ${item.noteId}` : ""
  ].filter(Boolean);
  return {
    title: primary,
    meta: secondary.join(" • ")
  };
}

function renderDetail(detail) {
  renderHealthPill();
  renderStatusChips();
  setView("detail");

  els.detailKanji.textContent = detail.kanji || "?";
  els.detailHeadline.textContent = detail.meanings.en[0] || `${detail.kanji} detail`;
  els.detailSubtitle.textContent = detail.collection.counts.isUnknown
    ? "This kanji is still marked unknown in the current collection scan."
    : `Browser search ready. ${formatCount(detail.collection.counts.collectionExpressionCount)} collection expressions are attached.`;
  els.detailRank.textContent = formatRank(detail.classification.jitenRank);
  els.detailDeficit.textContent = formatCount(detail.collection.counts.supportDeficit);
  els.detailSuspended.textContent = formatCount(detail.collection.counts.suspendedExpressionCount);
  els.detailMature.textContent = formatCount(detail.collection.counts.matureSupportCount);
  els.detailWritingMeta.textContent = detail.writing.strokeCount
    ? `Stroke count: ${detail.writing.strokeCount}${detail.writing.altStrokeCounts.length ? ` · alternate counts ${detail.writing.altStrokeCounts.join(", ")}` : ""}`
    : "No structured stroke count returned.";

  attachStrokeImage(els.detailStrokeImage, els.detailStrokeNote, detail.kanji, "Stroke order SVG unavailable for this kanji.");

  renderTokens(els.detailMeaningList, detail.meanings.en, "No meanings returned.");
  renderDefinitionRows(els.detailReadingList, [
    { label: "On", value: detail.readings.on.join(" ・ ") || "-" },
    { label: "Kun", value: detail.readings.kun.join(" ・ ") || "-" },
    { label: "Nanori", value: detail.readings.nanori.join(" ・ ") || "-" }
  ]);
  renderDefinitionRows(els.detailStructureList, [
    { label: "KanjiVG elements", value: detail.structure.kanjiVgElements.join(" ・ ") || "-" },
    { label: "Component hint", value: detail.structure.componentHint || "-" }
  ]);
  els.detailBrowserSearch.textContent = detail.collection.browserSearch || "No browser search string returned.";

  renderStackItems(
    els.detailCollectionList,
    [
      `<strong>${escapeHtml(formatCount(detail.collection.counts.collectionExpressionCount))}</strong> collection expressions`,
      `<strong>${escapeHtml(formatCount(detail.collection.counts.suspendedExpressionCount))}</strong> suspended expressions`,
      `<strong>${escapeHtml(formatCount(detail.collection.counts.activeRecurringExpressionCount))}</strong> active recurring expressions`,
      `<strong>${escapeHtml(formatCount(detail.collection.counts.matureSupportCount))}</strong> mature supporting expressions`,
      `<strong>${escapeHtml(formatCount(detail.collection.counts.supportDeficit))}</strong> support deficit`
    ],
    "No collection counts returned."
  );

  const supportRows = [
    ...detail.collection.suspendedExpressions.map((item) => `Suspended: ${escapeHtml(item)}`),
    ...detail.collection.activeRecurringExpressions.map((item) => `Bridge: ${escapeHtml(item)}`),
    ...detail.collection.matureSupportingExpressions.map((item) => `Mature: ${escapeHtml(item)}`)
  ];
  renderStackItems(els.detailSupportList, supportRows, "No supporting expressions returned.");

  const noteRows = [
    ...uniqueList(detail.collection.collectionExpressions).map((item) => `Collection expression: ${escapeHtml(item)}`),
    ...uniqueList(detail.relatedVocabulary.painExamples).map((item) => `Suspended pressure: ${escapeHtml(item)}`),
    ...uniqueList(detail.relatedVocabulary.bridgeExamples).map((item) => `Bridge example: ${escapeHtml(item)}`),
    ...uniqueList(detail.relatedVocabulary.matureExamples).map((item) => `Mature support: ${escapeHtml(item)}`),
    ...detail.relatedVocabulary.jmdictExamples.map((item) => {
      const entry = asObject(item);
      const pieces = [safeText(entry.expression || entry.word), safeText(entry.reading), safeText(entry.gloss || entry.meaning)]
        .filter(Boolean)
        .join(" • ");
      return pieces ? `Dictionary example: ${escapeHtml(pieces)}` : `Dictionary example: ${escapeHtml(JSON.stringify(entry))}`;
    }),
    ...detail.collection.matchingNotes.slice(0, 12).map((note) => {
      const summary = summarizeNote(note);
      return `<strong>${escapeHtml(summary.title)}</strong>${summary.meta ? `<br>${escapeHtml(summary.meta)}` : ""}`;
    })
  ];
  renderStackItems(els.detailNoteList, noteRows, "No collection pressure examples returned.");

  const sourceRows = detail.sources.map((source) => {
    const entry = asObject(source);
    const pieces = [safeText(entry.name || entry.label || entry.id, "Source"), safeText(entry.role), safeText(entry.description)]
      .filter(Boolean)
      .join(" • ");
    return escapeHtml(pieces);
  });
  renderStackItems(els.detailSourceList, sourceRows, "No source metadata returned.");
}

function renderStudyLoading() {
  renderStatusChips();
  setView("study");
  els.studyDue.textContent = "-";
  els.studyNew.textContent = "-";
  els.studyActive.textContent = "-";
  els.studyTarget.textContent = "-";
  els.studyNext.textContent = "Loading study overview...";
  els.studyPreview.innerHTML = "";
  els.studyAttribution.innerHTML = "";
  els.studyEmpty.classList.add("is-hidden");
}

function renderStudyOverview() {
  renderHealthPill();
  renderStatusChips();
  setView("study");
  const overview = state.studyOverview || normalizeStudyOverview({});
  els.studyDue.textContent = formatCount(overview.dueCount);
  els.studyNew.textContent = formatCount(overview.newCount);
  els.studyActive.textContent = formatCount(overview.activeQueueCount);
  els.studyTarget.textContent = formatPercent(overview.retentionTarget);
  els.studyNext.textContent = buildStudyOverviewMessage(overview);
  renderQueuePreview(els.studyPreview, els.studyEmpty, overview.queuePreview);

  const attributionEntries = Object.entries(asObject(overview.attribution));
  renderDefinitionRows(
    els.studyAttribution,
    attributionEntries.length
      ? attributionEntries.map(([key, value]) => ({
          label: key,
          value: Array.isArray(value) ? value.join(", ") : safeText(value, "-")
        }))
      : [{ label: "Attribution", value: "No attribution metadata returned." }]
  );
}

function handwritingGuideMode(session) {
  const level = asNumber(asObject(session).guideLevel);
  const policy = asObject(asObject(session).handwritingPolicy);
  return safeText(policy.guideMode, GUIDE_MODE_BY_LEVEL[level] || GUIDE_MODE_BY_LEVEL[3]);
}

function handwritingSupportsGuidedEvaluation(session, ui) {
  if (!session || !ui) {
    return false;
  }
  const strokeOrder = asObject(asObject(session).content).strokeOrder;
  const policy = asObject(asObject(session).handwritingPolicy);
  return Boolean(
    strokeOrder.available
      && asBoolean(policy.guidedEvaluationAvailable)
      && asObject(ui.handwriting).referenceGuideData
  );
}

function handwritingPhaseLabel(phase) {
  if (phase === "drawing") {
    return "Drawing";
  }
  if (phase === "evaluated") {
    return "Evaluated";
  }
  if (phase === "overridden") {
    return "Manual override";
  }
  return "Ready";
}

function handwritingGuideLabel(mode) {
  if (mode === "trace") {
    return "Ghost strokes stay visible from the start so you can trace into clean recall.";
  }
  if (mode === "outline") {
    return "A faint outline stays on the pad as a scaffold, but stroke order and direction still count.";
  }
  if (mode === "minimal-hints") {
    return "Only the practice grid is visible. Reveal the answer, then open a hint only if you need one.";
  }
  return "Start from a blank square. Reveal the answer after your first attempt, then ask for a hint if needed.";
}

function handwritingShouldShowReferenceOverlay(session, ui) {
  if (!session || !ui || !asObject(ui.handwriting).referenceGuideData) {
    return false;
  }
  const mode = handwritingGuideMode(session);
  return mode === "trace" || mode === "outline";
}

function handwritingAllowedRatings(session = state.currentSession, ui = state.sessionUi) {
  if (!session || !ui || !ui.revealed) {
    return [];
  }
  const baseRatings = session.outcomeMode === "binary"
    ? BINARY_RATING_ORDER.slice()
    : RATING_ORDER.slice();
  if (!session.requiresWriting) {
    return baseRatings;
  }
  const result = asObject(ui.handwriting).result;
  if (!result) {
    return [];
  }
  if (result.passed) {
    return baseRatings;
  }
  const policy = asObject(session.handwritingPolicy);
  const allowed = stringList(policy.allowedRatingsOnFailure);
  return allowed.length
    ? allowed.filter((rating) => baseRatings.includes(String(rating).toLowerCase()))
    : ["again"];
}

function canSubmitRating(rating = "") {
  if (!state.currentSession || !state.sessionUi || !state.sessionUi.revealed) {
    return false;
  }
  const allowedRatings = handwritingAllowedRatings(state.currentSession, state.sessionUi);
  if (!allowedRatings.length) {
    return false;
  }
  if (!rating) {
    return true;
  }
  return allowedRatings.includes(String(rating).toLowerCase());
}

function buildHandwritingResult() {
  const handwriting = state.sessionUi
    ? state.sessionUi.handwriting
    : { strokes: [], result: null, phase: "prewrite" };
  const result = handwriting.result;
  const mode = safeText(result && result.mode, "unrated");
  return {
    attempted: handwriting.strokes.length > 0 || Boolean(result),
    passed: Boolean(result && result.passed),
    score: result ? result.score : 0,
    hintsUsed: state.sessionUi ? state.sessionUi.hintsUsed : 0,
    strokeCount: handwriting.strokes.length,
    manual: !result || mode.startsWith("manual") || mode.startsWith("override"),
    selfAssessment: result ? result.label : "unrated",
    evaluationMode: result ? result.mode : "unrated",
    guideThreshold: result && result.threshold != null ? result.threshold : null,
    phase: safeText(handwriting.phase, "prewrite"),
    analysis: result
      ? {
          ...asObject(result.metrics),
          reasons: stringList(result.reasons),
          reasonSummary: safeText(result.reasonSummary),
          phase: safeText(handwriting.phase, "prewrite")
        }
      : null
  };
}

function currentPrompt(session) {
  return session.promptType === "production"
    ? session.prompts.production
    : session.prompts.recognition;
}

function visibleRatingButtons(session = state.currentSession) {
  if (!session) {
    return BINARY_RATING_ORDER.slice();
  }
  return session.outcomeMode === "binary"
    ? BINARY_RATING_ORDER.slice()
    : RATING_ORDER.slice();
}

function resampleStroke(stroke, sampleCount = 16) {
  const points = asArray(stroke)
    .map((point) => ({
      x: Number(asObject(point).x),
      y: Number(asObject(point).y)
    }))
    .filter((point) => Number.isFinite(point.x) && Number.isFinite(point.y));
  if (!points.length) {
    return [];
  }
  if (points.length === 1) {
    return Array.from({ length: sampleCount }, () => ({ ...points[0] }));
  }

  const lengths = [0];
  let total = 0;
  for (let index = 1; index < points.length; index += 1) {
    total += Math.hypot(
      points[index].x - points[index - 1].x,
      points[index].y - points[index - 1].y
    );
    lengths.push(total);
  }
  if (total === 0) {
    return Array.from({ length: sampleCount }, () => ({ ...points[0] }));
  }

  const samples = [];
  for (let index = 0; index < sampleCount; index += 1) {
    const target = total * (index / Math.max(sampleCount - 1, 1));
    let segment = 1;
    while (segment < lengths.length && lengths[segment] < target) {
      segment += 1;
    }
    const upper = Math.min(segment, points.length - 1);
    const lower = Math.max(upper - 1, 0);
    const spanStart = lengths[lower];
    const spanEnd = lengths[upper];
    const ratio = spanEnd === spanStart ? 0 : (target - spanStart) / (spanEnd - spanStart);
    const from = points[lower];
    const to = points[upper];
    samples.push({
      x: from.x + (to.x - from.x) * ratio,
      y: from.y + (to.y - from.y) * ratio
    });
  }
  return samples;
}

function sampleReferencePath(pathData, sampleCount = 16) {
  try {
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", pathData);
    const total = path.getTotalLength();
    if (!Number.isFinite(total) || total <= 0) {
      return [];
    }
    const samples = [];
    for (let index = 0; index < sampleCount; index += 1) {
      const point = path.getPointAtLength(total * (index / Math.max(sampleCount - 1, 1)));
      samples.push({ x: point.x, y: point.y });
    }
    return samples;
  } catch (error) {
    return [];
  }
}

function normalizeStrokeSets(strokeSets) {
  const allPoints = strokeSets.flat();
  if (!allPoints.length) {
    return strokeSets;
  }
  const xs = allPoints.map((point) => point.x);
  const ys = allPoints.map((point) => point.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const size = Math.max(maxX - minX, maxY - minY, 0.0001);
  return strokeSets.map((stroke) => stroke.map((point) => ({
    x: (point.x - minX) / size,
    y: (point.y - minY) / size
  })));
}

function normalizeSingleStroke(stroke) {
  return normalizeStrokeSets([stroke])[0] || [];
}

function averagePointDistance(left, right) {
  if (!left.length || !right.length) {
    return 1;
  }
  const count = Math.min(left.length, right.length);
  let total = 0;
  for (let index = 0; index < count; index += 1) {
    total += Math.hypot(left[index].x - right[index].x, left[index].y - right[index].y);
  }
  return total / count;
}

function strokeEndpointDistance(left, right, reversed = false) {
  if (!left.length || !right.length) {
    return 1;
  }
  const rightStart = reversed ? right[right.length - 1] : right[0];
  const rightEnd = reversed ? right[0] : right[right.length - 1];
  const leftStart = left[0];
  const leftEnd = left[left.length - 1];
  return (
    Math.hypot(leftStart.x - rightStart.x, leftStart.y - rightStart.y)
      + Math.hypot(leftEnd.x - rightEnd.x, leftEnd.y - rightEnd.y)
  ) / 2;
}

function bestStrokeMatch(stroke, candidates) {
  let bestIndex = -1;
  let bestDistance = Infinity;
  candidates.forEach((candidate, index) => {
    const distance = averagePointDistance(stroke, candidate);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  });
  return { index: bestIndex, distance: bestDistance };
}

function guideThreshold(level) {
  if (level <= 0) {
    return 0.5;
  }
  if (level === 1) {
    return 0.58;
  }
  if (level === 2) {
    return 0.66;
  }
  return 0.74;
}

function evaluateGuidedHandwriting(session, ui) {
  const guideData = asObject(asObject(ui).handwriting).referenceGuideData;
  if (!guideData) {
    return null;
  }

  const referenceGuideStrokes = asArray(guideData.guideStrokes);
  const referenceShapeStrokes = asArray(guideData.shapeStrokes);
  const drawnRawStrokes = ui.handwriting.strokes
    .map((stroke) => resampleStroke(stroke))
    .filter((stroke) => stroke.length);
  if (!drawnRawStrokes.length || !referenceGuideStrokes.length) {
    return null;
  }

  const drawnGuideStrokes = normalizeStrokeSets(drawnRawStrokes);
  const drawnShapeStrokes = drawnRawStrokes.map((stroke) => normalizeSingleStroke(stroke));
  const pairCount = Math.min(drawnGuideStrokes.length, referenceGuideStrokes.length);
  const expectedStrokeCount = session.content.strokeOrder.strokeCount || referenceGuideStrokes.length;
  let shapeTotal = 0;
  let placementTotal = 0;
  let endpointTotal = 0;
  let orderTotal = 0;
  let directionTotal = 0;
  let reversedStrokeCount = 0;
  let orderMissCount = 0;
  for (let index = 0; index < pairCount; index += 1) {
    const drawnGuide = drawnGuideStrokes[index];
    const drawnShape = drawnShapeStrokes[index];
    const referenceGuide = referenceGuideStrokes[index];
    const referenceShape = referenceShapeStrokes[index];
    const shapeDistance = averagePointDistance(drawnShape, referenceShape);
    const placementDistance = averagePointDistance(drawnGuide, referenceGuide);
    const forwardEndpointDistance = strokeEndpointDistance(drawnGuide, referenceGuide, false);
    const reverseEndpointDistance = strokeEndpointDistance(drawnGuide, referenceGuide, true);
    const reverseBetter = reverseEndpointDistance + 0.05 < forwardEndpointDistance;
    const bestMatch = bestStrokeMatch(drawnShape, referenceShapeStrokes);
    const sameIndexGap = Math.max(0, shapeDistance - bestMatch.distance);
    const orderScore = bestMatch.index === index
      ? 1
      : clamp(1 - ((sameIndexGap + 0.05) / 0.32));
    if (orderScore < 0.72) {
      orderMissCount += 1;
    }
    if (reverseBetter) {
      reversedStrokeCount += 1;
    }
    shapeTotal += clamp(1 - shapeDistance / 0.62);
    placementTotal += clamp(1 - placementDistance / 0.48);
    endpointTotal += clamp(1 - forwardEndpointDistance / 0.45);
    orderTotal += orderScore;
    directionTotal += reverseBetter
      ? clamp(1 - reverseEndpointDistance / 0.45) * 0.3
      : clamp(1 - forwardEndpointDistance / 0.45);
  }

  const shapeScore = pairCount ? shapeTotal / pairCount : 0;
  const placementScore = pairCount ? placementTotal / pairCount : 0;
  const endpointScore = pairCount ? endpointTotal / pairCount : 0;
  const orderScore = pairCount ? orderTotal / pairCount : 0;
  const directionScore = pairCount ? directionTotal / pairCount : 0;
  const strokeDelta = Math.abs(drawnGuideStrokes.length - expectedStrokeCount);
  const countScore = expectedStrokeCount
    ? clamp(1 - strokeDelta / Math.max(expectedStrokeCount, 1))
    : clamp(pairCount / Math.max(referenceGuideStrokes.length, 1));
  const coverageScore = clamp(
    pairCount / Math.max(drawnGuideStrokes.length, referenceGuideStrokes.length, 1)
  );
  const hintPenalty = Math.min(0.16, ui.hintsUsed * 0.05);
  const threshold = guideThreshold(session.content.strokeOrder.guideLevel);
  const score = clamp(
    (shapeScore * 0.28)
      + (placementScore * 0.16)
      + (endpointScore * 0.14)
      + (orderScore * 0.14)
      + (directionScore * 0.12)
      + (countScore * 0.1)
      + (coverageScore * 0.06)
      - hintPenalty
  );
  const passed = score >= threshold
    && strokeDelta <= Math.max(1, Math.round(expectedStrokeCount * 0.2))
    && orderScore >= 0.6
    && directionScore >= 0.58
    && placementScore >= 0.52;

  const reasons = [];
  if (strokeDelta) {
    reasons.push(`stroke count was off by ${strokeDelta}`);
  }
  if (orderScore < 0.6 || orderMissCount) {
    reasons.push("stroke order drifted away from the reference");
  }
  if (directionScore < 0.58 || reversedStrokeCount) {
    reasons.push("one or more strokes ran in the wrong direction");
  }
  if (placementScore < 0.52) {
    reasons.push("stroke placement drifted too far from the frame");
  }
  if (shapeScore < 0.58) {
    reasons.push("stroke shapes did not line up closely enough");
  }
  if (!passed && !reasons.length) {
    reasons.push("the overall trace stayed below the target");
  }
  const reasonSummary = reasons.slice(0, 2).join(". ");

  return {
    passed,
    score,
    threshold,
    mode: "guided",
    label: passed ? "guided-pass" : "guided-retry",
    reasons,
    reasonSummary,
    metrics: {
      expectedStrokeCount,
      capturedStrokeCount: drawnGuideStrokes.length,
      shapeScore,
      placementScore,
      endpointScore,
      orderScore,
      countScore,
      coverageScore,
      directionScore,
      hintsUsed: ui.hintsUsed,
      reversedStrokeCount,
      orderMissCount
    },
    message: passed
      ? `Guide pass at ${Math.round(score * 100)}% against a ${Math.round(threshold * 100)}% target.${ui.hintsUsed ? ` ${formatCount(ui.hintsUsed)} hint${ui.hintsUsed === 1 ? "" : "s"} used.` : ""}`
      : `Guide retry at ${Math.round(score * 100)}% against a ${Math.round(threshold * 100)}% target.${reasonSummary ? ` ${reasonSummary}.` : ""}`
  };
}

function evaluateHandwriting() {
  if (!state.currentSession || !state.sessionUi) {
    return;
  }
  if (!handwritingSupportsGuidedEvaluation(state.currentSession, state.sessionUi)) {
    return;
  }
  const guidedResult = evaluateGuidedHandwriting(state.currentSession, state.sessionUi);
  if (!guidedResult) {
    return;
  }
  state.sessionUi.handwriting.result = guidedResult;
  state.sessionUi.handwriting.phase = "evaluated";
  renderStudySession();
}

function renderStudySession() {
  if (!state.currentSession || !state.sessionUi) {
    els.studySessionEmpty.classList.remove("is-hidden");
    els.studySessionContent.classList.add("is-hidden");
    return;
  }

  const session = state.currentSession;
  const ui = state.sessionUi;
  const prompt = currentPrompt(session);
  const answerVisible = ui.revealed;
  const handwritingVisible = session.requiresWriting;
  const result = ui.handwriting.result;
  const strokeGuide = session.content.strokeOrder;
  const expectedStrokeCount = strokeGuide.strokeCount || strokeGuide.paths.length || 0;
  const guidedEvaluationReady = handwritingSupportsGuidedEvaluation(session, ui);
  const guideMode = handwritingGuideMode(session);
  const allowedRatings = handwritingAllowedRatings(session, ui);
  const visibleRatings = visibleRatingButtons(session);
  const failedWritingCapsRatings = Boolean(
    handwritingVisible
      && result
      && !result.passed
      && allowedRatings.length
      && allowedRatings.length < RATING_ORDER.length
  );
  const ratingLockCopy = failedWritingCapsRatings
    ? `${allowedRatings.map((rating) => formatRatingLabel(rating)).join(" / ")} are unlocked after this result.`
    : "All recall ratings are available once the handwriting check passes.";

  els.studySessionEmpty.classList.add("is-hidden");
  els.studySessionContent.classList.remove("is-hidden");
  els.sessionLabel.textContent = session.promptLabel;
  els.sessionTitle.textContent = `${session.kanji} · ${session.sessionKind === "review" ? "due review" : "new introduction"}`;
  els.sessionCopy.textContent = [
    session.guideLevelLabel,
    session.dueNow ? "Due now" : session.dueAt ? `Due ${formatDateTime(session.dueAt)}` : "Unscheduled",
    session.requiresWriting ? "Writing required" : "Writing optional"
  ].join(" • ");

  els.sessionBadges.innerHTML = [
    { label: session.promptType === "production" ? "Production prompt" : "Recognition prompt", tone: "accent" },
    { label: session.sessionKind === "review" ? "Review" : "Learn", tone: "ink" },
    { label: session.guideLevelLabel, tone: "signal" },
    { label: session.content.fontVariantLabel || "Canonical print", tone: "accent" }
  ].map((pill) => `<span class="pill" data-tone="${pill.tone}">${escapeHtml(pill.label)}</span>`).join("");

  if (session.promptType === "recognition") {
    els.sessionPromptKanji.textContent = session.prompts.recognition.kanji || session.kanji;
    els.sessionPromptKanji.style.fontFamily = session.content.fontFamily || "";
    els.sessionPromptKanji.classList.remove("is-hidden");
    els.sessionPromptKeyword.textContent = `Recognize this kanji in ${safeText(session.content.fontVariantLabel, "a different print style").toLowerCase()} before you reveal the answer bundle.`;
  } else {
    els.sessionPromptKanji.textContent = "字";
    els.sessionPromptKanji.style.fontFamily = "";
    els.sessionPromptKanji.classList.add("is-hidden");
    els.sessionPromptKeyword.textContent = prompt.keyword
      ? `Keyword: ${prompt.keyword}`
      : "Recall the kanji from meaning and context before you reveal it.";
  }

  els.sessionContext.innerHTML = stringList(prompt.context).length
    ? stringList(prompt.context).map((item) => `<span class="context-chip">${escapeHtml(item)}</span>`).join("")
    : '<span class="context-chip">No context returned for this prompt.</span>';
  els.sessionInstruction.textContent = prompt.instruction || "Reveal the answer when ready.";
  els.sessionReveal.textContent = answerVisible ? "Answer revealed" : "Reveal answer";
  els.sessionReveal.disabled = answerVisible;

  els.sessionAnswerPanel.classList.toggle("is-hidden", !answerVisible);
  if (answerVisible) {
    els.sessionAnswerKanji.textContent = session.answer.kanji || session.kanji;
    els.sessionAnswerSummary.textContent = session.support.whyInQueue || "No queue explanation returned.";
    renderDefinitionRows(els.sessionAnswerFacts, [
      { label: "Keyword", value: session.answer.keyword || "-" },
      { label: "Meanings", value: session.answer.meanings.join(" ・ ") || "-" },
      { label: "Primary readings", value: session.answer.primaryReadings.join(" ・ ") || session.answer.readings.join(" ・ ") || "-" },
      { label: "Component hint", value: session.answer.componentHint || "-" },
      { label: "Support deficit", value: formatCount(session.support.supportDeficit) },
      { label: "Browser search", value: session.support.browserSearch || "-" }
    ]);
    attachStrokeImage(els.sessionStrokeImage, els.sessionStrokeNote, session.kanji, "No stroke order SVG was returned for this session.");
  } else {
    els.sessionStrokeImage.classList.add("is-hidden");
    els.sessionStrokeNote.textContent = "Reveal the answer to load the stroke order diagram.";
  }

  els.handwritingPanel.classList.toggle("is-hidden", !handwritingVisible);
  if (handwritingVisible) {
    els.handwritingCopy.textContent = handwritingGuideLabel(guideMode);
    els.handwritingGuide.innerHTML = [
      `<strong>${escapeHtml(strokeGuide.guideLevelLabel || session.guideLevelLabel)}</strong>`,
      `<span> · ${escapeHtml(handwritingPhaseLabel(ui.handwriting.phase))}</span>`,
      `<span> · expected ${escapeHtml(formatCount(expectedStrokeCount))}</span>`,
      `<span> · captured ${escapeHtml(formatCount(ui.handwriting.strokes.length))}</span>`,
      `<span> · ${escapeHtml(ratingLockCopy)}</span>`
    ].join("");
    els.sessionHint.disabled = !answerVisible;
    els.handwritingEvaluate.classList.toggle("is-hidden", !guidedEvaluationReady);
    els.handwritingEvaluate.disabled = !answerVisible || !ui.handwriting.strokes.length || !guidedEvaluationReady;
    els.handwritingPass.disabled = !answerVisible || !ui.handwriting.strokes.length;
    els.handwritingFail.disabled = !answerVisible;
    els.canvasUndo.disabled = ui.handwriting.pointerId !== null || !ui.handwriting.strokes.length;
    els.canvasClear.disabled = !ui.handwriting.strokes.length && !ui.handwriting.currentStroke.length;
    els.handwritingStatus.textContent = result
      ? result.message
      : answerVisible
        ? guidedEvaluationReady
          ? "Run the guided check or override it manually after comparing against the answer."
          : "Guide geometry is unavailable here, so use the manual pass or retry after comparing against the answer."
        : guideMode === "trace"
          ? "Trace along the ghost strokes, then reveal the answer when you want to score the attempt."
          : guideMode === "outline"
            ? "Use the outline lightly, then reveal the answer before you score or override the attempt."
            : guideMode === "minimal-hints"
              ? "Draw from memory first. Reveal the answer before you check the attempt or open a hint."
              : "Start from a blank square, then reveal the answer only after your first attempt.";
    renderDefinitionRows(els.handwritingMetrics, [
      { label: "Guide mode", value: guidedEvaluationReady ? "Progressive guided scoring" : "Manual override only" },
      { label: "Stage", value: handwritingPhaseLabel(ui.handwriting.phase) },
      { label: "Expected strokes", value: expectedStrokeCount ? formatCount(expectedStrokeCount) : "-" },
      { label: "Captured strokes", value: formatCount(ui.handwriting.strokes.length) },
      { label: "Hints used", value: formatCount(ui.hintsUsed) },
      {
        label: "Latest score",
        value: result && result.score != null
          ? `${Math.round(result.score * 100)}%${result.threshold != null ? ` / target ${Math.round(result.threshold * 100)}%` : ""}`
          : "No score yet"
      },
      {
        label: "Allowed outcomes",
        value: allowedRatings.length
          ? allowedRatings.map((rating) => formatRatingLabel(rating)).join(" ・ ")
          : "No outcome unlocked yet"
      }
    ]);
    els.handwritingHintWrap.classList.toggle("is-hidden", !ui.hintVisible);
    if (ui.hintVisible) {
      attachStrokeImage(els.handwritingHintImage, els.handwritingHintNote, session.kanji, "No stroke order hint is available for this kanji.");
    }
    resizeCanvas();
    renderCanvas();
  }

  if (!canSubmitRating()) {
    els.ratingHelp.textContent = session.requiresWriting
      ? answerVisible
        ? failedWritingCapsRatings
          ? `This handwriting result caps the review at ${allowedRatings.map((rating) => formatRatingLabel(rating)).join(" / ")} until you pass or override it.`
          : "Self-check the handwriting attempt before recording an outcome."
        : "Reveal the answer before you self-check and record an outcome."
      : "Reveal the answer before recording an outcome.";
  } else if (failedWritingCapsRatings) {
    els.ratingHelp.textContent = `Handwriting marked for another pass. ${allowedRatings.map((rating) => formatRatingLabel(rating)).join(" / ")} are available until you improve or override the attempt.`;
  } else {
    els.ratingHelp.textContent = "Record a simple pass or fail for what was actually in memory.";
  }
  els.ratingButtons.forEach((button) => {
    const rating = safeText(button.dataset.rating).toLowerCase();
    button.textContent = formatRatingLabel(rating);
    button.classList.toggle("is-hidden", !visibleRatings.includes(rating));
    button.disabled = !canSubmitRating(rating);
  });
}

function navigate(hash) {
  window.location.hash = hash;
}

function attachStrokeImage(image, note, kanji, fallbackText) {
  const nextSrc = strokeAssetUrl(kanji);
  if (image.dataset.assetUrl === nextSrc) {
    return;
  }
  image.dataset.assetUrl = nextSrc;
  note.textContent = "Loading stroke order asset…";
  image.classList.add("is-hidden");
  image.onload = () => {
    image.classList.remove("is-hidden");
    note.textContent = "";
  };
  image.onerror = () => {
    image.classList.add("is-hidden");
    note.textContent = fallbackText;
  };
  image.src = nextSrc;
}

function eventPoint(event) {
  const rect = els.handwritingCanvas.getBoundingClientRect();
  const x = (event.clientX - rect.left) / rect.width;
  const y = (event.clientY - rect.top) / rect.height;
  return {
    x: Math.max(0, Math.min(1, x)),
    y: Math.max(0, Math.min(1, y))
  };
}

function canDraw() {
  return Boolean(state.currentSession && state.sessionUi && !els.handwritingPanel.classList.contains("is-hidden"));
}

function resizeCanvas() {
  const canvas = els.handwritingCanvas;
  if (!canvas || canvas.offsetWidth === 0) {
    return;
  }
  const dpr = window.devicePixelRatio || 1;
  const size = Math.max(240, Math.round(canvas.getBoundingClientRect().width));
  canvas.width = Math.round(size * dpr);
  canvas.height = Math.round(size * dpr);
}

function drawGrid(context, width, height) {
  context.save();
  context.strokeStyle = "rgba(24, 34, 30, 0.12)";
  context.lineWidth = 1;
  context.beginPath();
  context.moveTo(width * 0.5, 0);
  context.lineTo(width * 0.5, height);
  context.moveTo(0, height * 0.5);
  context.lineTo(width, height * 0.5);
  context.moveTo(0, 0);
  context.lineTo(width, height);
  context.moveTo(width, 0);
  context.lineTo(0, height);
  context.stroke();
  context.restore();
}

function drawStroke(context, stroke, width, height) {
  if (!stroke.length) {
    return;
  }
  context.save();
  context.strokeStyle = "#1f6b57";
  context.lineWidth = Math.max(4, width * 0.014);
  context.lineCap = "round";
  context.lineJoin = "round";
  context.beginPath();
  context.moveTo(stroke[0].x * width, stroke[0].y * height);
  for (let index = 1; index < stroke.length; index += 1) {
    context.lineTo(stroke[index].x * width, stroke[index].y * height);
  }
  context.stroke();
  context.restore();
}

function drawReferenceStroke(context, stroke, width, height, mode) {
  if (!stroke.length) {
    return;
  }
  context.save();
  context.strokeStyle = mode === "trace" ? "rgba(31, 107, 87, 0.24)" : "rgba(31, 107, 87, 0.16)";
  context.lineWidth = mode === "trace"
    ? Math.max(5, width * 0.02)
    : Math.max(3, width * 0.012);
  context.lineCap = "round";
  context.lineJoin = "round";
  if (mode === "outline") {
    context.setLineDash([Math.max(8, width * 0.03), Math.max(6, width * 0.02)]);
  }
  context.beginPath();
  context.moveTo(stroke[0].x * width, stroke[0].y * height);
  for (let index = 1; index < stroke.length; index += 1) {
    context.lineTo(stroke[index].x * width, stroke[index].y * height);
  }
  context.stroke();
  if (mode === "trace") {
    context.fillStyle = "rgba(31, 107, 87, 0.3)";
    context.beginPath();
    context.arc(
      stroke[0].x * width,
      stroke[0].y * height,
      Math.max(3, width * 0.012),
      0,
      Math.PI * 2
    );
    context.fill();
  }
  context.restore();
}

function renderCanvas() {
  const canvas = els.handwritingCanvas;
  const context = canvas.getContext("2d");
  const width = canvas.width;
  const height = canvas.height;
  context.clearRect(0, 0, width, height);
  context.fillStyle = "rgba(255, 251, 244, 0.98)";
  context.fillRect(0, 0, width, height);
  drawGrid(context, width, height);
  if (!state.sessionUi || !state.currentSession) {
    return;
  }
  const handwriting = state.sessionUi.handwriting;
  if (handwritingShouldShowReferenceOverlay(state.currentSession, state.sessionUi)) {
    const mode = handwritingGuideMode(state.currentSession);
    asArray(asObject(handwriting.referenceGuideData).guideStrokes)
      .forEach((stroke) => drawReferenceStroke(context, stroke, width, height, mode));
  }
  handwriting.strokes.forEach((stroke) => drawStroke(context, stroke, width, height));
  drawStroke(context, handwriting.currentStroke, width, height);
}

function resetHandwritingResult() {
  if (!state.sessionUi) {
    return;
  }
  state.sessionUi.handwriting.result = null;
  state.sessionUi.handwriting.phase = state.sessionUi.handwriting.strokes.length
    ? "drawing"
    : "prewrite";
}

function clearCanvas() {
  if (!state.sessionUi) {
    return;
  }
  state.sessionUi.handwriting.strokes = [];
  state.sessionUi.handwriting.currentStroke = [];
  state.sessionUi.handwriting.pointerId = null;
  resetHandwritingResult();
  renderStudySession();
}

function undoLastStroke() {
  if (!state.sessionUi || state.sessionUi.handwriting.pointerId !== null) {
    return;
  }
  state.sessionUi.handwriting.strokes.pop();
  resetHandwritingResult();
  renderStudySession();
}

function beginStroke(event) {
  if (!canDraw()) {
    return;
  }
  if (state.sessionUi.handwriting.pointerId !== null) {
    return;
  }
  event.preventDefault();
  resetHandwritingResult();
  state.sessionUi.handwriting.pointerId = event.pointerId;
  state.sessionUi.handwriting.currentStroke = [eventPoint(event)];
  state.sessionUi.handwriting.phase = "drawing";
  els.handwritingCanvas.setPointerCapture(event.pointerId);
  renderCanvas();
}

function moveStroke(event) {
  if (!canDraw()) {
    return;
  }
  if (state.sessionUi.handwriting.pointerId !== event.pointerId) {
    return;
  }
  event.preventDefault();
  state.sessionUi.handwriting.currentStroke.push(eventPoint(event));
  renderCanvas();
}

function endStroke(event) {
  if (!canDraw()) {
    return;
  }
  if (state.sessionUi.handwriting.pointerId !== event.pointerId) {
    return;
  }
  event.preventDefault();
  const current = state.sessionUi.handwriting.currentStroke;
  if (current.length) {
    state.sessionUi.handwriting.strokes.push(current.slice());
  }
  state.sessionUi.handwriting.currentStroke = [];
  state.sessionUi.handwriting.pointerId = null;
  state.sessionUi.handwriting.phase = state.sessionUi.handwriting.strokes.length
    ? "drawing"
    : "prewrite";
  renderStudySession();
}

function markHandwriting(passed) {
  if (!state.sessionUi) {
    return;
  }
  const strokeCount = state.sessionUi.handwriting.strokes.length;
  if (passed && !strokeCount) {
    return;
  }
  state.sessionUi.handwriting.result = {
    passed,
    score: passed ? 1 : strokeCount > 0 ? 0.25 : 0,
    threshold: null,
    mode: "manual-override",
    label: passed ? "override-pass" : "override-retry",
    reasons: passed ? [] : ["manual self-check kept this attempt below the pass line"],
    reasonSummary: passed ? "" : "manual self-check kept this attempt below the pass line",
    metrics: {
      expectedStrokeCount: state.currentSession ? state.currentSession.content.strokeOrder.strokeCount || 0 : 0,
      capturedStrokeCount: strokeCount,
      hintsUsed: state.sessionUi.hintsUsed
    },
    message: passed
      ? `Manual pass recorded after ${formatCount(strokeCount)} captured stroke${strokeCount === 1 ? "" : "s"}.`
      : strokeCount
        ? `Manual retry recorded after ${formatCount(strokeCount)} captured stroke${strokeCount === 1 ? "" : "s"}. Pass stays locked until you override or complete a passing attempt.`
        : "Manual retry recorded without captured strokes. Pass stays locked until you record a passing attempt."
  };
  state.sessionUi.handwriting.phase = "overridden";
  renderStudySession();
}

function revealAnswer() {
  if (!state.sessionUi) {
    return;
  }
  state.sessionUi.revealed = true;
  renderStudySession();
}

function toggleHint() {
  if (!state.sessionUi || !state.currentSession) {
    return;
  }
  if (!state.sessionUi.revealed) {
    return;
  }
  state.sessionUi.hintVisible = !state.sessionUi.hintVisible;
  if (state.sessionUi.hintVisible) {
    state.sessionUi.hintsUsed += 1;
    resetHandwritingResult();
  }
  renderStudySession();
}

async function saveSettings() {
  const parsed = buildSettingsPayload();
  syncSettingsPreviewFromForm();
  showStatus("Saving settings…", "loading");
  const payload = await requestJson("/api/settings", { method: "PUT", body: parsed });
  state.settings = Object.keys(payload).length ? payload : parsed;
  state.settingsText = JSON.stringify(state.settings, null, 2) || "{}";
  state.settingsLoaded = true;
  state.settingsDirty = false;
  populateSettingsForm(state.settings);
  showStatus("Settings saved.", "success");
}

async function syncCollection() {
  showStatus("Syncing with AnkiConnect…", "loading");
  const payload = await requestJson("/api/sync/ankiconnect", { method: "POST", body: {} });
  const message = extractMessage(payload, "Manual sync completed.");
  await Promise.allSettled([loadDashboard(true), loadStudyOverview(true), loadHealth(true)]);
  renderCurrentRoute();
  showStatus(message, "success");
}

async function refreshSeeds() {
  showStatus("Refreshing study seeds…", "loading");
  const payload = await requestJson("/api/study/seeds/refresh", { method: "POST", body: {} });
  const message = extractMessage(payload, "Study seeds refreshed.");
  state.studyOverview = null;
  await Promise.allSettled([loadStudyOverview(true), loadDashboard(true)]);
  renderCurrentRoute();
  showStatus(message, "success");
}

async function launchDashboardSession(mode) {
  navigate("#/study");
  await startSession(mode);
}

async function startSession(mode) {
  state.currentSessionMode = mode;
  showStatus(`Starting ${mode} study session…`, "loading");
  const payload = normalizeSessionEnvelope(
    await requestJson("/api/study/sessions", { method: "POST", body: { mode } })
  );
  if (!payload.available || !payload.session) {
    state.currentSession = null;
    state.sessionUi = null;
    await loadStudyOverview(true);
    renderStudyOverview();
    renderStudySession();
    showStatus(payload.message || "No study session is available right now.", "success");
    return;
  }
  state.currentSession = payload.session;
  state.sessionUi = createSessionUi(payload.session);
  renderStudyOverview();
  renderStudySession();
  clearStatus();
}

async function submitRating(rating) {
  if (!state.currentSession || !state.sessionUi || !canSubmitRating()) {
    return;
  }
  const session = state.currentSession;
  showStatus(`Submitting ${formatRatingLabel(rating).toLowerCase()} for ${session.kanji}…`, "loading");
  const payload = await requestJson("/api/study/reviews", {
    method: "POST",
    body: {
      kanji: session.kanji,
      reviewToken: session.reviewToken,
      promptType: session.promptType,
      rating,
      hintsUsed: state.sessionUi.hintsUsed,
      handwritingResult: buildHandwritingResult()
    }
  });

  if (payload.overview) {
    state.studyOverview = normalizeStudyOverview(asObject(payload.overview));
  } else {
    state.studyOverview = null;
    await loadStudyOverview(true);
  }

  state.kanjiCache.delete(session.kanji);
  const duplicate = Boolean(payload.duplicate);
  if (duplicate) {
    showStatus(`That review was already recorded for ${session.kanji}.`, "error");
  } else {
    showStatus(`Recorded ${formatRatingLabel(rating).toLowerCase()} for ${session.kanji}.`, "success");
  }
  await startSession(state.currentSessionMode);
}

function renderCurrentRoute() {
  if (state.route.name === "dashboard") {
    renderDashboard();
    return;
  }
  if (state.route.name === "detail") {
    const detail = state.kanjiCache.get(state.route.kanji);
    if (detail) {
      renderDetail(detail);
    }
    return;
  }
  renderStudyOverview();
  renderStudySession();
}

async function syncRoute(force = false) {
  state.route = parseRoute();
  state.pendingRouteKey = state.route.key;
  setActiveNav();

  if (state.route.name === "dashboard") {
    renderDashboardLoading();
    showStatus("Loading dashboard overview…", "loading");
    const results = await Promise.allSettled([
      loadHealth(force),
      loadSettings(force),
      loadDashboard(force),
      loadStudyOverview(force)
    ]);
    if (state.pendingRouteKey !== state.route.key) {
      return;
    }
    renderDashboard();
    handleLoadResults(results);
    return;
  }

  if (state.route.name === "detail") {
    setView("detail");
    showStatus(`Loading ${state.route.kanji}…`, "loading");
    const results = await Promise.allSettled([
      loadHealth(force),
      loadDetail(state.route.kanji, force)
    ]);
    if (state.pendingRouteKey !== state.route.key) {
      return;
    }
    const detail = state.kanjiCache.get(state.route.kanji);
    if (detail) {
      renderDetail(detail);
    }
    handleLoadResults(results);
    return;
  }

  renderStudyLoading();
  showStatus("Loading study overview…", "loading");
  const results = await Promise.allSettled([
    loadHealth(force),
    loadStudyOverview(force)
  ]);
  if (state.pendingRouteKey !== state.route.key) {
    return;
  }
  renderStudyOverview();
  renderStudySession();
  handleLoadResults(results);
}

function handleLoadResults(results) {
  const errors = results
    .filter((result) => result.status === "rejected")
    .map((result) => result.reason && result.reason.message)
    .filter(Boolean);
  if (errors.length) {
    showStatus(errors.join(" "), "error");
  } else {
    clearStatus();
  }
}

function onDashboardClick(event) {
  const button = event.target.closest("[data-kanji-link]");
  if (!button) {
    return;
  }
  navigate(`#/kanji/${encodeURIComponent(button.dataset.kanjiLink)}`);
}

function bindEvents() {
  els.navDashboard.addEventListener("click", () => navigate("#/"));
  els.navStudy.addEventListener("click", () => navigate("#/study"));
  els.refreshDashboard.addEventListener("click", () => syncRoute(true));
  els.syncCollection.addEventListener("click", () => syncCollection().catch((error) => showStatus(error.message, "error")));
  els.refreshSeeds.addEventListener("click", () => refreshSeeds().catch((error) => showStatus(error.message, "error")));
  els.reloadSettings.addEventListener("click", () => {
    state.settingsDirty = false;
    loadSettings(true)
      .then(() => renderDashboard())
      .catch((error) => showStatus(error.message, "error"));
  });
  els.saveSettings.addEventListener("click", () => saveSettings().catch((error) => showStatus(error.message, "error")));
  [
    els.settingAnkiConnectUrl,
    els.settingNoteModels,
    els.settingExpressionField,
    els.settingReadingField,
    els.settingMeaningField,
    els.settingMatureDays,
    els.settingSupportThreshold,
    els.settingPollingEnabled,
    els.settingPollingIntervalSeconds,
    els.settingJitenCacheTtlHours,
    els.settingJitenTimeoutSeconds
  ].forEach((field) => {
    const onEdit = () => {
      state.settingsDirty = true;
      syncSettingsPreviewFromForm();
    };
    field.addEventListener("input", onEdit);
    field.addEventListener("change", onEdit);
  });
  els.dashboardTbody.addEventListener("click", onDashboardClick);
  els.detailBack.addEventListener("click", () => navigate("#/"));
  els.studyRefresh.addEventListener("click", () => syncRoute(true));
  els.studyRefreshSeeds.addEventListener("click", () => refreshSeeds().catch((error) => showStatus(error.message, "error")));
  els.studyPreview.addEventListener("click", onDashboardClick);
  els.dashboardStudyPreview.addEventListener("click", onDashboardClick);
  els.dashboardOpenStudy.addEventListener("click", () => navigate("#/study"));
  els.dashboardStartMixed.addEventListener("click", () => {
    launchDashboardSession("mixed").catch((error) => showStatus(error.message, "error"));
  });
  els.dashboardStartReview.addEventListener("click", () => {
    launchDashboardSession("review").catch((error) => showStatus(error.message, "error"));
  });
  els.dashboardStartNew.addEventListener("click", () => {
    launchDashboardSession("new").catch((error) => showStatus(error.message, "error"));
  });
  els.startMixed.addEventListener("click", () => startSession("mixed").catch((error) => showStatus(error.message, "error")));
  els.startReview.addEventListener("click", () => startSession("review").catch((error) => showStatus(error.message, "error")));
  els.startNew.addEventListener("click", () => startSession("new").catch((error) => showStatus(error.message, "error")));
  els.sessionReveal.addEventListener("click", revealAnswer);
  els.sessionEnd.addEventListener("click", () => {
    state.currentSession = null;
    state.sessionUi = null;
    renderStudySession();
  });
  els.canvasUndo.addEventListener("click", undoLastStroke);
  els.canvasClear.addEventListener("click", clearCanvas);
  els.sessionHint.addEventListener("click", toggleHint);
  els.handwritingEvaluate.addEventListener("click", evaluateHandwriting);
  els.handwritingPass.addEventListener("click", () => markHandwriting(true));
  els.handwritingFail.addEventListener("click", () => markHandwriting(false));
  els.ratingButtons.forEach((button) => {
    button.addEventListener("click", () => {
      submitRating(button.dataset.rating).catch((error) => showStatus(error.message, "error"));
    });
  });
  els.handwritingCanvas.addEventListener("pointerdown", beginStroke);
  els.handwritingCanvas.addEventListener("pointermove", moveStroke);
  els.handwritingCanvas.addEventListener("pointerup", endStroke);
  els.handwritingCanvas.addEventListener("pointercancel", endStroke);
  window.addEventListener("hashchange", () => syncRoute(false));
  window.addEventListener("resize", () => {
    if (!els.handwritingPanel.classList.contains("is-hidden")) {
      resizeCanvas();
      renderCanvas();
    }
  });
}

function installTestHooks() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("testHooks") !== "1") {
    return;
  }
  window.__kanjiCompanionTestHooks = {
    setHandwritingStrokes(strokes) {
      if (!state.sessionUi) {
        return false;
      }
      state.sessionUi.handwriting.strokes = asArray(strokes)
        .map((stroke) => asArray(stroke).map((point) => ({
          x: clamp(asNumber(asObject(point).x), 0, 1),
          y: clamp(asNumber(asObject(point).y), 0, 1)
        })))
        .filter((stroke) => stroke.length);
      state.sessionUi.handwriting.currentStroke = [];
      state.sessionUi.handwriting.result = null;
      state.sessionUi.handwriting.phase = state.sessionUi.handwriting.strokes.length
        ? "drawing"
        : "prewrite";
      renderStudySession();
      return true;
    },
    getHandwritingSnapshot() {
      if (!state.sessionUi || !state.currentSession) {
        return null;
      }
      return {
        phase: state.sessionUi.handwriting.phase,
        strokeCount: state.sessionUi.handwriting.strokes.length,
        result: state.sessionUi.handwriting.result,
        allowedRatings: handwritingAllowedRatings(state.currentSession, state.sessionUi)
      };
    }
  };
}

function init() {
  installTestHooks();
  bindEvents();
  if (!window.location.hash) {
    window.location.hash = "#/";
  }
  syncRoute(true).catch((error) => showStatus(error.message, "error"));
}

init();
