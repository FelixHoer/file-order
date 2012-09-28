(ns file-order.gui
  (:require [file-order.model         :as model]
            [file-order.file          :as file]
            [file-order.preview.image :as image])
  (:use [file-order.constants])
  (:import (java.awt BorderLayout Dimension)
           (java.awt.event ActionListener ComponentAdapter InputEvent)
           (javax.swing ImageIcon BorderFactory JButton JFrame JFileChooser 
                        JLabel JScrollPane JPanel SwingUtilities)
           (javax.swing.event MouseInputAdapter)))

; functions

(defn in-bounds? [[x y] [a b w h]]
  (and (>= x a) (<= x (+ a w))
       (>= y b) (<= y (+ b h))))

(defn find-item [x y]
  (let [convert-bounds (fn [p] [(.getX p) (.getY p) (.getWidth p) (.getHeight p)])
        item-if-in-bounds #(when (in-bounds? [x y] (convert-bounds (:panel %))) %)]
    (some item-if-in-bounds (model/get-items))))

(defn create-item-label [n]
  (let [ext-type (file/extension-type n)
        icon (get EXTENSION_TYPE_ICON ext-type)]
    (doto (JLabel. n (ImageIcon. icon) JLabel/CENTER)
      (.setVerticalTextPosition JLabel/BOTTOM)
      (.setHorizontalTextPosition JLabel/CENTER))))

(defn create-item-panel [n]
  (doto (JPanel.)
    (.setOpaque true)
    (.setBackground UNSELECTED_BACKGROUND)
    (.setLayout (BorderLayout.))
    (.add (create-item-label n) BorderLayout/CENTER)))

(defn layout! [panel]
  (let [width (.getWidth panel)
        indexed-items (map vector (model/get-items) (iterate inc 0))
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

(defn select-item! [item]
  (when-not (nil? item)
    (model/set-item-selected! item true)
    (.setBackground (:panel item) SELECTED_BACKGROUND)))

(defn unselect-item! [item]
  (when-not (nil? item)
    (model/set-item-selected! item false)
    (.setBackground (:panel item) UNSELECTED_BACKGROUND)))

(defn select-item-range! [item]
  (let [to-select (model/set-last-to-item-selected! item true)]
    (doseq [{panel :panel} to-select]
      (.setBackground panel SELECTED_BACKGROUND))))

(defn unselect-all-items! []
  (let [unselected-items (model/set-all-items-selected! false)]
    (doseq [{panel :panel} unselected-items] 
      (.setBackground panel UNSELECTED_BACKGROUND))))

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
  (when-not (nil? item)
    (let [alter-fn (if (:selected? item) unselect-item! select-item!)] 
      (dosync 
        (alter-fn item)))))

(defmethod select-clicked! :shift [_ item]
  (when-not (nil? (model/get-last-clicked))
    (dosync 
      (select-item-range! item))))

(defmethod select-clicked! :default [_ item]
  (when-not (:selected? item)
    (dosync
      (unselect-all-items!)
      (select-item! item))))

(defn update-drag-position! [x y]
  (let [unselected-items (filter (complement :selected?) (model/get-items))
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
      (model/set-drag-position! item))))

(defn update-last-clicked! [item]
  (when-let [last-clicked (model/get-last-clicked)]
    (.setBorder (:panel last-clicked) (BorderFactory/createEmptyBorder 1 1 1 1)))
  (.setBorder (:panel item) (BorderFactory/createLineBorder LAST_CLICKED_BORDER))
  (dosync 
    (model/set-last-clicked! item)))

(defn create-multiselect-mouse-listener [grid-panel]
  (proxy [MouseInputAdapter] []
    (mousePressed [e]
      (if-let [item (find-item (.getX e) (.getY e))]
        (do
          (select-clicked! e item)
          (update-last-clicked! item))
        (dosync 
          (unselect-all-items!))))
    (mouseDragged [e]
      (update-drag-position! (.getX e) (.getY e))
      (.repaint grid-panel))
    (mouseReleased [_]
      (when-not (nil? (model/get-drag-position))
        (dosync 
          (model/reorder-items!))
        (layout! grid-panel)))))

(defn paint-drag-position! [g]
  (when-let [drag-position (model/get-drag-position)]
    (.setColor g INSERT_MARKER)
    (if (= :before-first drag-position)
      (let [p (:panel (first (model/get-items)))
            w 2
            h (.getHeight p)
            x (- (.getX p) (/ (+ ITEM_BORDER w) 2))
            y (.getY p)]
        (.fillRect g x y w h))
      (let [p (:panel drag-position)
            w 2
            h (.getHeight p)
            x (+ (.getX p) (.getWidth p) (/ (- ITEM_BORDER w) 2))
            y (.getY p)]
        (.fillRect g x y w h)))))

(defn calculate-preferred-size [width]
  (let [cols (max 1 (quot width ITEM_BORDER_WIDTH))
        rows (inc (quot (dec (count (model/get-items))) cols))]
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
    (doseq [{panel :panel} (model/get-items)] 
      (.add grid-panel panel))
    (doto grid-panel
      (.setSize 800 600)
      (.setLayout nil)
      (.addComponentListener resize-listener)
      (.addMouseListener mouse-listener)
      (.addMouseMotionListener mouse-listener))))

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
      (.setTitle TITLE)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setLayout (BorderLayout.))
      (.add (create-vertical-scrollpane grid-panel) BorderLayout/CENTER)
      (.add (create-button ORDER_BUTTON_TEXT file/order-files!) BorderLayout/SOUTH)
      (.addComponentListener (create-resize-proxy 
        #(.setSize grid-panel (.getWidth frame) (.getHeight grid-panel))))
      (.pack)
      (.setVisible true))))

(defn choose-directory []
  (let [chooser (JFileChooser.)]
    (.setDialogTitle chooser FILE_CHOOSER_TEXT)
    (.setFileSelectionMode chooser JFileChooser/DIRECTORIES_ONLY)
    (if (= (.showOpenDialog chooser nil) JFileChooser/APPROVE_OPTION)
      (.getSelectedFile chooser))))

(defn set-image-preview! [{p :panel} thumb]
  (let [label (.getComponent p 0)]
      (.setIcon label (ImageIcon. thumb))))

(defn set-image-previews-async! []
  (.start (Thread. 
    #(doseq [{item :item thumb :thumb} (image/create-thumbnail-seq)]
      (SwingUtilities/invokeLater (fn [] (set-image-preview! item thumb)))))))