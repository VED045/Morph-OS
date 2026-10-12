# Phase 8 increment — workspace and native AI hardening

Version 0.3 validates all new snapshots and history entries before restoring them, protects page/widget occupancy, bounds local-model import and output, rejects stale or unsupported AI proposals, and isolates native CPU inference from the launcher process. Icon treatment and home removal are tested through undo.

A pinned Qwen3 0.6B INT4 model ran on the API 36 emulator and generated a validated functional three-minute timer. The public small-model and pinned-model hashes are recorded in ON_DEVICE_AI.md. No provider key or model weights are bundled.

Native UI checks include paging/recreation, model setup state, accessible arrangement controls and a manually observed drag/swap that survived force-stop/reopen. Screenshots were inspected and clipping defects corrected before packaging. Current automated results are in VERIFICATION.md.

This is an increment, not completion of production acceptance. Physical OnePlus/OEM memory/performance/battery, formal TalkBack/large-font checks, supply-chain review, production signing and live cloud inference remain pending. Native library presence and emulator success do not replace physical-device tests.
