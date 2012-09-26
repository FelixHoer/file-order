(ns file-order.core
  (:import (java.awt BorderLayout Color Dimension GraphicsEnvironment GridLayout)
           (java.awt.geom AffineTransform)
           (java.awt.event ActionListener ComponentAdapter InputEvent)
           (java.io File)
           (javax.imageio ImageIO)
           (javax.swing ImageIcon BorderFactory JButton JFrame JFileChooser JLabel JScrollPane JPanel SwingUtilities)
           (javax.swing.event MouseInputAdapter)))

; constants

(def IMAGE_HEIGHT 128)
(def IMAGE_WIDTH  128)

(def ITEM_HEIGHT (+ 30 IMAGE_HEIGHT))
(def ITEM_WIDTH  IMAGE_WIDTH)

(def ITEM_BORDER 10)
(def OUTER_BORDER 10)

(def ITEM_BORDER_HEIGHT (+ ITEM_BORDER ITEM_HEIGHT))
(def ITEM_BORDER_WIDTH  (+ ITEM_BORDER ITEM_WIDTH))

(def SELECTED_BACKGROUND (Color. 120 120 220))
(def UNSELECTED_BACKGROUND (Color. 220 220 220))
(def INSERT_MARKER (Color. 100 100 255))

(def FILE_PREFIX "fo")

(def EXTENSION_MAP {
  :image (set (ImageIO/getReaderFormatNames))
  :audio #{"aac" "mp3" "ogg" "wav"}
  :video #{"avi" "flv" "mov" "mp4" "mpeg" "mpg" "wmv"}})

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

(defn load-files [dir]
  (filter #(.isFile %) (seq (.listFiles dir))))

(defn create-icon-label [n icon]
  (doto (JLabel. n icon JLabel/CENTER)
    (.setVerticalTextPosition JLabel/BOTTOM)
    (.setHorizontalTextPosition JLabel/CENTER)))

(defn extension-type [n _]
  (let [extension (second (re-find #"^.+\.(.+)$" n))]
    (when-not (nil? extension)
      (let [lower-extension (.toLowerCase extension)]
        (some (fn [[type exts]] (when (contains? exts lower-extension) type)) EXTENSION_MAP)))))

(defmulti create-item-label extension-type)

(defmethod create-item-label :image [n f]
  (create-icon-label n (ImageIcon. (create-thumbnail f))))

(defmethod create-item-label :audio [n _]
  (create-icon-label n (ImageIcon. (clojure.java.io/resource "icons/audio-x-generic.png"))))

(defmethod create-item-label :video [n _]
  (create-icon-label n (ImageIcon. (clojure.java.io/resource "icons/video-x-generic.png"))))

(defmethod create-item-label :default [n _]
  (create-icon-label n (ImageIcon. (clojure.java.io/resource "icons/text-x-generic.png"))))

(defn create-item-panel [n f]
  (doto (JPanel.)
    (.setOpaque true)
    (.setBackground UNSELECTED_BACKGROUND)
    (.setLayout (BorderLayout.))
    (.add (create-item-label n f) BorderLayout/CENTER)))

(defn layout! [panel]
  (let [width (.getWidth panel)
        indexed-items (map vector @items (iterate inc 0))
        cols (max 1 (quot width ITEM_BORDER_WIDTH))]
    (doseq [[{p :panel} i] indexed-items]
      (.setBounds p 
        (+ OUTER_BORDER (* ITEM_BORDER_WIDTH (mod i cols)))
        (+ OUTER_BORDER (* ITEM_BORDER_HEIGHT (quot i cols)))
        ITEM_WIDTH 
        ITEM_HEIGHT))
    (.repaint panel)))

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
          idxs [(find-idx-by :name item      @items) 
                (find-idx-by :name last-item @items)]]
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
                  (inc (find-idx-by :name @drag-position unselected-items)))
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
        (layout! grid-panel)))))

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

(defn calculate-preferred-size [width]
  (let [cols (max 1 (quot width ITEM_BORDER_WIDTH))
        rows (inc (quot (dec (count @items)) cols))]
    (Dimension. 
      (+ (* ITEM_BORDER_WIDTH  cols) (* 2 OUTER_BORDER)) 
      (+ (* ITEM_BORDER_HEIGHT rows) (* 2 OUTER_BORDER)))))

(defn create-files-grid []
  (let [grid-panel (proxy [JPanel] []
                    (paintComponent [g]
                      (proxy-super paintComponent g)
                      (paint-drag-position! g))
                    (getPreferredSize []
                      (calculate-preferred-size (.getWidth this))))
        resize-listener (create-resize-proxy #(layout! grid-panel))
        mouse-listener (create-multiselect-mouse-listener grid-panel)]
    (doseq [{panel :panel} @items] 
      (.add grid-panel panel))
    (doto grid-panel
      (.setSize 800 600)
      (.setLayout nil)
      (.addComponentListener resize-listener)
      (.addMouseListener mouse-listener)
      (.addMouseMotionListener mouse-listener))))

(defn format-file [{f :file n :name} idx len]
  (let [padding-len (inc (int (Math/log10 len)))
        padded-idx (with-out-str (printf (str "%" 0 padding-len "d") idx))
        parent (.getParent f)
        separator File/separator]
    (File. (str parent separator FILE_PREFIX padded-idx "_" n))))

(defn order-files! []
  (let [indexed-items (map vector (iterate inc 0) @items)
        len (count indexed-items)
        transfer-desc (fn [[idx item]] 
                        {:from (:file item) 
                         :to (format-file item idx len)
                         :item item})
        transfers (map transfer-desc indexed-items)]
    (doseq [{from :from to :to} transfers] 
      (.renameTo from to))
    (dosync
      (ref-set items (map (fn [{item :item to :to}] (assoc item :file to)) transfers)))))

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
      (.add (create-button "Order!" order-files!) BorderLayout/SOUTH)
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

(defstruct item-struct :name :file :panel :selected?)

(defn strip-prefix [f]
  (let [n (.getName f)
        found (re-find (re-pattern (str FILE_PREFIX "\\d+" "_" "(.+)")) n)]
    (if (nil? found) n (second found))))

(defn create-item-struct [f]
  (let [n (strip-prefix f)
        p (create-item-panel n f)]
    (struct-map item-struct :name n :file f :panel p :selected? false)))

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