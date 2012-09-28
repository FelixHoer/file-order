(ns file-order.constants
  (:import (java.awt Color)
           (javax.imageio ImageIO)))

; gui

(def IMAGE_HEIGHT 128)
(def IMAGE_WIDTH  128)

(def ITEM_HEIGHT (+ 30 IMAGE_HEIGHT))
(def ITEM_WIDTH  (+ 10 IMAGE_WIDTH))

(def ITEM_BORDER 10)
(def OUTER_BORDER 10)

(def ITEM_BORDER_HEIGHT (+ ITEM_BORDER ITEM_HEIGHT))
(def ITEM_BORDER_WIDTH  (+ ITEM_BORDER ITEM_WIDTH))

(def SELECTED_BACKGROUND (Color. 175 210 255))
(def UNSELECTED_BACKGROUND (Color. 220 220 220))
(def INSERT_MARKER (Color. 65 140 255))
(def LAST_CLICKED_BORDER (Color. 150 150 150))

(def TITLE "file-order")
(def ORDER_BUTTON_TEXT "Order!")
(def FILE_CHOOSER_TEXT "Select a Directory")

; file

(def FILE_PREFIX "fo")

(def EXTENSION_MAP {
  :image (set (ImageIO/getReaderFormatNames))
  :audio #{"aac" "mp3" "ogg" "wav"}
  :video #{"avi" "flv" "mov" "mp4" "mpeg" "mpg" "wmv"}})