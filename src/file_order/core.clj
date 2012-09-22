(ns file-order.core
  (:import (java.awt GraphicsEnvironment BorderLayout GridLayout Dimension Toolkit Color)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener ComponentListener InputEvent)
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

(def SELECTED_BACKGROUND (Color. 120 120 220))
(def UNSELECTED_BACKGROUND (Color. 220 220 220))

(def items (ref []))
(def selected-items (ref []))
(def unselected-items (ref []))

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
    (.setOpaque true)
    (.setBackground UNSELECTED_BACKGROUND)
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

(defn in-bounds? [[x y] [a b w h]]
  (and
    (and (>= x a) (<= x (+ a w)))
    (and (>= y b) (<= y (+ b h)))))

(defn find-item [x y]
  (let [convert-bounds (fn [p] 
                        (let [r (.getBounds p nil)] 
                          [(.getX r) (.getY r) (.getWidth r) (.getHeight r)]))]
    (first (filter #(in-bounds? [x y] (convert-bounds (:panel %))) @items))))

(defn without [i coll]
  (filter #(not (= i %)) coll))

(defn select-item [item]
  (if (not (nil? item))
    (do
      (alter selected-items conj item)
      (.setBackground (:panel item) SELECTED_BACKGROUND))))

(defn unselect-item [item]
  (if (not (nil? item))
    (do
      (alter selected-items #(without item %))
      (.setBackground (:panel item) UNSELECTED_BACKGROUND))))

(defn unselect-all-items []
  (doseq [{panel :panel} @selected-items] 
    (.setBackground panel UNSELECTED_BACKGROUND))
  (ref-set selected-items []))

(defn key-pressed? [key-mask e]
  (= key-mask (bit-and (.getModifiersEx e) key-mask)))

(def ctrl-pressed?  (partial key-pressed? InputEvent/CTRL_DOWN_MASK))
(def shift-pressed? (partial key-pressed? InputEvent/SHIFT_DOWN_MASK))

(defn create-multiselect-mouse-listener []
  (proxy [MouseInputAdapter] []
    (mousePressed [e]
      (println "press")
      (cond
        (ctrl-pressed? e)
          (let [item (find-item (.getX e) (.getY e))
                in-selected? (some #(= item %) @selected-items)
                alter-fn (if in-selected? unselect-item select-item)] 
            (dosync 
              (alter-fn item)))
        (shift-pressed? e)
          nil
        :else 
          (dosync 
            (unselect-all-items)
            (select-item (find-item (.getX e) (.getY e)))))
      (println @selected-items))
    (mouseDragged [e]
      (println "drag"))
    (mouseReleased [e]
      (println "release"))))

(defn create-files-grid []
  (let [grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g)))
        resize-listener (create-resize-proxy #(layout! grid-panel))
        mouse-listener (create-multiselect-mouse-listener)]
    (doseq [{panel :panel} @items] 
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

(defn create-main-frame []
  (let [frame (JFrame.)
        grid-panel (create-files-grid)]
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
            its (map (fn [f] {:file f :panel (create-item-panel f)}) files)]
        (dosync
          (ref-set items its)
          (ref-set unselected-items its))
        (create-main-frame)))))

(defn -main [& args]
  (SwingUtilities/invokeLater setup))