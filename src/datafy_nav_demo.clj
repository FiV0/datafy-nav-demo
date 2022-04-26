(ns datafy-nav-demo
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]
            [cognitect.rebl :as rebl]))


(d/datafy *ns*)

;; protocols via metadata

(comment
  (rebl/ui))

(extend-protocol p/Datafiable
  java.io.File
  (datafy [this]
    (if (.isDirectory this)
      (vary-meta (-> this .list lazy-seq)
                 merge
                 {`p/nav (fn [_ _ v]
                           (io/file this v))})
      {:length (.length this)
       :name   (.getName this)})))

(def f (io/file "/"))

(d/datafy f)

(meta *1)

(->> f
     d/datafy
     (#(d/nav % 0 (first %)))
     d/datafy)

(defrecord NavFS [path]
  p/Datafiable
  (datafy [this]
    (let [f (io/file path)]
      (if-not (.isDirectory f)
        (.getPath f)
        (into {}
              (map (fn [c]
                     [(.getPath c)
                      (if (.isDirectory c)
                        (NavFS.
                         (.getPath c))
                        'file)]))
              (.listFiles f)))))
  p/Navigable
  (nav [this k v]
    (NavFS. k)))


(def nav-f (NavFS. "/"))

(d/datafy nav-f)

(def datafied-nav-f (p/datafy nav-f))

(d/nav datafied-nav-f (-> datafied-nav-f first first) (-> datafied-nav-f first second))

(d/datafy *1)

;; jdbc

(def con (jdbc/get-connection
          (jdbc/get-datasource {:dbtype "sqlite"
                                :dbname "resources/chinook.db"})))

(def res (jdbc/execute! con ["SELECT * FROM tracks;"]
                        ;; add other schema relationships here
                        {:schema {:tracks/GenreId :genres/GenreId ; many-to-one
                                  :genres/GenreId [:tracks/GenreId]}})) ;one-to-many

(def track (first res))

(meta track)

(d/nav track :tracks/GenreId (:tracks/GenreId track))

;; xt

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "xtdb-chinook/tx-log")
      :xtdb/document-store (kv-store "xtdb-chinook/doc-store")
      :xtdb/index-store (kv-store "xtdb-chinook/index-store")})))

(def xtdb-node (start-xtdb!))

(defn stop-xtdb! [] (.close xtdb-node))

(defn db [] (xt/db xtdb-node))

(xt/q (db)
      '{:find [?name]
        :where
        [[?t :track/name "For Those About To Rock (We Salute You)" ]
         [?t :track/album ?album]
         [?album :album/artist ?artist]
         [?artist :artist/name ?name]]})

(defn xt-nav [db]
  (fn [e a v]
    (if-let [new-e (xt/entity db v)]
      (vary-meta new-e
                 merge
                 {`p/nav (xt-nav db)})
      v)))

(defn entity [db eid]
  (vary-meta (xt/entity db eid)
             merge
             {`p/nav (xt-nav db)}))

(entity (db) :track/id-1)

;; reverse lookup

(defn- reverse-attribute? [a]
  (= \_ (first (name a))))

(comment
  (reverse-attribute? :foo/_bar))

(defn- reverse-attribute->attribute [a]
  (keyword (namespace a) (subs (name a) 1)))

(comment
  (reverse-attribute->attribute :foo/_bar))

(defn reverse-lookup [db e reverse-a]
  (let [attribute (reverse-attribute->attribute reverse-a)
        query `{:find [~'(pull ?e [*])]
                :where
                [[~'?e ~attribute ~(:xt/id e)]]}]
    (xt/q db query)))

(comment
  (def album-e (entity (db) :album/id-1))
  (reverse-lookup (db) album-e :track/_album))

(defn- attach-nav-fn [o nav-fn]
  (vary-meta o merge {`p/nav nav-fn}))

(defn xt-nav [db]
  (fn [e a v]
    (if-let [new-e (xt/entity db v)]
      (attach-nav-fn new-e (xt-nav db))
      (if (reverse-attribute? a)
        (let [res (reverse-lookup db e a)]
          (into (empty res) (map (fn [[e]] [(attach-nav-fn e (xt-nav db))])) res))
        v))))

(defn entity [db eid]
  (attach-nav-fn (xt/entity db eid) (xt-nav db)))

(entity (db) :track/id-1)

;; urls

(java.net.URL. "https://clojure.org")


;; caveats
;; metadata attachment with respect to serialization

(def max-lazy 100)

(extend-protocol p/Datafiable
  clojure.lang.LazySeq
  (datafy [this]
    (if (< (bounded-count max-lazy this) max-lazy)
      this
      (lazy-seq (take max-lazy this)))))

(def lazy-datafied (d/datafy (lazy-seq (range))))

(keys (meta lazy-datafied))
