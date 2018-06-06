(ns xmaslist.database
  (:require [clojure.java.jdbc :refer :all]))

(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "db/database.db"})

(defn create-db []
  (try (db-do-commands db
                       [(create-table-ddl :user
                                          [[:username :text :primary :key]
                                           [:password :text]])
                        (create-table-ddl :wishlistitem
                                          [[:id :integer :primary :key]
                                           [:author :text]
                                           [:descr :text]
                                           [:score :integer]
                                           [:price :text]
                                           [:claim :boolean]
                                           [:comment :text]])])
       (catch Exception e (println e))))

(defn get-wishlist [user]
  (query db ["select * from wishlistitem where wishlistitem.author = ?" user]))

(defn authenticate-user [user password]
  (not
   (empty? 
    (query db ["select username from user where username = ? and password = ?"
               user password]))))

(defn add-item [item]
  (insert! db :wishlistitem item))

(defn remove-item [item-id]
  (delete! db :wishlistitem ["id = ?" item-id]))

(defn get-item [item-id]
  (first (query db ["select * from wishlistitem where id = ?" item-id])))

(defn change-password [user password]
  (update! db :user {:password password} ["username = ?" user]))

(defn claim-item [item]
  (update! db :wishlistitem {:claim true}  ["id = ?" item]))

(defn other-users [user]
  (->>
   (query db ["select username from user where username != ?" user])
   (map :username)))

(defn has-user [user]
  (not (empty?
        (query db ["select username from user where username == ?" user]))))

(defn item-user [item-id]
  (->
   (query db ["select author from wishlistitem where id = ?" item-id])
   first
   :author))
