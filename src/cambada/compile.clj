(ns cambada.compile
  (:gen-class)
  (:require [cambada.clean :as clean]
            [cambada.cli :as cli]
            [cambada.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.find :as ns.find])
  (:import (java.util.jar JarFile)))

(def cli-options
  (concat [["-a" "--aot NS_NAMES" "Namespaces to be AOT-compiled or `all` (default)"
            :default ['all]
            :default-desc "all"
            :parse-fn #(as-> % $
                         (string/split $ #":")
                         (map symbol $))]]
          cli/base-cli-options))

(defn classpath-entries
  []
  (string/split
   (System/getProperty "java.class.path")
   (re-pattern (System/getProperty "path.separator"))))

(defn namespacess-in-jar
  [jar-name]
  (println jar-name)
  (ns.find/find-namespaces-in-jarfile
   (JarFile. (io/file jar-name))))

(defn classpath-namespaces
  [{:keys [aot] :as task}]
  (if (= (first aot) 'all)
    (->> (utils/get-dep-jars task)
         (filter #(re-find #"\.jar$" %))
         (mapcat namespacess-in-jar)
         distinct)
    '()))

(defn ^:private aot-namespaces
  [{:keys [aot deps-map] :as task}]
  (if (= (first aot) 'all)
    (->> (:paths deps-map)
         (map io/file)
         (map ns.find/find-namespaces-in-dir)
         distinct
         flatten)
    aot))

(defn apply! [{:keys [deps-map out] :as task}]
  (clean/apply! task)
  (let [target (utils/compiled-classes-path out)
        aot-ns (aot-namespaces task)
        other-ns (classpath-namespaces task)]
    (utils/mkdirs target)
    (cli/info "Creating" target)
    (binding [*compile-path* target]
      ; We want to ensure that all classes in current project are complilable
      (doseq [ns aot-ns]
        (cli/info "  Compiling" ns)
        (compile ns))
      ; For all other namespaces. i.e. some class that is not referenced
      ; by namespaces in current project we should try to compile them.
      ; For example if there is :gen-class in some nemespace that is not
      ; in current project but we need it. We will try as there are plenty
      ; of namespaces even in clojure that are not AOT compliable
      (doseq [ns other-ns]
        (cli/info "  Compiling" ns)
        (try (compile ns)
             (catch Exception e
               (cli/warn
                (format "  Unable to compile %s" ns))))))))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.compile"
      :entrypoint-description
      "Compiles the specified namespaces into a set of classfiles."
      :apply-fn apply!})))
