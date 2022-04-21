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

;; TODO datomic + xt auto key resolving
;; TODO look at xtdb-inspector
;; TODO think about url navigation / datafication / visualization

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

;; (defn q [db query]
;;   (let [res (-> (xt/q db query) first)
;;         nav (xt-nav schema db)]
;;     (->> res
;;          (map #(vary-meta % merge {`p/nav nav}))
;;          (into #{}))))

;; (def xt-q (partial q schema))



;; (def xt-entity (partial entity schema))

;; (def eid (-> (xt/q (db)
;;                    '{:find [?e]
;;                      :where [[?e :tmdb.person/id]]})
;;              first
;;              first))

;; (def e (xt-entity (db) eid))

;; (meta e)

;; (d/nav e :tmdb.person/id 65731)

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
