"""
Generate FSRS-7 reference vectors by driving srs-benchmark's own models/fsrs_v7.py.

The point of running upstream's real class rather than a re-derivation is that the
fixture is then an oracle rather than a restatement of the same assumptions: if the
Kotlin port and this file both encoded the same misreading, the fixture could not
notice. It has already earned that twice — FSRS-7 feeds the *previous* difficulty into
its stability update where this package's FSRS-6 engine uses the next one, and
upstream's Newton interval solver floors at one second in a way that suits the training
penalty it serves and not a scheduler.

Upstream computes in float32 because it is training a model. The Kotlin engine is
float64, so vectors are generated in float64 (by replacing the model's parameter tensor
with a double one) and both widths are compared here to size the Kotlin test's
tolerance from measurement rather than by picking a round number.

Not run in CI: it needs PyTorch and an srs-benchmark checkout, which is far too heavy
for a per-push job, and a fixture regenerated automatically could absorb an upstream
change into a green build. Regenerate deliberately when the pinned commit moves.

Usage:

    git clone https://github.com/open-spaced-repetition/srs-benchmark
    cd srs-benchmark
    git checkout 70cc4387f573ff20b13ac9c106333a335c8a4cb8   # the pinned commit
    python -m venv .venv && . .venv/bin/activate            # needs Python >= 3.12
    pip install torch numpy pandas scipy tqdm fsrs-optimizer
    python path/to/generate-fsrs7-reference-cases.py

Then copy the emitted JSON over testdata/fsrs7-reference-cases.json, update
Fsrs7AlgorithmInfo's pinned commit and blob, and run ./gradlew check. Any resulting
fixture diff is the review artifact: it shows exactly which schedules the upstream
change would move.
"""

import json
import math
import os
import sys

# Run from inside an srs-benchmark checkout, so its modules import.
sys.path.insert(0, os.getcwd())

import torch  # noqa: E402
from config import load_config  # noqa: E402
from models.fsrs_v7 import FSRS7  # noqa: E402

# FSRS-7 is documented as always used with --short --secs, which sets s_min to 1e-4.
CONFIG = load_config(["--algo", "FSRS-7", "--short", "--secs"])

INIT_W = list(FSRS7.init_w)
S_MIN = CONFIG.s_min
S_MAX = 36500.0


def build(dtype):
    model = FSRS7(CONFIG)
    model.w = torch.nn.Parameter(torch.tensor(INIT_W, dtype=dtype))
    return model


def t(value, dtype):
    return torch.tensor(value, dtype=dtype)


def forgetting_curve(model, elapsed, stability, dtype):
    w = model.w
    return float(
        model.forgetting_curve(
            t([elapsed], dtype),
            t([stability], dtype),
            -w[-8],
            -w[-7],
            w[-6],
            w[-5],
            w[-4],
            w[-3],
            w[-2],
            w[-1],
        )[0]
    )


def init_state(model, rating, dtype):
    """First review: stability is w[rating-1], difficulty is init_d clamped."""
    stability = float(model.w[rating - 1])
    difficulty = float(model.init_d(t([float(rating)], dtype)).clamp(1, 10)[0])
    return stability, difficulty


def step(model, stability, difficulty, rating, elapsed, dtype):
    """One non-initial review through upstream's own step()."""
    state = torch.stack([t([stability], dtype), t([difficulty], dtype)], dim=1)
    x = torch.stack([t([elapsed], dtype), t([float(rating)], dtype)], dim=1)
    out = model.step(x, state)
    return float(out[0, 0]), float(out[0, 1])


def stability_parts(model, stability, difficulty, rating, elapsed, dtype):
    """The long-term and short-term stabilities and the blend coefficient."""
    state = torch.stack([t([stability], dtype), t([difficulty], dtype)], dim=1)
    r = forgetting_curve(model, elapsed, stability, dtype)
    s_long, s_short = model.stability_after_review(
        state, t([r], dtype), t([float(rating)], dtype)
    )
    coefficient = float(model.transition_function(t([elapsed], dtype))[0])
    return float(s_long[0]), float(s_short[0]), coefficient, r


def interval_for(model, stability, desired_retention, dtype):
    """
    Invert the forgetting curve for the interval at a target retention.

    FSRS-7's curve has no closed-form inverse, so this brackets the root and
    bisects it. Bisection rather than Newton because this is a fixture generator
    where robustness matters more than iteration count, and because a bracketed
    method cannot silently converge to the wrong branch.
    """
    lo, hi = 1e-9, S_MAX

    def r_at(days):
        return forgetting_curve(model, days, stability, dtype)

    if r_at(hi) > desired_retention:
        return hi
    for _ in range(300):
        mid = math.sqrt(lo * hi)
        if r_at(mid) > desired_retention:
            lo = mid
        else:
            hi = mid
    return math.sqrt(lo * hi)


def build_with(w, dtype):
    model = FSRS7(CONFIG)
    model.w = torch.nn.Parameter(torch.tensor(w, dtype=dtype))
    return model


