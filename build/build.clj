(ns build
  (:require
    [cemerick.pomegranate.aether :as mvn]
    [clojure.java.io :as io]
    [clojure.tools.build.api :as b]))

(defn get-tag
  []
  (b/git-process {:git-args "tag --points-at HEAD"}))

(defn add-defaults
  [params]
  (merge {:jar "target/out.jar"
          :pom "target/pom.xml"
          :version (get-tag)
          :clojars-username (System/getenv "CLOJARS_USERNAME")
          :clojars-password (System/getenv "CLOJARS_PASSWORD")}
         params))

(def basis (b/create-basis {:project "deps.edn"}))

(defn clean
  [params]
  (b/delete {:path "target"})
  params)

(defn jar
  [{:keys [version jar] :as params}]
  {:pre [version jar]}
  (b/jar {:class-dir "src"
          :jar-file jar})
  params)

(defn pom
  [{:keys [version lib] :as params}]
  {:pre [version lib]}
  (b/write-pom {:target "target"   ;; don't embed the pom, just produce it so deployment is possible
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  params)


(defn publish
  [{:keys [clojars-username clojars-password jar pom lib version] :as params}]
  {:pre [clojars-username clojars-password jar pom lib version]}
  (mvn/deploy :coordinates [lib version]
              :jar-file (io/file jar)
              :pom-file (io/file pom)
              :repository {"clojars" (merge
                                       (get-in basis [:mvn/repos "clojars"])
                                       {:username clojars-username
                                        :password clojars-password})})
  params)

(defn log
  [params msg & keys]
  (println msg \tab (str (when keys (pr-str (select-keys params keys)))))
  params)

(def whitelist
  [:jar :version :lib :pom])

(defn pipeline
  [params]
  (-> params
      add-defaults
      (log "Cleaning...")
      clean
      (log "Creating jar..." :jar)
      jar
      (log "Writing pom..." :version :lib)
      pom
      (log "Publishing to clojars with redacted credentials..." :jar :pom)
      publish
      (log "Succesfully published to Clojars." :lib :version)
      (select-keys whitelist)
      prn))
