(ns file-order.core  
  (:use seesaw.core seesaw.chooser)
  (:require [seesaw.dnd :as dnd])
  (:import (java.awt GraphicsEnvironment BorderLayout)
           (java.awt.geom AffineTransform)
           (java.io File)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon JFileChooser JFrame)))

(def MAX_HEIGHT 200)
(def MAX_WIDTH  200)

(defn calculate-ratio [image]
  (let [w (.getWidth image)
        h (.getHeight image)
        ratio (if (> (/ h w) (/ w h))
                (/ MAX_HEIGHT h)
                (/ MAX_WIDTH w))]
    ratio))

(defn create-thumbnail [f]
  (let [src (ImageIO/read f)
        image (.createCompatibleImage (.getDefaultConfiguration (.getDefaultScreenDevice (GraphicsEnvironment/getLocalGraphicsEnvironment))) MAX_WIDTH MAX_HEIGHT)
        ratio (calculate-ratio src)
        at (AffineTransform/getScaleInstance ratio ratio)]
    (doto (.createGraphics image)
      (.drawRenderedImage src at)
      (.dispose))
    image))

(defn load-files [dir]
  (seq (.listFiles dir)))

(defn create-render-fn [thumbs]
  (fn [renderer info]
    (let [n (:value info)
          thumb (get thumbs n)]
      (.setIcon renderer (ImageIcon. thumb)))))

(defn create-files-listbox [dir]
  (let [files (load-files dir)
        thumbs (into {} (map (fn [f] [(.getName f) (create-thumbnail f)]) files))]
    (listbox
      :model (map #(.getName %) files)
      :renderer (create-render-fn thumbs)
      :drag-enabled? true
      :drop-mode :insert
      :transfer-handler [
         :import [dnd/string-flavor (fn [{:keys [target data drop-location]}]
                                      (doto (.getModel target)
                                        (.remove (:index data))
                                        (.add (:index drop-location) (:selection data))))]
         :export {
           :actions (constantly :move)
           :start   (fn [c] [dnd/string-flavor {:selection (selection c) 
                                                :index (.getSelectedIndex c)}])
          }])))

(defn order-list [& args]
  (println "Order!"))

(defn create-main-frame [dir]
  (doto (JFrame.)
    (.setTitle "file-order")
    (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
    (.setLayout (BorderLayout.))
    (.add (scrollable (create-files-listbox dir)) BorderLayout/CENTER)
    (.add (button :text "Order!"
                  :listen [:action order-list])
          BorderLayout/SOUTH)
    (.pack)
    (.show)))

(defn choose-directory []
  (let [chooser (JFileChooser.)]
    (.setDialogTitle chooser "Select a Directory")
    (.setFileSelectionMode chooser JFileChooser/DIRECTORIES_ONLY)
    (if (= (.showOpenDialog chooser nil) JFileChooser/APPROVE_OPTION)
      (.getSelectedFile chooser)
      nil)))

(defn -main [& args]
  (invoke-later
    (let [dir (choose-directory)]
      (if (not (nil? dir)) (create-main-frame dir)))))