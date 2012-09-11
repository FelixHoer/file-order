(ns file-order.core
  (:import (java.awt GraphicsEnvironment BorderLayout Dimension Toolkit)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener)
           (java.io File)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon JFileChooser JPanel JFrame JButton JLabel JScrollPane SwingUtilities)))

(def ITEM_HEIGHT 200)
(def ITEM_WIDTH  200)

(def ITEM_BORDER_HEIGHT (+ 10 ITEM_HEIGHT))
(def ITEM_BORDER_WIDTH  (+ 10 ITEM_WIDTH))

(defn calculate-ratio [image]
  (let [w (.getWidth image)
        h (.getHeight image)
        ratio (if (> (/ h w) (/ w h))
                (/ ITEM_HEIGHT h)
                (/ ITEM_WIDTH w))]
    ratio))

(defn create-thumbnail [f]
  (let [src (ImageIO/read f)
        image (.createCompatibleImage (.getDefaultConfiguration (.getDefaultScreenDevice (GraphicsEnvironment/getLocalGraphicsEnvironment))) ITEM_WIDTH ITEM_HEIGHT)
        ratio (calculate-ratio src)
        at (AffineTransform/getScaleInstance ratio ratio)]
    (doto (.createGraphics image)
      (.drawRenderedImage src at)
      (.dispose))
    image))

(defn load-files [dir]
  (seq (.listFiles dir)))

(comment
(defn create-render-fn [thumbs]
  (fn [renderer info]
    (let [n (:value info)
          thumb (get thumbs n)]
      (.setIcon renderer (ImageIcon. thumb)))))
)

(defn create-item-panel [name]
  (doto (JPanel.)
    (.add (JLabel. name))))

(defn layout [item-panels width]
  (let [indexed-items (map vector item-panels (iterate inc 0))
        cols (max 1 (quot width ITEM_BORDER_WIDTH))]
    (doseq [[p i] indexed-items]
      (.setBounds p 
        (* ITEM_BORDER_WIDTH (mod i cols)) 
        (* ITEM_BORDER_HEIGHT (quot i cols)) 
        ITEM_WIDTH 
        ITEM_HEIGHT))
    item-panels))

(defn create-files-grid [dir]
  (let [files (load-files dir)
        thumbs (into {} (map (fn [f] [(.getName f) (create-thumbnail f)]) files))
        file-names (map #(.getName %) files)
        item-panels (map create-item-panel file-names)
        grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g))
                    (getPreferredSize []
                      (let [cols (max 1 (quot (.getWidth this) ITEM_BORDER_WIDTH))
                            rows (inc (quot (dec (count files)) cols))]
                        (Dimension. 
                          (* ITEM_BORDER_WIDTH cols) 
                          (* ITEM_BORDER_HEIGHT rows)))))]
    (.setSize grid-panel 800 600)
    (.setLayout grid-panel nil)
    (doseq [item-panel (layout item-panels 800)] 
      (.add grid-panel item-panel))
    grid-panel))

(defn order-list [& args]
  (println "Order!"))

(defn create-button [name action-fn]
  (let [button (JButton. name)
        action-proxy (proxy [ActionListener] []
          (actionPerformed [e]
            (action-fn)))]
    (.addActionListener button action-proxy)
    button))

(defn create-main-frame [dir]
  (doto (JFrame.)
    (.setTitle "file-order")
    (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
    (.setLayout (BorderLayout.))
    (.add (JScrollPane. (create-files-grid dir)) BorderLayout/CENTER)
    (.add (create-button "Order!" order-list) BorderLayout/SOUTH)
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
  (SwingUtilities/invokeLater 
    #(let [dir (choose-directory)]
        (if (not (nil? dir)) (create-main-frame dir))
        nil)))