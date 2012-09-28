(ns file-order.core
  (:require [file-order.gui   :as gui]
            [file-order.model :as model]
            [file-order.file  :as file])
  (:import (javax.swing SwingUtilities)))

; functions

(defn create-item-struct [f]
  (let [n (file/strip-prefix f)
        p (gui/create-item-panel n f)]
    (struct-map model/item-struct :name n :file f :panel p :selected? false)))

(defn setup! []
  (when-let [dir (gui/choose-directory)]
    (let [files (file/load-files dir)]
      (if-not (empty? files)
        (do
          (dosync
            (model/set-items! (map create-item-struct files)))
          (gui/create-main-frame))
        (recur)))))

(defn -main [& args]
  (SwingUtilities/invokeLater setup!))