(ns xmaslist.mylist
  (:gen-class)
  (:require [clojure.string :as string]
            [hiccup.page :refer [xhtml]]
            [hiccup.util :refer [escape-html]]
            [xmaslist.database :as db]))


