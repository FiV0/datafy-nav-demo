(ns datafy-nav-demo
  (:require [clojure.java.io :as io]
            [clojure.core.protocols :as p]
            [next.jdbc :as jdbc]))

;; do a couple of examples
;; - stretching that this my interpretation
;; - introduction jdbc as a motivating example
;; - maybe auto key resolving in xt

;; TODO jean corfield post
;; TODO datomic + xt auto key resolving

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
