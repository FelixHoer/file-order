(ns file-order.core
  (:import (java.awt GraphicsEnvironment BorderLayout GridLayout Dimension Toolkit)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener ComponentListener)
           (java.io File)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon JFileChooser JPanel JFrame JButton JLabel JScrollPane SwingUtilities)
           (javax.swing.event MouseInputAdapter)))

(def IMAGE_HEIGHT 200)
(def IMAGE_WIDTH  200)

(def ITEM_HEIGHT (+ 30 IMAGE_HEIGHT))
(def ITEM_WIDTH  IMAGE_WIDTH)

(def ITEM_BORDER 10)

(def ITEM_BORDER_HEIGHT (+ ITEM_BORDER ITEM_HEIGHT))
(def ITEM_BORDER_WIDTH  (+ ITEM_BORDER ITEM_WIDTH))

(defn calculate-ratio [image]
  (let [w (.getWidth image)
        h (.getHeight image)
        ratio (if (> (/ h w) (/ w h))
                (/ IMAGE_HEIGHT h)
                (/ IMAGE_HEIGHT w))]
    ratio))

(defn create-thumbnail [f]
  (let [src (ImageIO/read f)
        image (.createCompatibleImage (.getDefaultConfiguration (.getDefaultScreenDevice (GraphicsEnvironment/getLocalGraphicsEnvironment))) IMAGE_WIDTH IMAGE_HEIGHT)
        ratio (calculate-ratio src)
        at (AffineTransform/getScaleInstance ratio ratio)]
    (doto (.createGraphics image)
      (.drawRenderedImage src at)
      (.dispose))
    image))

(defn load-files [dir]
  (seq (.listFiles dir)))

(defn create-item-label [f]
  (doto (JLabel. (.getName f) (ImageIcon. (create-thumbnail f)) JLabel/CENTER)
    (.setVerticalTextPosition JLabel/BOTTOM)
    (.setHorizontalTextPosition JLabel/CENTER)))

(defn create-item-panel [f]
  (doto (JPanel.)
    (.add (create-item-label f))))

(defn layout! [panel]
  (let [cols (max 1 (quot (.getWidth panel) ITEM_BORDER_WIDTH))]
    (.setLayout panel (GridLayout. 0 cols ITEM_BORDER ITEM_BORDER))))

(defn create-resize-proxy [f]
  (proxy [ComponentListener] []
    (componentHidden [e])
    (componentMoved [e])
    (componentShown [e])
    (componentResized [e]
      (f))))

(defn create-multiselect-mouse-listener [items]
  (proxy [MouseInputAdapter] []
    (mousePressed [e]
      (println "pressed: " (.getSource e)))
    (mouseDragged [e]
      (println "drag"))
    (mouseReleased [e]
      (println "release"))))

(defn create-files-grid [items]
  (let [grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g)))
        resize-listener (create-resize-proxy #(layout! grid-panel))
        mouse-listener (create-multiselect-mouse-listener items)]
    (doseq [{panel :panel} items] 
      (.add grid-panel panel))
    (doto grid-panel
      (.setSize 800 600)
      (.addComponentListener resize-listener)
      (.addMouseListener mouse-listener)
      (.addMouseMotionListener mouse-listener))))

(defn order-list [& args]
  (println "Order!"))

(defn create-button [name action-fn]
  (let [button (JButton. name)
        action-proxy (proxy [ActionListener] []
          (actionPerformed [e]
            (action-fn)))]
    (doto button 
      (.addActionListener action-proxy))))

(defn create-vertical-scrollpane [c]
  (JScrollPane. c 
    JScrollPane/VERTICAL_SCROLLBAR_AS_NEEDED 
    JScrollPane/HORIZONTAL_SCROLLBAR_NEVER))

(defn create-main-frame [items]
  (let [frame (JFrame.)
        grid-panel (create-files-grid items)]
    (doto frame
      (.setTitle "file-order")
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setLayout (BorderLayout.))
      (.add (create-vertical-scrollpane grid-panel) BorderLayout/CENTER)
      (.add (create-button "Order!" order-list) BorderLayout/SOUTH)
      (.addComponentListener (create-resize-proxy 
        #(.setSize grid-panel (.getWidth frame) (.getHeight grid-panel))))
      (.pack)
      (.show))))

(defn choose-directory []
  (let [chooser (JFileChooser.)]
    (.setDialogTitle chooser "Select a Directory")
    (.setFileSelectionMode chooser JFileChooser/DIRECTORIES_ONLY)
    (if (= (.showOpenDialog chooser nil) JFileChooser/APPROVE_OPTION)
      (.getSelectedFile chooser))))

(defn setup []
  (let [dir (choose-directory)]
    (if (not (nil? dir)) 
      (let [files (load-files dir)
            items (map (fn [f] {:file f :panel (create-item-panel f)}) files)
            frame (create-main-frame items)])
      
      )))

(defn -main [& args]
  (SwingUtilities/invokeLater setup))