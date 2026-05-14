# SRS System Overhaul Plan

## Status: Ready to Execute
**Created**: 2026-05-13  
**Codebase**: `/Users/skerraut/Documents/kanji_anki`  
**Branch**: `main` (create feature branch `srs-overhaul` before starting)

## Prerequisites (DONE)
- [x] OpenCode model config fixed (`~/.config/opencode/oh-my-openagent.json`)
  - Haiku: `anthropic.claude-haiku-4-5-20251001-v1:0`
  - Sonnet: `anthropic.claude-sonnet-4-6`
  - Quick category: upgraded to sonnet
- [x] FSRS-5 algorithm spec retrieved (see below)
- [x] Existing Java FSRS library identified: https://github.com/open-spaced-repetition/rs-fsrs-java
- [x] Full audit completed (5 Oracle agents, all findings documented)

---

## Task List (Execute in Order)

### Phase 1: Quick Bug Fixes (Parallel — use subagents)

#### 1. Fix timezone bug
- **File**: `app/src/main/java/dev/bee/kanjianki/MainActivity.java` + `sync/ManualSyncEngine.java`
- **Bug**: `startOfDay` computed as `now - (now % 86_400_000L)` = UTC midnight, not local
- **Fix**: Replace with `Calendar.getInstance()` approach:
  ```java
  private static long startOfLocalDay(long nowMillis) {
      Calendar cal = Calendar.getInstance();
      cal.setTimeInMillis(nowMillis);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTimeInMillis();
  }
  ```
- **Search for**: `% 86_400_000` or `% DAY` patterns in app module

#### 2. Add stability cap
- **File**: `core/src/main/java/dev/bee/kanjianki/core/BridgeScheduler.java` line ~893
- **Fix**: Add `Math.min(stability, 36500.0)` before interval calculation
- **One-line change**

#### 3. Fix dueCount overcounting
- **File**: `BridgeScheduler.java` line ~788
- **Bug**: `dueCount(items, nowMillis)` doesn't do family dedup or row filtering
- **Fix**: Find callers in app module (likely `ReminderScheduler`), switch to 2-arg version

#### 4. Wrap saveStudyItem in transaction
- **File**: `app/src/main/java/dev/bee/kanjianki/data/LocalStore.java` line ~1173
- **Fix**: Add `beginTransaction()`/`setTransactionSuccessful()`/`endTransaction()` around `upsertStudyItem`

#### 5. Make migration v16 crash-resilient
- **File**: `LocalStore.java` line ~293-311
- **Fix**: Add `IF NOT EXISTS` to CREATE TABLE, wrap in try-catch with recovery

#### 6. Fix hasSimilarKanji stale read
- **File**: `LocalStore.java` — `studyItemForKanji()` method
- **Fix**: Apply same `kanjiWithSimilarNeighbors` query that `studyItems()` uses

---

### Phase 2: FSRS-5 Implementation (Main work)

#### 7. Integrate rs-fsrs-java library
- **Repo**: https://github.com/open-spaced-repetition/rs-fsrs-java
- **Approach**: Add as Gradle dependency or vendor the source
- **Key change**: Replace `BridgeScheduler`'s custom multiplier model with real FSRS-5

#### FSRS-5 Formulas (19 weights: w[0]–w[18])
```
Default weights: [0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 
                  0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 
                  2.2698, 0.2315, 2.9898, 0.51655, 0.6621]

Constants: DECAY = -0.5, FACTOR = 19/81

Initial stability:       S₀(G) = w[G-1]
Initial difficulty:      D₀(G) = w4 - e^(w5 * (G-1)) + 1
Difficulty update:       ΔD(G) = -w6 * (G-3)
                         D' = D + ΔD * (10-D)/9
                         D'' = w7 * D₀(4) + (1-w7) * D'
Retrievability:          R(t,S) = (1 + FACTOR * t/S)^DECAY
Interval:                I(r,S) = (S/FACTOR) * (r^(1/DECAY) - 1)
Stability after recall:  S'_r = S * (e^w8 * (11-D) * S^(-w9) * (e^(w10*(1-R)) - 1) * w15[Hard] * w16[Easy] + 1)
Stability after forget:  S'_f = w11 * D^(-w12) * ((S+1)^w13 - 1) * e^(w14*(1-R))
Same-day review:         S'(S,G) = S * e^(w17 * (G - 3 + w18))
```

