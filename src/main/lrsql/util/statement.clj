(ns lrsql.util.statement
  (:require [ring.util.codec :refer [form-encode]]
            [lrsql.util :as u]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Preparation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; If a Statement lacks a version, the version MUST be set to 1.0.0
;; TODO: Change for version 2.0.0
(def xapi-version "1.0.0")

;; NOTE: It is recommended that we override any pre-existing authorities
;; in a statement, unless there's a high degree of trust. We assume such
;; a degree of trust (e.g. if the LRSs are part of the same system), but
;; we may need to address this in the future (e.g. set using env vars).

(defn prepare-statement
  "Prepare `statement` for LRS storage by coll-ifying context activities
   and setting missing id, timestamp, authority, version, and stored
   properties."
  [authority statement]
  (let [{?id        "id"
         ?timestamp "timestamp"
         ?authority "authority"
         ?version   "version"}
        statement
        {squuid      :squuid
         squuid-ts   :timestamp
         squuid-base :base-uuid}
        (u/generate-squuid*)
        assoc-to-stmt (fn [stmt k v] ; Assoc while also changing the meta
                        (-> stmt
                            (assoc k v)
                            (vary-meta update
                                       :assigned-vals
                                       conj
                                       (keyword k))))
        squuid-ts-str (u/time->str squuid-ts)]
    (cond-> statement
      true
      ss/fix-statement-context-activities
      true
      (vary-meta assoc :assigned-vals #{})
      true
      (vary-meta assoc :primary-key squuid)
      true
      (assoc-to-stmt "stored" squuid-ts-str)
      (not ?id)
      (assoc-to-stmt "id" (u/uuid->str squuid-base))
      (not ?timestamp)
      (assoc-to-stmt "timestamp" squuid-ts-str)
      (not ?authority)
      (assoc-to-stmt "authority" authority)
      (not ?version)
      (assoc-to-stmt "version" xapi-version))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Equality
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn statement-equal?
  "Compare two Statements based on their immutable properties."
  [stmt1 stmt2]
  (ss/statements-immut-equal? stmt1 stmt2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn format-statement
  "Given `statement`, format it according to the value of `format`:
   - :exact      No change to the Statement
   - :ids        Return only the IDs in each Statement object
   - :canonical  Return a \"canonical\" version of lang maps based on `ltags`."
  [statement format ltags]
  (case format
    :exact
    statement
    :ids
    (ss/format-statement-ids statement)
    :canonical
    (ss/format-canonical statement ltags)
    ;; else
    (throw (ex-info "Unknown format type"
                    {:type   ::unknown-format-type
                     :format format}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Pre-query

(defn ensure-default-max-limit
  "Given `?limit`, apply the maximum possible limit (if it is zero
   or exceeds that limit) or the default limit (if it is `nil`).
   The maximum and default limits are set in as environment vars."
  [{limit-max     :stmt-get-max
    limit-default :stmt-get-default
    :as _lrs-config}
   {?limit :limit
    :as    params}]
  (assoc params
         :limit
         (cond
           ;; Ensure limit is =< max
           (pos-int? ?limit)
           (min ?limit limit-max)
           ;; If zero, spec says use max
           (and ?limit (zero? ?limit))
           limit-max
           ;; Otherwise, apply default
           :else
           limit-default)))

;; Post-query

(defn make-more-url
  "Forms the `more` URL value from `query-params`, the URL prefix `prefix` and
   the Statement PK `next-cursor` which points to the first Statement of the
   next page."
  [query-params prefix next-cursor]
  (let [{?agent :agent} query-params]
    (str prefix
         "/statements?"
         (form-encode
          (cond-> query-params
            true   (assoc :from next-cursor)
            ?agent (assoc :agent (String. (u/write-json ?agent))))))))
