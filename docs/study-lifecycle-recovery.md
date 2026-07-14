# Study lifecycle recovery

Kani restores an interrupted canonical flashcard or eligible similar-kanji choice only when the
local database can prove that it is still the same scheduler task and presentation. Recovery is a
convenience layer over persisted scheduler state; it is never a second queue and it never makes a
review decision.

## Durable envelope

The app keeps one versioned recovery envelope in app-private `SharedPreferences`. Android cloud and
device-transfer backups exclude all shared preferences through `backup_rules.xml` and
`data_extraction_rules.xml`.

The envelope has two states:

- **Active:** the ungraded card identity, its active scheduler token, source sync epoch, prompt
  source, typed draft, whether a plain flashcard was revealed, and, for an eligible similar-kanji
  choice, a digest of its canonical choice set.
- **Pending answer:** the feedback needed to keep an accepted answer visible until Continue. During
  submission it also retains the previous active state as a fallback if the database transaction is
  rejected.

Identity changes use synchronous `commit()` under one process-wide lock. Keystrokes update the
in-process preference map with `apply()` to avoid synchronous disk I/O while typing; `onPause`, answer
submission, and explicit exit force the latest map to disk. Exact claims and conditional clears
compare the whole encoded value; same-family pending updates additionally require an unchanged
token, card identity, answer evidence, and monotonic phase. Each mounted active card claims a new
write epoch. A superseded Activity, canceled route load, or late text callback therefore cannot
overwrite or delete a newer card. Moving from active to pending is one whole-value write, not a clear
followed by a save.

The active envelope deliberately stores only a SHA-256 digest of the answer signature. A restorable
similar-kanji choice additionally stores a domain-separated SHA-256 digest of the canonical choice
signature. It does not store the option list, display order, correct answer, canonical expression,
reading, meaning, examples, or mnemonic outside the database. The learner's own typed draft is
persisted because it is the state being restored. Once an answer is submitted, the pending envelope
stores the selected answer and exact family signature needed to reproduce and validate the answer
screen.

Only choices rebuilt from the current persisted, due similar-choice source qualify for active
recovery. Its stored signature must canonicalize to the same choice set produced for the scheduler
session; ad-hoc fallback choices never receive a recovery digest. Display order is rebuilt by
normalizing and sorting the set, then applying a deterministic shuffle bound to the persisted
session token. A process restart therefore keeps the same order without making that order durable
or depending on incidental database query order.

## Exact validation

Before mounting an active envelope, Kani requires all of the following:

- exactly one current study item matches the kanji and answer-signature digest;
- the dashboard row has the same answer signature;
- active token, scheduler revision, routing version, and recomputed task type all match;
- the review token has not been consumed;
- the item is neither retired nor suppressed; and
- the latest successful sync timestamp is unchanged.

For a similar-kanji choice, preparation must also resolve the current persisted due-choice source
again and reproduce the envelope's canonical-set digest. The active envelope requires an empty
typed draft and unrevealed state. The same content check runs both for a directly active envelope
and for the active fallback retained while an answer is in the submitting phase. An expired or
changed due source, a fallback-generated set, malformed digest, missing target, or set mismatch
invalidates the inspected envelope rather than mounting a different choice card.

Any mismatch clears only the envelope that was inspected, using a conditional clear. A concurrent
replacement survives. This deliberately invalidates an interrupted card after any successful sync,
even if that sync happened to leave the row unchanged; conservatism is safer than mounting stale
private UI state onto newly imported scheduler inputs.

An answered canonical card is bound to its raw answer signature and pre-review scheduler revision.
The current item must have consumed the token and be exactly one revision newer. Version-1 pending
answers, which predate family binding, are accepted only when the kanji has exactly one study-item
family. Targeted compatibility repairs keep their existing pending-answer path because they have no
canonical `study_items` row.

## Route and exit behavior

An ordinary launch resumes a valid active or pending Study envelope. Activity recreation also saves
a one-shot Study-route marker so the stale original launch intent cannot send a rotated Study screen
back to Browse, Update, or Home. Explicit Study intents and shortcuts still take their normal higher
priority.

Leaving for Home, Stats, Settings, Browse, Games, or Update disables ordinary auto-resume before the
destination renders. The envelope is retained in a dormant state so an explicit return to Study can
recover it, but late review callbacks cannot reactivate ordinary resume. Temporary answer-panel
navigation into kanji details is not treated as leaving Study. Screenshot and benchmark routes may
observe the app but never mutate production recovery state.

Similar-choice actions capture the rendered session token and recovery write epoch. Choice,
Continue, and nested difference-screen callbacks validate that identity against the still-mounted
route; grading additionally requires the unanswered phase. A callback retained by a destroyed
Activity therefore cannot grade or replace a newer session.

## Current boundary

This slice restores:

- the exact active canonical flashcard and Study route;
- typed meaning/reading drafts;
- the ungraded reveal state of plain recognition, font, word-reading, and sentence cards; and
- an ungraded similar-kanji choice whose current persisted due source reproduces its canonical set,
  with deterministic token-bound display order; and
- accepted/pending answer feedback that existed before this work.

Other ungraded choice-card presentations, full run history and progress breakdown, task-timer
position, open dialogs, hint state, and handwriting strokes are intentionally not encoded yet.
Accepted choice selection is already part of pending-answer feedback. A recovered active card
reconciles its target against the currently selectable remaining work, so the fresh tracker cannot
end a multi-card run at one, but prior completed-position detail is not reconstructed. Active
writing and the remaining ungraded choice destinations fail closed instead of being partially
restored. Those states need their own deterministic content identity and bounded payload contracts
before they can safely join this envelope.

The regression surface is JVM/Robolectric only: envelope CAS/tombstone behavior, malformed payloads,
exact scheduler invalidation, startup precedence, process-style Activity reconstruction, typed draft
restoration, plain reveal restoration, deterministic similar-choice ordering and digest validation,
active and submitting-fallback recovery, stale callback rejection, and late completion after
explicit exit. No device or emulator is required for this UI-lifecycle slice.
