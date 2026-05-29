# Undo last study rating seam

Kani now records both the before and after scheduler snapshots for each persisted study review in `review_log`:

- `scheduler_state_before_json`
- `scheduler_state_after_json`

The app-level undo guard lives in `StudyReviewActions.undoLastAppliedReview`. It only restores the previous `StudyItem` snapshot and deletes the consumed review token when the current item still exactly matches the saved after-review scheduler boundary. That keeps rollback limited to the immediately previous answer and rejects stale undo after another review or scheduler mutation has moved the card forward.

This is intentionally a backend/test seam, not a full UI entry point yet. A safe UI flow can build on it by loading the latest review row, reconstructing the saved snapshots, calling the guard inside one database transaction, then refreshing the current study card.