#### What to replace in BridgeScheduler.java:
- `reviewInterval()` (line 893-897) → use FSRS interval formula
- `applyReviewPass()` (line 660-662) → use S'_r formula
- `applyReviewAgain()` (line 638-639) → use S'_f formula  
- `graduateToReview()` (line 607-611) → use S₀(G) initial stability
- Difficulty updates (lines 609, 638, 662) → use mean-reversion formula
- `SchedulerParameters` class → replace with `Fsrs5Weights` (19 params + targetRetention)
- `SchedulerTuner` → remove or replace with proper FSRS optimizer

#### Data model changes:
- `Records.SchedulerParameters` needs new fields for w[0]-w[18]
- `LocalStore` settings persistence needs to store 19 weights
- Migration: old multiplier params → map to closest FSRS-5 defaults
- `TaskMemory` stability/difficulty fields remain (same semantics, different math)

---

### Phase 3: Suppression System

#### 8. Implement mature-sibling suppression
- **Spec**: `documentation/srs.md` lines 24-56
- **Data fields exist**: `suppressedByTaskType`, `suppressedAtMillis`, `matureIntervalDays`
- **Code to add**:
  1. In `activeQueueItems()`: filter out items where `suppressedByTaskType != null`
  2. In `applyReviewPass()`: after setting `matureIntervalDays`, check if item is a dominator and suppress lower siblings
  3. In `applyReviewAgain()`: clear suppression on all items dominated by this one
  4. In `alignAnswerSignature()`: clear suppression for affected signature (already partially done)
- **Dominance rules**:
  - `word_reading` dominates `font_meaning` and `kanji_meaning`
  - `font_meaning` dominates `kanji_meaning`
  - Mature = `scheduledIntervalDays >= 21` + at least one successful due review + last rating != Again
- **External orchestration needed**: Suppression is checked at queue-building time, not review time. Need a method like `applySuppression(List<StudyItem>)` called during `seedQueue` or `nextSession`.

---

### Phase 4: Decomposition

#### 9. Decompose BridgeScheduler into 3 classes
- `QueueSeeder` (~300 lines): seedQueue*, reconcile, admit, retire
- `ReviewEngine` (~400 lines): applyReview, FSRS math, learning/relearning transitions
- `LadderStateMachine` (~200 lines): rung promotion/demotion, suppression logic
- **Mechanical refactor** — no logic changes, just extraction

---

### Phase 5: Verification

#### 10. Run full test gate and commit/push
```sh
# Create branch first
git checkout -b srs-overhaul

# Run gate
gradle :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:lintDebug

# Commit
git add -A
git commit -m "feat: FSRS-5 scheduler, suppression system, and bug fixes

- Replace custom multiplier model with real FSRS-5 algorithm (19-weight model)
- Implement mature-sibling suppression (word_reading dominates lower rungs)
- Fix UTC timezone bug in startOfDay calculation
- Add stability cap (36500 days) to prevent overflow
- Fix dueCount overcounting (use family-dedup version)
- Wrap saveStudyItem in transaction for crash safety
- Make migration v16 crash-resilient with IF NOT EXISTS
- Fix hasSimilarKanji stale read in studyItemForKanji
- Decompose BridgeScheduler into QueueSeeder + ReviewEngine + LadderStateMachine"

git push -u origin srs-overhaul
```

---

## Key Files
| File | Role | Lines |
|------|------|-------|
| `core/src/main/java/dev/bee/kanjianki/core/BridgeScheduler.java` | Main scheduler | 1174 |
| `core/src/main/java/dev/bee/kanjianki/core/Records.java` | Data model | 2161 |
| `core/src/main/java/dev/bee/kanjianki/core/SchedulerTuner.java` | Parameter tuning | 46 |
| `core/src/main/java/dev/bee/kanjianki/core/AdaptiveLoadPlanner.java` | Session sizing | ~500 |
| `app/src/main/java/dev/bee/kanjianki/data/LocalStore.java` | Persistence | ~3500 |
| `app/src/main/java/dev/bee/kanjianki/MainActivity.java` | UI + startOfDay | ~5000 |
| `core/src/test/java/dev/bee/kanjianki/core/BridgeSchedulerTest.java` | Core tests | ~1500 |
| `core/src/test/java/dev/bee/kanjianki/core/LadderSchedulerTest.java` | Ladder tests | ~800 |
| `documentation/srs.md` | Suppression spec | 68 |

## Execution Strategy
- **Use subagents** for Phase 1 (6 independent bug fixes in parallel)
- **Use Oracle** for FSRS-5 review after implementation
- **Self-execute** the FSRS-5 core integration (most complex/interdependent)
- **Use subagent** for decomposition (mechanical refactor)

## Resume Command
```
Look at .sisyphus/plans/srs-overhaul.md and execute it. Start with Phase 1 bug fixes in parallel using subagents, then FSRS-5, then suppression, then decomposition. Commit and push when done.
```
