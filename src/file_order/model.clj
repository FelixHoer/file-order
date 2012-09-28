(ns file-order.model)

; structs

(defstruct item-struct :name :file :panel :selected?)

; refs

(def items (ref []))
(def items-by-name (ref {}))
(def last-clicked-item (ref nil))
(def drag-position (ref nil))

; functions

(defn =name [a b]
  (= (:name a) (:name b)))

(defn find-idx [i coll]
  (let [indexed-items (map vector (iterate inc 0) coll)
        index-for-item (fn [[idx item]] (when (=name i item) idx))]
    (some index-for-item indexed-items)))

(defn set-item-selected! [item sel?]
  (ref-set items (map #(if (=name item %) (assoc % :selected? sel?) %) @items))
  item)

(defn set-item-range-selected! [start-idx end-idx sel?]
  (let [indexed-items (map vector (iterate inc 0) @items)
        between? (fn [idx] (and (>= idx start-idx) (<= idx end-idx)))
        between-items (map second (filter (comp between? first) indexed-items))
        select-between (fn [[idx item]] (if (between? idx) (assoc item :selected? sel?) item))
        new-items (map select-between indexed-items)]
    (ref-set items new-items)
    between-items))

(defn set-last-to-item-selected! [item sel?]
  (let [its @items
        last-item @last-clicked-item
        idxs [(find-idx item      its) 
              (find-idx last-item its)]
        start-idx (apply min idxs)
        end-idx (apply max idxs)]
    (set-item-range-selected! start-idx end-idx sel?)))

(defn set-all-items-selected! [sel?]
  (let [its (map #(assoc % :selected? sel?) @items)]
    (ref-set items its)
    its))

(defn reorder-items! []
  (let [anchor @drag-position
        selected-map (group-by :selected? @items)
        selected-items (get selected-map true)
        unselected-items (get selected-map false)
        idx (if (= :before-first anchor) 
                0 
                (inc (find-idx anchor unselected-items)))
        [before-items after-items] (split-at idx unselected-items)
        new-items (concat before-items selected-items after-items)]
    (ref-set items new-items)
    (ref-set drag-position nil)))

(defn get-item-by-name [n]
  (get @items-by-name n))

(defn get-items []
  @items)

(defn set-items! [its]
  (ref-set items its)
  (ref-set items-by-name (into {} (map (fn [i] [(:name i) i]) its)))
  its)

(defn get-drag-position []
  @drag-position)

(defn set-drag-position! [item]
  (ref-set drag-position item)
  item)

(defn get-last-clicked []
  @last-clicked-item)

(defn set-last-clicked! [item]
  (ref-set last-clicked-item item)
  item)