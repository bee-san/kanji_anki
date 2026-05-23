package dev.bee.kanjianki.core.study

class StrokeDiagnosisFormatter private constructor() {
    companion object {
        @JvmStatic
        fun text(analysis: WritingAnalysis?): String {
            if (!canShow(analysis)) {
                return ""
            }
            val lines = ArrayList<String>()
            for (entry in analysis!!.strokeOrder.diagnosis.entries) {
                val line = line(entry)
                if (line.isNotEmpty()) {
                    lines.add(line)
                }
            }
            return lines.joinToString("\n")
        }

        @JvmStatic
        fun canShow(analysis: WritingAnalysis?): Boolean {
            if (analysis == null ||
                analysis.strokeOrder == null ||
                analysis.strokeOrder.missingGuide ||
                analysis.strokeOrder.diagnosis.isEmpty()
            ) {
                return false
            }
            return when (analysis.status) {
                WritingAnalysis.Status.NO_INK,
                WritingAnalysis.Status.MODEL_UNAVAILABLE,
                WritingAnalysis.Status.NO_STROKE_DATA,
                WritingAnalysis.Status.RECOGNITION_ERROR -> false

                else -> true
            }
        }

        @JvmStatic
        fun line(entry: StrokeDiagnosis.Entry?): String {
            if (entry?.label == null) {
                return ""
            }
            return when (entry.label) {
                StrokeDiagnosis.Label.WRONG_ORDER -> strokeLine(entry, "likely wrong order")
                StrokeDiagnosis.Label.WRONG_DIRECTION -> strokeLine(entry, "likely wrong direction")
                StrokeDiagnosis.Label.MISSING_STROKE -> strokeLine(entry, "may be missing")
                StrokeDiagnosis.Label.ROUGH_SHAPE -> strokeLine(entry, "shape looks rough")
                StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY -> "Recognized, but the stroke path was messy"
            }
        }

        @JvmStatic
        fun strokeLine(entry: StrokeDiagnosis.Entry?, label: String): String {
            if (entry == null) {
                return ""
            }
            return "Stroke " + entry.strokeNumber + ": " + label
        }
    }
}
