(ns file-order.core
  (:import (java.awt GraphicsEnvironment BorderLayout GridLayout Dimension Toolkit Color)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener ComponentListener InputEvent)
           (java.io File)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon JFileChooser JPanel JFrame JButton JLabel JScrollPane SwingUtilities BorderFactory)
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
(def INSERT_MARKER (Color. 100 100 255))

(def items (ref []))
(def selected-items (ref []))
(def unselected-items (ref []))
(def drag-position (ref nil))

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

(defn set-items! [grid-panel]
  (.removeAll grid-panel)
  (doseq [{panel :panel} @items] 
    (.add grid-panel panel))
  (.validate grid-panel))

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
  (let [convert-bounds (fn [p] [(.getX p) (.getY p) (.getWidth p) (.getHeight p)])]
    (first (filter #(in-bounds? [x y] (convert-bounds (:panel %))) @items))))

(defn without [i coll]
  (filter #(not (= i %)) coll))

(defn select-item [item]
  (if (not (nil? item))
    (do
      (alter selected-items conj item)
      (alter unselected-items #(without item %))
      (.setBackground (:panel item) SELECTED_BACKGROUND))))

(defn unselect-item [item]
  (if (not (nil? item))
    (do
      (alter unselected-items conj item)
      (alter selected-items #(without item %))
      (.setBackground (:panel item) UNSELECTED_BACKGROUND))))

(defn unselect-all-items []
  (doseq [{panel :panel} @selected-items] 
    (.setBackground panel UNSELECTED_BACKGROUND))
  (ref-set selected-items [])
  (ref-set unselected-items @items))

(defn key-pressed? [key-mask e]
  (= key-mask (bit-and (.getModifiersEx e) key-mask)))

(def ctrl-pressed?  (partial key-pressed? InputEvent/CTRL_DOWN_MASK))
(def shift-pressed? (partial key-pressed? InputEvent/SHIFT_DOWN_MASK))

(defn in-seq? [i coll]
  (some #(= i %) coll))

(defn find-idx [i coll]
  (let [indexed-items (map vector (iterate inc 0) coll)]
    (some #(when (= i (second %)) (first %)) indexed-items)))

(defn create-multiselect-mouse-listener []
  (proxy [MouseInputAdapter] []
    (mousePressed [e]
      (cond
        (ctrl-pressed? e)
          (let [item (find-item (.getX e) (.getY e))
                in-selected? (in-seq? item @selected-items)
                alter-fn (if in-selected? unselect-item select-item)] 
            (dosync 
              (alter-fn item)))
        (shift-pressed? e)
          (when-not (empty? @selected-items)
            (let [item (find-item (.getX e) (.getY e))
                  last-item (first @selected-items)
                  idxs [(find-idx item @items) (find-idx last-item @items)]
                  start-idx (apply min idxs)
                  end-idx (apply max idxs)
                  diff (inc (- end-idx start-idx))
                  select-candidates (take diff (drop start-idx @items))
                  to-select (filter #(not (in-seq? % @selected-items)) select-candidates)]
              (dosync 
                (doseq [i to-select]
                  (select-item i)))))
        :else 
          (dosync 
            (unselect-all-items)
            (select-item (find-item (.getX e) (.getY e))))))
    (mouseDragged [e]
      (let [x (.getX e) 
            y (.getY e)
            pairs (partition 2 1 @unselected-items)
            coords (fn [p] [(.getX p) (.getY p)])
            located-before? (fn [[a b]] (or (> (- y b) ITEM_HEIGHT)
                                           (< a x)))
            located-after? #(not (located-before? %))
            between (fn [[a b]] (when (and (located-before? (coords (:panel a))) 
                                           (located-after? (coords (:panel b)))) 
                                      [a b]))
            pair (some between pairs)]
        (dosync
          (ref-set drag-position 
            (cond 
              (located-after? (coords (:panel (first @unselected-items))))
                [:before-first (first @unselected-items)]
              (nil? pair)
                [(last @unselected-items) :after-last]
              :else
                pair)))
        (.repaint (.getParent (:panel (first @items)))) ; hack...
        ))
    (mouseReleased [e]
      (when (not (nil? @drag-position)) 
        (dosync
              (let [first-item (first @drag-position)
                    idx (if (= :before-first first-item) 0 (inc (find-idx first-item @unselected-items)))
                    parts (split-at idx @unselected-items)
                    new-items (concat (first parts) @selected-items (second parts))]
                (ref-set items new-items)
                (ref-set drag-position nil)))
        (let [grid-panel (.getParent (:panel (first @items)))]
          (set-items! grid-panel)
          (.repaint grid-panel)) ; hack...
        ))))

(defn create-files-grid []
  (let [grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g)
                      (cond
                        (nil? @drag-position)
                          nil
                        (= :before-first (first @drag-position))
                          (let [p (:panel (first @items))
                                w 2
                                h (.getHeight p)
                                x (- (.getX p) (/ (+ ITEM_BORDER w) 2))
                                y (.getY p)]
                            (doto g
                              (.setColor INSERT_MARKER)
                              (.fillRect x y w h)))
                        :else
                          (let [p (:panel (first @drag-position))
                                w 2
                                h (.getHeight p)
                                x (+ (.getX p) (.getWidth p) (/ (- ITEM_BORDER w) 2))
                                y (.getY p)]
                            (doto g
                              (.setColor INSERT_MARKER)
                              (.fillRect x y w h))))))
        resize-listener (create-resize-proxy #(layout! grid-panel))
        mouse-listener (create-multiselect-mouse-listener)]
    (set-items! grid-panel)
    (doto grid-panel
      (.setSize 800 600)
      (.setBorder (BorderFactory/createEmptyBorder ITEM_BORDER ITEM_BORDER ITEM_BORDER ITEM_BORDER))
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