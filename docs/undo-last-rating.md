# Undo last study rating seam

Kani now records both the before and after scheduler snapshots for each persisted study review in `review_log`:

- `scheduler_state_before_json`
- `scheduler_state_after_json`

The production undo boundary lives in `LocalStoreStudy.undoLastAppliedReview`.
It deletes the review token and review timeline event, restores the previous
`StudyItem` scheduler snapshot at a new monotonic `scheduler_revision`, and
marks stats dirty in one transaction. The row update is compare-and-swap on the
saved after-review revision, so another review, sync mutation, or prior undo
makes the request stale instead of overwriting newer state. Elapsed task-time
and objective-choice observations are deliberately preserved.

The UI may advance, show success, or refresh the session only after that
transaction reports success. As with review commits, cache invalidation is an
after-commit effect.
