(ns finmgmt.store
  "SSoT for the ISCO-08 1211 community finance-management actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store. First wave-1
  (design & governance) actor.

  Domain:

    client      — a registered organization (:client-id, :name)
    budget-line — a registered budget line {:line-id :client-id :name
                  :amount} (smallest currency unit).
    spend       — a committed expenditure {:spend-id :client-id
                  :line-id :amount}. The remaining budget of a line is
                  ALWAYS line amount − Σ registered spends — a ledger
                  sum, never a remembered number.
    record      — a committed operating record (budget draft, approved
                  expenditure, reallocation) — written ONLY via
                  commit-record!.
    ledger      — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (budget-line [s line-id])
  (spends-of [s line-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-budget-line! [s l])
  (register-spend! [s sp])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (budget-line [_ line-id] (get-in @a [:budget-lines line-id]))
  (spends-of [_ line-id] (filter #(= line-id (:line-id %)) (:spends @a)))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-budget-line! [s l]
    (swap! a assoc-in [:budget-lines (:line-id l)] l) s)
  (register-spend! [s sp]
    (swap! a update :spends (fnil conj []) sp) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :budget-lines {} :spends []
                                    :records [] :ledger []}
                                   seed)))))
