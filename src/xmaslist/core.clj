(ns xmaslist.core
  (:gen-class)
  (:require [clojure.string :as string]
            [hiccup.page :refer [xhtml]]
            [hiccup.util :refer [escape-html]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [not-found]]
            [ring.handler.dump :refer [handle-dump]]
            [ring.middleware.session :refer [wrap-session]]
            [xmaslist.database :as db]))

(defn make-page [title navigation body]
  (xhtml 
   (list
    [:head
     [:link {:rel "stylesheet"
             :type "text/css"
             :href "/xmaslist.css"}]
     [:title#title (str "Kerstlijst: " title)]]
    [:body
     [:div#non-footer
      [:div#content
       [:div#header
        [:div#header-rightwrap
         [:div#header-left]]]
       [:div#hoofding [:h1 title]]
       [:div#navigation-bar navigation]
       [:div#main body]]]
     [:div#footer
      [:div#footer-rightwrap
       [:div#footer-left]]]])))

(defn format-score [score]
  (list
   (repeat score [:img {:src "/images/vollester.png"}])
   (repeat (- 5 score) [:img {:src "/images/legester.png"}])))

(defn format-price [price]
  (if (string/blank? price)
    ""
    (str (escape-html (string/trim price)) " &euro;")))

(defn format-item [item actions]
  (list
   [:tr.item
    [:td.descr (escape-html (:descr item))]
    [:td.score (format-score (:score item))]
    [:td.price (format-price (:price item))]
    [:td.actions actions]]
   [:tr [:td.comment {:colspan "4"}
         (escape-html (:comment item))]]))

(defn login-content [msg]
  (make-page
   "Kerstlijst login" '()
   (list 
    (if msg
      [:p.error msg]
      '())
    [:form
     {:action "/login" :method "post"}
     [:fieldset
      [:legend "Geef naam en paswoord"]
      [:div.form-item
       [:label {:for "name"} "naam"]
       [:input.text {:type "text" :name "name"}]]
      [:div.form-item
       [:label {:for "password"} "paswoord"]
       [:input.text {:type "password" :name "password"}]]
      [:input.submit {:type "submit" :value "inloggen"}]]])))

(defn login-page [request]
  {:status 200
   :body (login-content false)})

(defn login [request]
  (let [params (:form-params request)
        user (params "name")]
    (if (db/authenticate-user user (params "password"))
      {:status 303
       :session (assoc (:session request) :username user)
       :headers {"Location" "/my-list"}}
      {:status 200
       :body (login-content (str "Naam of paswoord onjuist."))})))

(defn with-user [page]
  (fn [request]
    (if-let [user (:username (:session request))]
      (page user request)
      {:status 303
       :headers {"Location" "/login"}})))

(defn navigation [page me]
  (defn link-or-string [sym str cont]
    (if (= page sym) str
        [:a {:href cont} (escape-html str)]))
  (list
   [:h3 (link-or-string 'my-list "Mijn lijstje" "/my-list")]
   [:h3 "Andere lijsten"]
   [:ul (map
         (fn [user]
           [:li (link-or-string
                 user user 
                 (str "/other-list?user=" user))])
         (db/other-users me))]
   [:h3 (link-or-string 'change-password
                        "Paswoord veranderen"
                        "/change-password")]))


(defn format-mylist-items [me]
  [:table
   (map (fn [item]
          (format-item item
                       [:a {:href  (str "/remove?item=" (:id  item))}
                        "verwijderen"]))
        (db/get-wishlist me))])

(def add-item-form
  [:form {:action "/add" :method "post"}
   [:fieldset
    [:legend "Voeg een nieuw voorwerp toe"]
    [:div.form-item
     [:label {:for "description"} "Omschrijving"]
     [:input.text {:type "text" :name "description"
                   :size "40"}]]
    [:div.form-item
     [:label {:for "score"} "score"]
     [:input {:type "radio" :name "score"
              :value "1" :checked "checked"}]
     "*"
     [:input {:type "radio" :name "score"
              :value "2"}]
     "**"
     [:input {:type "radio" :name "score"
              :value "3"}]
     "***"
     [:input {:type "radio" :name "score"
              :value "4"}]
     "****"
     [:input {:type "radio" :name "score"
              :value "5"}]
     "*****"]
    [:div.form-item
     [:label {:for "price"} "Prijs: "]
     [:input.text {:type "text" :name "price" :size "5"}]
     " &euro;"]
    [:div.form-item
     [:label {:for "comment"} "commentaar"]
     [:textarea {:name "comment" :rows "6" :cols "50"}]]
    [:input {:type "submit" :name "submit" :value "voeg toe"}]]])

(defn get-int [str]
  (try
    (Integer. (string/trim str))
    (catch Exception e 0)))

(defn get-score [scorestr]
  (let [score (get-int scorestr)]
    (if (<= 1 score 5)
      score 1)))

(def add-item
  (with-user
    (fn [me request]
      (let [params (:form-params request)
            description (params "description")
            score (params "score")
            price (params "price")
            comment (params "comment")]
        (when (and description score price comment)
          (db/add-item {:author me
                        :descr description
                        :score (get-score score)
                        :price price
                        :claim false
                        :comment comment}))
        {:status 303
         :headers {"Location" "/my-list"}}))))

(def mylist
  (with-user
    (fn [me request]
      {:status 200 :body 
       (make-page
        (escape-html (str  "Lijstje van " me))
        (navigation 'my-list me)
        (list
         add-item-form
         (format-mylist-items me)))})))

(def remove-item
  (with-user
    (fn [me request]
      (let [itemid (get-int ((:query-params request) "item"))]
        (when (= (db/item-user itemid) me)
          (db/remove-item itemid))
        {:status 303
         :headers {"Location" "/my-list"}}))))

(defn format-otherlist-items [me ownername]
  (let [wishlist (db/get-wishlist ownername)
        unclaimed (filter #(= 0 (:claim %)) wishlist)]
    [:table
     (map (fn [item]
            (format-item
             item [:a {:href (str "/claim?item=" (:id item))}
                   "eis&nbsp;op"]))
          unclaimed)]))

(def otherlist
  (with-user
    (fn [me request]
      (let [owner ((:query-params request) "user")]
        (cond 
          (= owner me) {:status 303
                        :headers {"Location" "/mylist"}}

          (db/has-user owner)
          {:status 200 :body
           (make-page
            (str "Lijstje van " (escape-html owner))
            (navigation owner me)
            (format-otherlist-items me owner))}
          :else {:status 404
                 :body "user not found"})))))

(def claim-item
  (with-user
    (fn [me request]
      (let [itemid ((:query-params request) "item")
            item (db/get-item itemid)]
        (db/claim-item (get-int itemid))
        {:status 303
         :headers {"Location"
                   (if item 
                     (str "/other-list?user=" (:author item))
                     ("/my-list"))}}))))

(defn change-password-content [me msg]
  (make-page
   "Verander Paswoord"
   (navigation 'change-password me)
   (list
    (if msg [:p.error msg] '())
    [:form {:action "/change-password" :method "post"}
     [:fieldset
      [:legend "Geef oud en nieuw passwoord (tweemaal)"]
      [:div.form-item
       [:label {:for "oldpass"} "oud paswoord"]
       [:input.text {:type "password" :name "oldpass"}]]
      [:div.form-item
       [:label {:for "newpass"} "nieuw paswoord"]
       [:input.text {:type "password" :name "newpass"}]]
      [:div.form-item
       [:label {:for "newpass2"} "paswoord nogeens"]
       [:input.text {:type "password" :name "newpass2"}]]
      [:input.submit {:type "submit" :value "verander paswoord"}]]])))

(def change-password-page
  (with-user
    (fn [me request]
      {:status 200
       :body (change-password-content me nil)})))

(def change-password
  (with-user
    (fn [me request]
      (let [params (:form-params request)
            oldpass (params "oldpass")
            newpass (params "newpass")
            newpass2 (params "newpass2")]
        (cond (not (and oldpass newpass newpass2))
              {:status 200
               :body (change-password-content me)}

              (not (db/authenticate-user me oldpass))
              (change-password-content me "Paswoord is onjuist")

              (not= newpass newpass2)
              (change-password-content me "Paswoorden komen niet overeen")

              :else
              (do (db/change-password me newpass)
                  {:status 303
                   :headers {"Location" "/my-list" }}))))))

(defn coffee [request]
  {:status 418})

(defroutes app
  (GET "/" [] mylist)
  (GET "/login" [] login-page)
  (POST "/login" [] login)
  (POST "/add" [] add-item)
  (GET "/remove" [] remove-item)
  (GET "/claim" [] claim-item)
  (GET "/my-list" [] mylist)
  (GET "/other-list" [] otherlist)
  (GET "/change-password" [] change-password-page)
  (POST "/change-password" [] change-password)
  (GET "/coffee" [] coffee)
  (not-found "<h1>This is not the page you are looking for</h1> 
              <p>Sorry, the page you requested was not found!</p>"))

(defn -main
  "Runs the webserver"
  [port-number]
  (jetty/run-jetty (wrap-session (wrap-resource (wrap-params #'app) "static"))
     {:port (Integer. port-number)}))

(defn -dev-main
  "Runs the webserver and reloads code changes via the development profile of Leiningen"
  [port-number]
  (jetty/run-jetty (wrap-session (wrap-resource (wrap-params (wrap-reload #'app)) "static"))
                   {:port (Integer. port-number)}))
