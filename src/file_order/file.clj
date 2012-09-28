(ns file-order.file
  (:require [file-order.model :as model])
  (:use [file-order.constants])
  (:import (java.io File)))

; functions

(defn load-files [dir]
  (sort (filter #(.isFile %) (seq (.listFiles dir)))))

(defn extension-type [n]
  (when-let [extension (second (re-find #"^.+\.(.+)$" n))]
    (let [lower-extension (.toLowerCase extension)]
      (some (fn [[type exts]] (when (contains? exts lower-extension) type)) EXTENSION_MAP))))

(defn strip-prefix [f]
  (let [n (.getName f)
        found (re-find (re-pattern (str FILE_PREFIX "\\d+" "_" "(.+)")) n)]
    (if (nil? found) n (second found))))

(defn format-file [{f :file n :name} idx len]
  (let [padding-len (inc (int (Math/log10 len)))
        padded-idx (with-out-str (printf (str "%" 0 padding-len "d") idx))
        parent (.getParent f)
        separator File/separator]
    (File. (str parent separator FILE_PREFIX padded-idx "_" n))))

(defn order-files! []
  (let [transfers (ref nil)]
    (dosync
      (let [indexed-items (map vector (iterate inc 0) (model/get-items))
            len (count indexed-items)
            transfer-desc (fn [[idx item]] 
                            {:from (:file item) 
                             :to (format-file item idx len)
                             :item item})
            trans (map transfer-desc indexed-items)]
        (model/set-items! (map (fn [{item :item to :to}] (assoc item :file to)) trans))
        (ref-set transfers trans)))
    (when-let [trans @transfers]
      (doseq [{from :from to :to} trans] 
        (.renameTo from to)))))