(ns file-order.preview.image
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

(defn create-thumbnail [f]
  (let [src (ImageIO/read f)
        [w h] (claculate-limited-size src)
        graphics-env (GraphicsEnvironment/getLocalGraphicsEnvironment)
        screen-conf (.getDefaultConfiguration (.getDefaultScreenDevice graphics-env))
        image (.createCompatibleImage screen-conf w h)
        ratio (calculate-ratio src)
        transform (AffineTransform/getScaleInstance ratio ratio)]
    (doto (.createGraphics image)
      (.drawRenderedImage src transform)
      (.dispose))
    image))