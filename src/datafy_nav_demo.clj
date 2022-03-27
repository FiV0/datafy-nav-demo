(ns datafy-nav-demo
  (:require [clojure.core.protocols :as p]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]
            [cognitect.rebl :as rebl]))

(comment
  (rebl/ui))

;; do a couple of examples
;; - stretching that this my interpretation
;; - introduction jdbc as a motivating example
;; - maybe auto key resolving in xt

;; TODO jean corfield post
;; TODO datomic + xt auto key resolving
;; TODO add REBL
;; TODO look at xtdb-inspector
;; TODO think about url navigation / datafication / visualization

(extend-protocol p/Datafiable
  java.io.File
  (datafy [this]
    (if (.isDirectory this)
      (vary-meta
       (-> this .list lazy-seq)
       merge
       {`p/nav (fn [_ _ v]
                 (io/file this v))})
      {:length (.length this)
       :name (.getName this)})))

(def f (io/file "/"))

(p/datafy f)

(meta *1)

(->> f
     p/datafy
     (#(p/nav % 0 (first %)))
     p/datafy)

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

(p/datafy nav-f)

(def datafied-nav-f (p/datafy nav-f))

(p/nav datafied-nav-f (-> datafied-nav-f first first) (-> datafied-nav-f first second))

(p/datafy *1)

;; jdbc

;; here I first had `with-open`. This doesn't work as the connection gets
;; closed after the first connection.
(def con (jdbc/get-connection
          (jdbc/get-datasource {:dbtype "sqlite"
                                :dbname "resources/chinook.db"})))

(def res (jdbc/execute! con ["SELECT * FROM tracks;"]
                        ;; add other schema relationships here
                        {:schema {:tracks/GenreId :genres/GenreId ; many-to-one
                                  :genres/GenreId [:tracks/GenreId]}})) ;one-to-many

(def track (first res))

(meta track)

(p/nav track :tracks/GenreId (:tracks/GenreId track))

;; in case of jdbc datafy is mostly irrelevant as data is mostly already in clojure form

;; xt

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/dev/tx-log")
      :xtdb/document-store (kv-store "data/dev/doc-store")
      :xtdb/index-store (kv-store "data/dev/index-store")})))

(def xtdb-node (start-xtdb!))

(defn stop-xtdb! [] (.close xtdb-node))

(def EOF (Object.))

(defn read-data []
  (with-open [pbr (-> (io/file "resources/movies.edn")
                      io/reader
                      java.io.PushbackReader.)]
    (loop [res []]
      (let [form (edn/read {:eof EOF} pbr)]
        (if (= form EOF)
          res
          (recur (conj res form)))))))

(def data (read-data))

(xt/submit-tx xtdb-node (take 1 data))

(defn db [] (xt/db xtdb-node))

(->> (xt/q (db)
           '{:find [(pull ?e [*])]
             :where [[?e :tmdb.person/id 65731]]})
     ;; first
     ;; first
     ;; (xt/entity (db))
     )

(def schema [{:type :movie
              :key :tmdb.movie/id
              :key-fn (fn [id] (keyword (name 'tmdb) (str "movie-" id)))}
             {:type :person
              :key :tmdb.person/id
              :key-fn (fn [id] (keyword (name 'tmdb) (str "person-" id)))}])

(defn find-mapping [schema type]
  (some #(when (= type (:key %)) %) schema))

(comment
  (find-mapping schema :tmbd.movie/id))

(defn xt-nav [schema db]
  (fn [e a v]
    (let [ks (set (keys e))]
      (if (not (ks a))
        (do
          (println "not case")
          v)
        (if-let [{:keys [key-fn]} (find-mapping schema a)]
          (xt/entity db (key-fn v))
          (do
            (println "other not")
            (get e a)))))))

(defn q [schema db query]
  (let [res (-> (xt/q db query) first)
        nav (xt-nav schema db)]
    (->> res
         (map #(vary-meta % merge {`p/nav nav}))
         (into #{}))))

(def xt-q (partial q schema))

(defn entity [schema db eid]
  (vary-meta (xt/entity db eid)
             merge
             {`p/nav (xt-nav schema db)}))

(def xt-entity (partial entity schema))

(def eid (-> (xt/q (db)
                   '{:find [?e]
                     :where [[?e :tmdb.person/id]]})
             first
             first))

(def e (xt-entity (db) eid))

(meta e)

(p/nav e :tmdb.person/id 65731)
