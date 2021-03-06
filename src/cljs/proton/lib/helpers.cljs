(ns proton.lib.helpers
  (:require [clojure.string :as string :refer [split upper-case lower-case]]
            [cljs.nodejs :as node]))

(def fs (node/require "fs"))
(def path (node/require "path"))
(def user-home-dir (.normalize path (if (= (.-platform js/process) "win32") (.. js/process -env -USERPROFILE) (.. js/process -env -HOME))))

;; seperate map with overrides. 189 (underscore) kept getting resolved as '½' which we don't want.
(def char-code-override {189 "_"
                         9 "tab"})

(defn normalize-keystroke
  "Remove 'shift-' prefix from keystroke. For uppercase letter atom keystroke will be
  'shift-<capital_letter>' e.g. :S equal to 'shift-S'."
  [keystroke]
  (if (zero? (.indexOf keystroke "shift-"))
    (last keystroke)
    keystroke))

(defn generate-div [text class-name]
  (let [d (.createElement js/document "div")]
    (set! (.-textContent d) text)
    (.add (.-classList d) class-name)
    d))

(defn read-file [path]
  (.readFileSync fs path #js {:encoding "utf8"}))

(defn is-file? [path]
  (try
    (.isFile (.statSync fs path))
    (catch js/Error e
      false)))

(defn extract-keyletter-from-event [event]
  (let [key-code (.. event -originalEvent -keyCode)
        key (if (nil? (char-code-override key-code))
                (.fromCharCode js/String key-code)
                (char-code-override key-code))
        shift-key (.. event -originalEvent -shiftKey)]

      (if shift-key
        (keyword (upper-case key))
        (keyword (lower-case key)))))

(defn extract-keycode-from-event [event]
  (.. event -originalEvent -keyCode))

(defn extract-keystroke-from-event [event]
  (.keystrokeForKeyboardEvent js/atom.keymaps (.-originalEvent event)))

(defn console!
  ([s] (console! s nil))
  ([s key] (console! s key true))
  ([s key js-print]
   (let [k (if (nil? key) "proton" (str "proton:" key))
         s (str "["k"] " s)]
    (if js-print
      (.log js/console s)
      (println s)))))

(defn keybinding-row-html [keybinding]
  (let [options (nth keybinding 1)
        {:keys [category action title]} options
        is-category? ((comp not nil?) category)
        class-name (if is-category? "proton-key-category" "proton-key-action")
        value (if is-category? (str "+" category) (or title action))
        keystroke (name (key keybinding))]
      (str "<li class='flex-item'><span class='proton-key'>[" keystroke "]</span> ➜ <span class='" class-name "'>" value "</span></li>")))

(defn tree->html [tree]
  (->>
    (sort (seq (dissoc tree :category)))
    (map keybinding-row-html)
    (string/join " ")
    (conj [])
    (apply #(str "<p>Keybindings:</p><ul class='flex-container'>" % "</ul>"))))

(defn process->html [steps]
  (let [steps-html (map #(str "<tr><td class='process-step'>" (get % 0) "</td><td class='process-status'>" (get % 1) "</td></tr>") steps)]
    (str "<h2>Welcome to proton<h2><table>" (string/join " " steps-html) "</table>")))

(defn deep-merge
  "Deeply merges maps so that nested maps are combined rather than replaced.
  For example:
  (deep-merge {:foo {:bar :baz}} {:foo {:fuzz :buzz}})
  ;;=> {:foo {:bar :baz, :fuzz :buzz}}
  ;; contrast with clojure.core/merge
  (merge {:foo {:bar :baz}} {:foo {:fuzz :buzz}})
  ;;=> {:foo {:fuzz :quzz}} ; note how last value for :foo wins"
  [& vs]
  (if (every? map? vs)
    (apply merge-with deep-merge vs)
    (last vs)))

(defn deep-merge-with
  "Deeply merges like `deep-merge`, but uses `f` to produce a value from the
  conflicting values for a key in multiple maps."
  [f & vs]
  (if (every? map? vs)
    (apply merge-with (partial deep-merge-with f) vs)
    (apply f vs)))
