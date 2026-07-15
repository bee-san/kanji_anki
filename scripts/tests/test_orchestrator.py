from __future__ import annotations

import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from typing import Any, cast

from scripts.ralph_loop import orchestrator


class OrchestratorTestCase(unittest.TestCase):
    def _base_args(self, latency_measurements: Path) -> Namespace:
        return Namespace(
            file_bucket="all",
            max_files=1,
            critic_profile="design",
            button_profile="uitester",
            critic_cmd="",
            button_cmd="",
            agent_cmd="",
            reviewer_model="gpt5",
            reviewer_cmd="",
            pr_branch="",
            push_pr_branch=False,
            require_remote_green=False,
            latency_measurements=latency_measurements,
        )

    def _inventory_stub(self) -> dict[str, object]:
        return {
            "schema": "button-latency-inventory-v1",
            "source_timings": "relative",
            "source_manifest": "inline",
            "source_button_contract": "inline",
            "target_budget_ms": 1000,
            "measurement_status": "pending_real_device_timings",
            "measurement_notes": ["noop"],
            "summary": {
                "row_count": 0,
                "measured_rows": 0,
                "high_risk_rows": 0,
                "missing_click_coverage_rows": 0,
                "pending_timing_rows": 0,
            },
            "rows": [],
        }

    def _run_audit_only_with(self, *, timing_exists: bool) -> None:
        capture: dict[str, Any] = {}

        def fake_build_manifest(_: Path) -> dict[str, object]:
            return {
                "schema": "ui-manifest-v1",
                "files": [],
            }

        def fake_build_contract(_: Path, _manifest_path: Path) -> dict[str, object]:
            return {
                "schema": "button-contract-v1",
                "rows": [],
            }

        def fake_build_inventory(
            _repo_root: Path,
            _manifest_path: Path,
            _contract_path: Path,
            timings: dict[str, object] | Path | None = None,
            *,
            target_budget_ms: int = 1000,
        ) -> dict[str, object]:
            capture["timings"] = timings
            capture["budget"] = target_budget_ms
            return self._inventory_stub()

        def fake_write_outputs(*_args, **_kwargs) -> None:
            return None

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            run_dir = repo_root / "run"
            latency_path = repo_root / "latency.json"
            if timing_exists:
                latency_path.write_text(
                    '{"schema":"button-latency-measurements-v1","rows":[{"id":"x","baseline_ms":200,"after_ms":150}]}',
                    encoding="utf-8",
                )
            args = self._base_args(Path("latency.json") if timing_exists else Path("missing.json"))

            original_build_manifest = orchestrator.ui_manifest.build_manifest
            original_build_contract = orchestrator.button_contract.build_contract
            original_build_inventory = orchestrator.button_latency_inventory.build_inventory
            original_contract_write = orchestrator.button_contract.write_outputs
            original_inventory_write = orchestrator.button_latency_inventory.write_outputs

            try:
                orchestrator.ui_manifest.build_manifest = fake_build_manifest  # type: ignore[method-assign]
                orchestrator.button_contract.build_contract = fake_build_contract  # type: ignore[method-assign]
                orchestrator.button_latency_inventory.build_inventory = fake_build_inventory  # type: ignore[method-assign]
                orchestrator.button_contract.write_outputs = fake_write_outputs  # type: ignore[method-assign]
                orchestrator.button_latency_inventory.write_outputs = fake_write_outputs  # type: ignore[method-assign]

                result = orchestrator._run_audit_only(args, repo_root, run_dir)
                self.assertEqual(cast(dict[str, object], result)["status"], "passed")
            finally:
                orchestrator.ui_manifest.build_manifest = original_build_manifest
                orchestrator.button_contract.build_contract = original_build_contract
                orchestrator.button_latency_inventory.build_inventory = original_build_inventory
                orchestrator.button_contract.write_outputs = original_contract_write
                orchestrator.button_latency_inventory.write_outputs = original_inventory_write

        if timing_exists:
            self.assertIsInstance(capture["timings"], Path)
            self.assertEqual(cast(Path, capture["timings"]).name, "latency.json")
            self.assertTrue(str(cast(Path, capture["timings"])).endswith("/latency.json"))
        else:
            self.assertIsNone(capture["timings"])
        self.assertEqual(capture["budget"], 1000)

    def test_audit_only_uses_relative_latency_measurement_path(self) -> None:
        self._run_audit_only_with(timing_exists=True)

    def test_audit_only_skips_missing_latency_measurement_path(self) -> None:
        self._run_audit_only_with(timing_exists=False)


if __name__ == "__main__":
    unittest.main()
