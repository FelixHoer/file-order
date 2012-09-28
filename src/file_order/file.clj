(ns file-order.file
  (:require [file-order.model :as model])
  (:use [file-order.constants])
  (:import (java.io File)))

; functions

(defn load-files [dir]
  (filter #(.isFile %) (seq (.listFiles dir))))

(defn extension-type [n]
  (let [extension (second (re-find #"^.+\.(.+)$" n))]
    (when-not (nil? extension)
      (let [lower-extension (.toLowerCase extension)]
        (some (fn [[type exts]] (when (contains? exts lower-extension) type)) EXTENSION_MAP)))))

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
  (let [indexed-items (map vector (iterate inc 0) (model/get-items))
        len (count indexed-items)
        transfer-desc (fn [[idx item]] 
                        {:from (:file item) 
                         :to (format-file item idx len)
                         :item item})
        transfers (map transfer-desc indexed-items)]
    (doseq [{from :from to :to} transfers] 
      (.renameTo from to))
    (dosync
      (model/set-items! (map (fn [{item :item to :to}] (assoc item :file to)) transfers)))))