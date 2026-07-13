# cloud-itonami-isco-1211

**Community Finance Management** — the ISCO-08 1211 (Finance Managers)
actor, the **first wave-1 (design & governance) implemented actor**
per ADR-2607121000.

**Maturity: `:implemented`** — FinanceManagementAdvisor ⊣
FinanceManagementGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
14 tests / 36 assertions green.

The finance-management HARD invariant: **the budget ceiling is a
ledger sum** — the governor recomputes each line's remaining balance
(line amount − Σ registered spends) on every proposal, and an overrun
is held at any confidence. A balance is arithmetic, not a memory, and
neither confidence nor seniority approves past it; the remedy is a
reallocation proposal, on the record. The actor's commit node
registers approved expenditures as spends, so the ceiling tightens
with every approval — state, not memory. Also HARD: invented/foreign
budget lines, non-positive amounts, unregistered organization,
non-`:propose` effect.

Escalations (always human sign-off): `:approve-expenditure` (financial
effect, even within budget), `:reallocate-budget`, low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
