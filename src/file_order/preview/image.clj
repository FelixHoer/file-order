(ns file-order.preview.image
  (:require [file-order.model :as model]
            [file-order.file  :as file])
  (:use [file-order.constants])
  (:import (java.awt GraphicsEnvironment)
           (java.awt.geom AffineTransform)
           (javax.imageio ImageIO)))

; functions

(defn calculate-ratio [image]
  (let [w (.getWidth image)
        h (.getHeight image)
        ratio (if (> (/ h w) (/ w h))
                (/ IMAGE_HEIGHT h)
                (/ IMAGE_HEIGHT w))]
    ratio))

(defn claculate-limited-size [image]
  (let [w (.getWidth image)
        h (.getHeight image)]
    (if (> w h)
      [IMAGE_WIDTH (* IMAGE_HEIGHT (/ h w))]
      [(* IMAGE_WIDTH (/ w h)) IMAGE_HEIGHT])))

(defn create-thumbnail [src]
  (let [[w h] (claculate-limited-size src)
        graphics-env (GraphicsEnvironment/getLocalGraphicsEnvironment)
        screen-conf (.getDefaultConfiguration (.getDefaultScreenDevice graphics-env))
        image (.createCompatibleImage screen-conf w h)
        ratio (calculate-ratio src)
        transform (AffineTransform/getScaleInstance ratio ratio)]
    (doto (.createGraphics image)
      (.drawRenderedImage src transform)
      (.dispose))
    image))

(defn create-pictures-seq 
  ([image-items]
    (create-pictures-seq (map :name image-items) []))
  ([todo-names failed-names]
    (if (empty? todo-names)
      (if (empty? failed-names)
        nil
        (create-pictures-seq failed-names []))
      (let [first-todo (first todo-names)
            it (model/get-item-by-name first-todo)
            pic (try 
                  (ImageIO/read (:file it)) 
                  (catch Exception e nil))]
        (if-not (nil? pic)
          (cons {:item it :pic pic} (lazy-seq (create-pictures-seq (rest todo-names) failed-names)))
          (lazy-seq (create-pictures-seq (rest todo-names) (cons first-todo failed-names))))))))

(defn create-thumbnail-seq []
  (let [image-item? #(= :image (file/extension-type (:name %)))
        image-items (filter image-item? (model/get-items))
        pic-to-thumb (fn [{it :item pic :pic}] {:item it :thumb (create-thumbnail pic)})]
    (map pic-to-thumb (create-pictures-seq image-items))))