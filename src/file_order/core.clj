(ns file-order.core
  (:import (java.awt GraphicsEnvironment BorderLayout GridLayout Color)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener ComponentAdapter InputEvent)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon JFileChooser JPanel JFrame JButton JLabel JScrollPane SwingUtilities BorderFactory)
           (javax.swing.event MouseInputAdapter)))

; constants

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

; refs

(def items (ref []))
(def last-clicked-item (ref nil))
(def drag-position (ref nil))

; functions

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

(defn set-panel-items! [grid-panel]
  (.removeAll grid-panel)
  (doseq [{panel :panel} @items] 
    (.add grid-panel panel))
  (.validate grid-panel))

(defn create-resize-proxy [f]
  (proxy [ComponentAdapter] []
    (componentResized [_]
      (f))))

(defn in-bounds? [[x y] [a b w h]]
  (and
    (and (>= x a) (<= x (+ a w)))
    (and (>= y b) (<= y (+ b h)))))

(defn find-item [x y]
  (let [convert-bounds (fn [p] [(.getX p) (.getY p) (.getWidth p) (.getHeight p)])
        item-if-in-bounds #(when (in-bounds? [x y] (convert-bounds (:panel %))) %)]
    (some item-if-in-bounds @items)))

(defn find-idx-by [k i coll]
  (let [indexed-items (map vector (iterate inc 0) coll)
        index-for-item (fn [[idx item]] (when (= (get i k) (get item k)) idx))]
    (some index-for-item indexed-items)))

(defn select-item! [item]
  (when-not (nil? item)
    (ref-set items (map #(if (= item %) (assoc % :selected? true) %) @items))
    (.setBackground (:panel item) SELECTED_BACKGROUND)))

(defn select-item-range! [start-idx end-idx]
  (let [indexed-items (map vector (iterate inc 0) @items)
        between? (fn [idx] (and (>= idx start-idx) (<= idx end-idx)))
        select-between (fn [[idx item]] (if (between? idx) (assoc item :selected? true) item))]
    (ref-set items (map select-between indexed-items)))
  (let [diff (inc (- end-idx start-idx))
        select-candidates (take diff (drop start-idx @items))
        not-selected? #(not (:selected %))
        to-select (filter not-selected? select-candidates)]
    (doseq [{panel :panel} to-select] 
      (.setBackground panel SELECTED_BACKGROUND))))

(defn unselect-item! [item]
  (when-not (nil? item)
    (ref-set items (map #(if (= item %) (assoc % :selected? false) %) @items))
    (.setBackground (:panel item) UNSELECTED_BACKGROUND)))

(defn unselect-all-items! []
  (doseq [{panel :panel} @items] 
    (.setBackground panel UNSELECTED_BACKGROUND))
  (ref-set items (map #(assoc % :selected? false) @items)))

(defn key-pressed? [key-mask e]
  (= key-mask (bit-and (.getModifiersEx e) key-mask)))

(def ctrl-pressed?  (partial key-pressed? InputEvent/CTRL_DOWN_MASK))
(def shift-pressed? (partial key-pressed? InputEvent/SHIFT_DOWN_MASK))

(defn modifiers [e]
  (cond
    (ctrl-pressed? e)  :ctrl
    (shift-pressed? e) :shift))

(defmulti  select-clicked! (fn [e _] (modifiers e)))

(defmethod select-clicked! :ctrl [_ item]
  (let [alter-fn (if (:selected? item) unselect-item! select-item!)] 
    (dosync 
      (alter-fn item))))

(defmethod select-clicked! :shift [_ item]
  (when-not (nil? @last-clicked-item)
    (let [last-item @last-clicked-item
          idxs [(find-idx-by :file item      @items) 
                (find-idx-by :file last-item @items)]]
      (dosync 
        (apply select-item-range! (sort idxs))))))

(defmethod select-clicked! :default [_ item]
  (when-not (:selected? item)
    (dosync 
      (unselect-all-items!)
      (select-item! item))))

(defn update-drag-position! [x y]
  (let [unselected-items (filter (complement :selected?) @items)
        pairs (partition 2 1 (concat [:before-first] unselected-items [:after-last]))
        coords (fn [p] [(.getX p) (.getY p)])
        located-before? (fn [[a b]] (or (> (- y b) ITEM_HEIGHT)
                                        (< a x)))
        located-after? #(not (located-before? %))
        between (fn [[a b]] (when (and (or (keyword? a) (located-before? (coords (:panel a))))
                                       (or (keyword? b) (located-after? (coords (:panel b))))) 
                                  a))
        item (some between pairs)]
    (dosync
      (ref-set drag-position item))))

(defn reorder-items! []
  (dosync
    (let [selected-map (group-by :selected? @items)
          selected-items (get selected-map true)
          unselected-items (get selected-map false)
          idx (if (= :before-first @drag-position) 
                  0 
                  (inc (find-idx-by :file @drag-position unselected-items)))
          parts (split-at idx unselected-items)
          new-items (concat (first parts) selected-items (second parts))]
      (ref-set items new-items)
      (ref-set drag-position nil))))

(defn create-multiselect-mouse-listener [grid-panel]
  (proxy [MouseInputAdapter] []
    (mousePressed [e]
      (let [item (find-item (.getX e) (.getY e))]
        (if (nil? item) 
          (dosync 
            (unselect-all-items!))
          (do
            (select-clicked! e item)
            (dosync (ref-set last-clicked-item item))))))
    (mouseDragged [e]
      (update-drag-position! (.getX e) (.getY e))
      (.repaint grid-panel))
    (mouseReleased [_]
      (when-not (nil? @drag-position)
        (reorder-items!)
        (set-panel-items! grid-panel)
        (.repaint grid-panel)))))

(defn paint-drag-position! [g]
  (when-not (nil? @drag-position)
    (.setColor g INSERT_MARKER)
    (if (= :before-first @drag-position)
      (let [p (:panel (first @items))
            w 2
            h (.getHeight p)
            x (- (.getX p) (/ (+ ITEM_BORDER w) 2))
            y (.getY p)]
        (.fillRect g x y w h))
      (let [p (:panel @drag-position)
            w 2
            h (.getHeight p)
            x (+ (.getX p) (.getWidth p) (/ (- ITEM_BORDER w) 2))
            y (.getY p)]
        (.fillRect g x y w h)))))

(defn create-files-grid []
  (let [grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g)
                      (paint-drag-position! g)))
        resize-listener (create-resize-proxy #(layout! grid-panel))
        mouse-listener (create-multiselect-mouse-listener grid-panel)]
    (set-panel-items! grid-panel)
    (doto grid-panel
      (.setSize 800 600)
      (.setBorder (BorderFactory/createEmptyBorder ITEM_BORDER ITEM_BORDER ITEM_BORDER ITEM_BORDER))
      (.addComponentListener resize-listener)
      (.addMouseListener mouse-listener)
      (.addMouseMotionListener mouse-listener))))

(defn order-list! [& args]
  (println "Order!"))

(defn create-button [name action-fn]
  (let [button (JButton. name)
        action-proxy (proxy [ActionListener] []
          (actionPerformed [_]
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
      (.add (create-button "Order!" order-list!) BorderLayout/SOUTH)
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

(defstruct item-struct :file :panel :selected?)

(defn create-item-struct [f]
  (struct-map item-struct
    :file f
    :panel (create-item-panel f)
    :selected? false))

(defn setup! []
  (let [dir (choose-directory)]
    (if (not (nil? dir)) 
      (let [files (load-files dir)
            its (map create-item-struct files)]
        (dosync
          (ref-set items its))
        (create-main-frame)))))

(defn -main [& args]
  (SwingUtilities/invokeLater setup!))