# A legal-but-extreme parameter vector, used to reach the two stability guards that
# the default parameters never trigger.
#
# Upstream's stability_after_review caps the failure branch at the current stability
# and takes max(failure, success) on a pass. Under FSRS7's default weights neither
# comparison ever binds, so a port could delete both and every default-parameter
# vector would still agree. A learner's own fitted parameters are not the defaults,
# so "unreachable by default" is not "unreachable".
#
# Every value below is inside FSRS7ParameterClipper's bounds: the long-term failure
# multiplier w[10] at its ceiling of 1.5, the difficulty exponent w[11] at its floor
# of 0.001, the stability exponent w[12] at its ceiling of 1.0, and the
# retrievability multiplier w[13] at its ceiling of 3.5.
EXTREME_W = list(INIT_W)
EXTREME_W[10] = 1.5
EXTREME_W[11] = 0.001
EXTREME_W[12] = 1.0
EXTREME_W[13] = 3.5


def generate(dtype):
    model = build(dtype)
    cases = []

    # ── Forgetting curve ──────────────────────────────────────────────────────
    # Sub-day, day-scale, and long elapsed times against small and large
    # stabilities, because the two power laws are weighted by s**-swp1 and
    # s**swp2 and so trade dominance as stability grows. A curve tested only at
    # one stability scale would miss a swapped weight.
    for stability in (0.01, 0.5, 2.3065, 10.0, 100.0, 1000.0):
        for elapsed in (0.0, 0.00694, 0.0833, 0.5, 1.0, 3.0, 10.0, 100.0, 1000.0):
            cases.append(
                {
                    "kind": "forgettingCurve",
                    "name": f"curve_s{stability}_t{elapsed}",
                    "stability": stability,
                    "elapsedDays": elapsed,
                    "expectedRetrievability": forgetting_curve(
                        model, elapsed, stability, dtype
                    ),
                }
            )

    # ── Initial state ─────────────────────────────────────────────────────────
    for rating in (1, 2, 3, 4):
        stability, difficulty = init_state(model, rating, dtype)
        cases.append(
            {
                "kind": "initialState",
                "name": f"init_rating{rating}",
                "rating": rating,
                "expectedStability": stability,
                "expectedDifficulty": difficulty,
            }
        )

    # ── Difficulty update ─────────────────────────────────────────────────────
    for difficulty in (1.0, 2.5, 5.0, 7.3, 10.0):
        for rating in (1, 2, 3, 4):
            state = torch.stack([t([1.0], dtype), t([difficulty], dtype)], dim=1)
            expected = float(
                model.next_d(state, t([float(rating)], dtype)).clamp(1, 10)[0]
            )
            cases.append(
                {
                    "kind": "nextDifficulty",
                    "name": f"difficulty_d{difficulty}_r{rating}",
                    "difficulty": difficulty,
                    "rating": rating,
                    "expectedDifficulty": expected,
                }
            )

    # ── Stability components and the transition blend ─────────────────────────
    # Exposed separately from step() so a failure localises: the long-term and
    # short-term branches share nine parameters at offsets 7 and 16, which is
    # exactly the kind of duplication an off-by-one index error hides in.
    for stability, difficulty in ((0.5, 5.0), (5.0, 6.0), (50.0, 3.2)):
        for rating in (1, 2, 3, 4):
            for elapsed in (0.0, 0.00694, 0.5, 1.0, 7.0):
                s_long, s_short, coefficient, r = stability_parts(
                    model, stability, difficulty, rating, elapsed, dtype
                )
                cases.append(
                    {
                        "kind": "stabilityComponents",
                        "name": f"parts_s{stability}_d{difficulty}_r{rating}_t{elapsed}",
                        "stability": stability,
                        "difficulty": difficulty,
                        "rating": rating,
                        "elapsedDays": elapsed,
                        "expectedRetrievability": r,
                        "expectedLongTermStability": s_long,
                        "expectedShortTermStability": s_short,
                        "expectedTransitionCoefficient": coefficient,
                    }
                )

    # ── Full step ─────────────────────────────────────────────────────────────
    for stability, difficulty in ((0.01, 1.0), (0.5, 5.0), (5.0, 6.0), (50.0, 3.2), (1000.0, 9.5)):
        for rating in (1, 2, 3, 4):
            for elapsed in (0.0, 0.00694, 0.0833, 0.5, 1.0, 7.0, 100.0):
                next_s, next_d = step(model, stability, difficulty, rating, elapsed, dtype)
                cases.append(
                    {
                        "kind": "nextState",
                        "name": f"step_s{stability}_d{difficulty}_r{rating}_t{elapsed}",
                        "stability": stability,
                        "difficulty": difficulty,
                        "rating": rating,
                        "elapsedDays": elapsed,
                        "expectedStability": next_s,
                        "expectedDifficulty": next_d,
                    }
                )

    # ── Clamp boundaries ──────────────────────────────────────────────────────
    # Upstream clamps stability into [s_min, 36500] and difficulty into [1, 10].
    # The sweep above never actually reaches either stability bound, so without
    # these a port could omit the clamps entirely and still pass. Chosen by
    # searching for inputs that do reach them.
    for stability, difficulty, rating, elapsed, why in (
        (0.001, 5.0, 1, 0.0, "same-day lapse at tiny stability floors at s_min"),
        (0.0001, 5.0, 1, 100.0, "already at s_min stays there"),
        (36500.0, 1.0, 4, 1.0, "easy review at max stability caps at 36500"),
        (10000.0, 1.0, 4, 1.0, "large stability stays below the cap"),
        (1.0, 1.0, 1, 10.0, "difficulty floor: a lapse from d=1 must not go below 1"),
        (1.0, 10.0, 4, 10.0, "difficulty ceiling: easy from d=10 must not exceed 10"),
    ):
        next_s, next_d = step(model, stability, difficulty, rating, elapsed, dtype)
        cases.append(
            {
                "kind": "nextState",
                "name": f"clamp_s{stability}_d{difficulty}_r{rating}_t{elapsed}",
                "note": why,
                "stability": stability,
                "difficulty": difficulty,
                "rating": rating,
                "elapsedDays": elapsed,
                "expectedStability": next_s,
                "expectedDifficulty": next_d,
            }
        )

    # ── Interval inversion ────────────────────────────────────────────────────
    # Fractional intervals are the visible behaviour change from FSRS-6, so the
    # sub-day stabilities here are the cases that would silently round to 1 day
    # under the old integer contract.
    for stability in (0.01, 0.1, 1.0, 2.3065, 10.0, 100.0, 3650.0):
        for desired_retention in (0.7, 0.8, 0.9, 0.95, 0.99):
            cases.append(
                {
                    "kind": "interval",
                    "name": f"interval_s{stability}_dr{desired_retention}",
                    "stability": stability,
                    "desiredRetention": desired_retention,
                    "expectedIntervalDays": interval_for(
                        model, stability, desired_retention, dtype
                    ),
                }
            )

    # ── The stability guards, under legal non-default parameters ──────────────
    extreme = build_with(EXTREME_W, dtype)
    for stability, difficulty in ((0.01, 1.0), (1.0, 1.0), (5.0, 5.0), (100.0, 10.0)):
        for rating in (1, 2, 3, 4):
            for elapsed in (0.0, 0.5, 1.0, 30.0):
                next_s, next_d = step(extreme, stability, difficulty, rating, elapsed, dtype)
                cases.append(
                    {
                        "kind": "nextStateWithParameters",
                        "name": f"guard_s{stability}_d{difficulty}_r{rating}_t{elapsed}",
                        "parameters": EXTREME_W,
                        "stability": stability,
                        "difficulty": difficulty,
                        "rating": rating,
                        "elapsedDays": elapsed,
                        "expectedStability": next_s,
                        "expectedDifficulty": next_d,
                    }
                )

    # ── A multi-review sequence ───────────────────────────────────────────────
    # An end-to-end fold, because per-call vectors cannot catch a state that is
    # threaded wrongly between reviews.
    sequence = []
    stability, difficulty = init_state(model, 3, dtype)
    sequence.append({"rating": 3, "elapsedDays": 0.0, "stability": stability, "difficulty": difficulty})
    for rating, elapsed in ((3, 0.00694), (3, 1.0), (4, 3.0), (1, 8.0), (2, 0.0833), (3, 2.0), (3, 15.0)):
        stability, difficulty = step(model, stability, difficulty, rating, elapsed, dtype)
        sequence.append(
            {
                "rating": rating,
                "elapsedDays": elapsed,
                "stability": stability,
                "difficulty": difficulty,
            }
        )
    cases.append({"kind": "sequence", "name": "sequence_good_start", "steps": sequence})

    return cases


