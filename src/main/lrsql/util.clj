(ns lrsql.util
  (:require [clj-uuid]
            [java-time]
            [java-time.properties :as jt-props]
            [hugsql.core :as hug]
            [clojure.spec.alpha :as s]
            [clojure.java.io    :as io]
            [cheshire.core      :as cjson]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.squuid :as squuid]
            [com.yetanalytics.lrs.xapi.document :refer [json-bytes-gen-fn]]
            [com.yetanalytics.lrs.xapi.statements.timestamp :refer [normalize]]
            [lrsql.spec.common :refer [instant-spec]])
  (:import [java.util UUID]
           [java.time Instant]
           [java.io StringReader PushbackReader ByteArrayOutputStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def-hugsql-db-fns
  "A custom version of `hugsql.core/def-db-fns` that reads .sql files
   during compilation (instead of during runtime as in the original macro)."
  [file]
  (let [parsed-defs# (hug/parsed-defs-from-file file)]
    ;; Validate defs at compile time
    (doseq [pdef parsed-defs#]
      (hug/validate-parsed-def! pdef))
    ;; Runtime actions
    `(doseq [~'exp-pdef (map hug/expand-compile-frags ~parsed-defs#)]
       (hug/compile-exprs ~'exp-pdef)
       (hug/dispatch-on-pdef ~'exp-pdef
                             {} ; options - empty map is the hugsql default
                             ~hug/intern-db-fn
                             ~hug/intern-sqlvec-fn))))

(defmacro wrap-parse-fn
  "Wrap `(parse-fn s)` in an exception such that on parse failure, the
   folloiwing error data is thrown:
     :type       ::parse-failure
     :data       `data`
     :data-type  `data-type`"
  [parse-fn data-type data]
  `(try (~parse-fn ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse nil or invalid %s"
                                  ~data-type)
                          {:type      ::parse-failure
                           :data      ~data
                           :data-type ~data-type})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef current-time
  :args (s/cat)
  :ret instant-spec)

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (java-time/instant))

(def valid-units
  (set (map jt-props/unit-key jt-props/predefined-units)))

(s/fdef offset-time
  :args (s/cat :ts instant-spec
               :offset-amt nat-int?
               :offset-unit valid-units)
  :ret instant-spec)

(defn offset-time
  "Given a java.util.Instant timestamp `ts` and the offset given by the
   `offset-amount` int and the `offset-unit` keyword, return another timestamp
   that was offset by the given amount. Valid units are given by
   `java-time.repl/show-units`."
  [^Instant ts offset-amount offset-unit]
  (.plus ts offset-amount (java-time/unit offset-unit)))

(s/fdef str->time
  :args (s/cat :ts-str ::xs/timestamp)
  :ret instant-spec)

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (wrap-parse-fn java-time/instant "timestamp" ts-str))

(s/fdef time->str
  :args (s/cat :ts instant-spec)
  :ret ::xs/timestamp)

(defn time->str
  "Convert a java.util.Instant timestamp into a string. Said string is
   normalized according to the requirements of the lrs library."
  [ts]
  (normalize (java-time/format ts)))

(s/fdef time->millis
  :args (s/cat :ts instant-spec)
  :ret nat-int?)

(defn time->millis
  "Convert a java.util.Instant timestamp into a number representing the
   number of milliseconds since January 1, 1970."
  [^Instant ts]
  (.toEpochMilli ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::timestamp instant-spec)
(s/def ::base-uuid uuid?)
(s/def ::squuid uuid?)

(s/fdef generate-squuid*
  :args (s/cat)
  :ret (s/keys :req-un [::timestamp ::base-uuid ::squuid]))

(defn generate-squuid*
  "Return a map containing the following:
   :squuid     The sequential UUID made up of a base UUID and timestamp.
   :base-uuid  The base v4 UUID that provides the lower 80 bits.
   :timestamp  The timestamp that provides the higher 48 bits."
  []
  (squuid/generate-squuid*))

(s/fdef generate-squuid
  :args (s/cat)
  :ret ::squuid)

(defn generate-squuid
  "Return a new sequential UUID, or SQUUID. The most significant 48 bits
   are created from a timestamp representing the current time, which always
   increments, enforcing sequentialness. See the colossal-squuid lib for more
   details."
  []
  (squuid/generate-squuid))

(s/fdef str->uuid
  :args (s/cat :uuid-str ::xs/uuid)
  :ret uuid?)

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (wrap-parse-fn UUID/fromString "UUID" uuid-str))

(s/fdef time->uuid
  :args (s/cat :ts instant-spec)
  :ret uuid?)

(defn time->uuid
  "Convert a java.util.Instant timestamp to a UUID. The upper 48 bits represent
   the timestamp, while the lower 80 bits are `8FFF-8FFF-FFFFFFFFFFFF`."
  [^Instant ts]
  (squuid/time->uuid ts))

(s/fdef uuid->str
  :args (s/cat :uuid uuid?)
  :ret ::xs/uuid)

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Overall approach is taken from lrs:
;; https://github.com/yetanalytics/lrs/blob/master/src/main/com/yetanalytics/lrs/xapi/document.cljc
;; Reimplemented here as functions instead of dynamic vars in order to avoid
;; issues with AOT compliation.

(defn- parse-json*
  "Read a JSON string or byte array `data`."
  [data]
  (let [string (if (bytes? data) (String. ^"[B" data) data)]
    (with-open [rdr (PushbackReader. (StringReader. string) 64)]
      (doall (cjson/parsed-seq rdr)))))

(s/def ::object? boolean?)

(s/fdef parse-json
  :args (s/cat :data (s/with-gen
                       (s/or :string string? :bytes bytes?)
                       json-bytes-gen-fn)
               :kwargs (s/keys* :opt-un [::object?]))
  :ret ::xs/any-json)

(defn parse-json
  "Read a JSON string or byte array `data`. `data` must only consist of one
   JSON object, array, or scalar; in addition, `data` must be an object by
   default. To parse JSON arrays or scalars, set `:object?` to false."
  [data & {:keys [object?] :or {object? true}}]
  (let [[result & ?more] (wrap-parse-fn parse-json* "JSON" data)]
    (cond
      ?more
      (throw (ex-info "More input after first JSON data!"
                      {:type  ::extra-json-input
                       :first result
                       :rest  ?more}))
      (and object? (not (map? result)))
      (throw (ex-info "Parsed JSON result is not an object!"
                      {:type   ::not-json-object
                       :result result}))
      :else
      result)))

(defn- write-json*
  "Write `jsn` to an output stream."
  [out-stream jsn]
  (with-open [wtr (io/writer out-stream)]
    (cjson/generate-stream jsn wtr)
    out-stream))

(s/fdef write-json
  :args (s/cat :jsn ::xs/any-json)
  :ret bytes?)

(defn write-json
  "Write `jsn` to a byte array."
  [jsn]
  (let [out-stream (ByteArrayOutputStream. 4096)]
    (.toByteArray ^ByteArrayOutputStream (write-json* out-stream jsn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bytes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; IOFactory predicate taken from lrs.xapi.statements.attachment/content
(s/fdef data->bytes ; TODO: Add more options if need be
  :args (s/cat :data (s/or :string string?
                           :bytes bytes?
                           :io-factory #(satisfies? clojure.java.io/IOFactory %)))
  :ret bytes?)

(defn data->bytes
  "Convert `data` into a byte array."
  [data]
  (if (bytes? data)
    data
    (let [baos (ByteArrayOutputStream.)]
      (with-open [in (io/input-stream data)]
        (io/copy in baos)
        (.flush baos)
        (.toByteArray baos)))))
