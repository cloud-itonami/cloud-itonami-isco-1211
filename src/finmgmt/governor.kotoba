(ns finmgmt.governor
  "FinanceManagementGovernor — the independent safety/traceability layer
  for the ISCO-08 1211 community finance-management actor (itonami
  actor pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. First wave-1 actor.
  Finance-management twist: the remaining budget is RECOMPUTED from the
  registered spend ledger every time — a balance is a sum, not a
  memory, and an overrun is arithmetic that neither confidence nor
  seniority can approve away.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. budget-line basis — an expenditure must cite a REGISTERED budget
                           line belonging to this client (no invented
                           budgets).
    4. amount sanity     — the amount must be a positive integer.
    5. budget ceiling    — amount must be <= line amount − Σ registered
                           spends on that line (recomputed from the
                           ledger). Overruns are held at any
                           confidence; the remedy is a reallocation
                           proposal, on the record.
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-expenditure (financial effect — always human, even
       within budget).
    7. :op :reallocate-budget (structural change — always human).
    8. low confidence (< `confidence-floor`)."
  (:require [finmgmt.store :as store]))

(def confidence-floor 0.6)

(defn- committed-total [store line-id]
  (transduce (map :amount) + 0 (store/spends-of store line-id)))

(defn- hard-violations [{:keys [request proposal]} client-record line store]
  (let [{:keys [op line-id amount]} proposal
        spend-op? (= :approve-expenditure op)
        remaining (when line (- (:amount line) (committed-total store line-id)))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and spend-op? (nil? line-id))
      (conj {:rule :no-budget-line :detail "支出は予算 line の引用が必須（予算の捏造禁止）"})

      (and spend-op? line-id (nil? line))
      (conj {:rule :unknown-budget-line :detail (str "未登録 budget line: " line-id)})

      (and spend-op? line (not= (:client-id line) (:client-id request)))
      (conj {:rule :budget-line-wrong-client :detail "budget line が別 client のもの"})

      (and spend-op? (not (and (integer? amount) (pos? amount))))
      (conj {:rule :invalid-amount :detail (str "金額が不正: " amount)})

      (and spend-op? line (integer? amount) (pos? amount)
           (= (:client-id line) (:client-id request))
           (> amount remaining))
      (conj {:rule :budget-overrun
             :detail (str "金額 " amount " > 残高 " remaining
                          "（残高は台帳の合計であって記憶ではない — 是正は再配分提案を記録に残すこと）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `finmgmt.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        line (some->> (:line-id proposal) (store/budget-line store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record line store)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (contains? #{:approve-expenditure :reallocate-budget} (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