def main():
    f64 = generate(torch.float64)
    f32 = generate(torch.float32)

    # Size the tolerance from the observed float32-vs-float64 gap rather than
    # picking a round number and hoping. Reported, not silently applied.
    worst = 0.0
    worst_name = ""
    for a, b in zip(f64, f32):
        assert a["name"] == b["name"]
        for key, value in a.items():
            if not isinstance(value, float) or key == "name":
                continue
            other = b[key]
            scale = max(abs(value), abs(other), 1e-12)
            relative = abs(value - other) / scale
            if relative > worst:
                worst, worst_name = relative, f"{a['name']}.{key}"

    print(f"cases: {len(f64)}")
    print(f"worst float32-vs-float64 relative gap: {worst:.3e} at {worst_name}")

    payload = {
        "algorithm": "FSRS-7",
        "source": "open-spaced-repetition/srs-benchmark models/fsrs_v7.py",
        "parameterCount": len(INIT_W),
        "parameters": INIT_W,
        "stabilityMin": S_MIN,
        "stabilityMax": S_MAX,
        "cases": f64,
    }
    output_path = os.path.join(os.getcwd(), "fsrs7-reference-cases.json")
    with open(output_path, "w") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print(f"wrote {output_path}")


if __name__ == "__main__":
    main()
