(ns chromex-scittle.background
  (:require-macros [chromex.support :refer [runonce]])
  (:require [chromex-scittle.background.core :as core]
            [goog.object :as gobject]
            [sci.core :as sci]
            [sci.ctx-store :as store]
            [sci.impl.unrestrict]
            [chromex-scittle.background.error :as error] ;; [scittle.impl.common :as common]
            [clojure.edn :as edn]
            [chromex.ext.runtime :as runtime]
            [lambdaisland.fetch :as fetch]
            [kitchen-async.promise :as p]
            [sci.nrepl.server :as nrepl-server]
            ))

;; equivalent of scittle.impl.common.cljns
(def cljns (sci/create-ns 'clojure.core nil)) ;; [scittle.impl.common :refer [cljns]]


(set! sci.impl.unrestrict/*unrestricted* true)


(clojure.core/defmacro time
  "Evaluates expr and prints the time it took. Returns the value of expr."
  [expr]
  `(let [start# (cljs.core/system-time)
         ret# ~expr]
     (prn (cljs.core/str "Elapsed time: "
                         (.toFixed (- (system-time) start#) 6)
                         " msecs"))
     ret#))

(def stns (sci/create-ns 'sci.script-tag nil))
(def rns (sci/create-ns 'cljs.reader nil))

(def namespaces
  {'clojure.core
   {'time (sci/copy-var time cljns)
    'system-time (sci/copy-var system-time cljns)
    'random-uuid (sci/copy-var random-uuid cljns)
    ;; 'read-string (sci/copy-var read-string cljns)
    'update-keys (sci/copy-var update-keys cljns)
    'update-vals (sci/copy-var update-vals cljns)
    'parse-boolean (sci/copy-var cljs.core/parse-boolean cljns)
    'parse-double (sci/copy-var parse-double cljns)
    'parse-long (sci/copy-var parse-long cljns)
    'parse-uuid (sci/copy-var parse-uuid cljns)
    ;; 'NaN? (sci/copy-var js/NaN? cljns)
    'infinite? (sci/copy-var infinite? cljns)
    'iteration (sci/copy-var iteration cljns)
    'abs (sci/copy-var abs cljns)
    }
   'goog.object {'set gobject/set
                 'get gobject/get}
   'sci.core {'stacktrace sci/stacktrace
              'format-stacktrace sci/format-stacktrace}
   })

(store/reset-ctx!
  (sci/init {:namespaces namespaces
             :classes {'js js/globalThis
                       :allow :all
                       'Math js/Math}
             :ns-aliases {'clojure.pprint 'cljs.pprint}
             :features #{:scittle :cljs}}))

(def !last-ns (volatile! @sci/ns))

(defn- -eval-string [s]
  (sci/binding [sci/ns @!last-ns]
    (let [rdr (sci/reader s)]
      (loop [res nil]
        (let [form (sci/parse-next (store/get-ctx) rdr)]
          (if (= :sci.core/eof form)
            (do
              (vreset! !last-ns @sci/ns)
              res)
            (recur (sci/eval-form (store/get-ctx) form))))))))

(defn ^:export eval-string [s]
  (try (-eval-string s)
       (catch :default e
         (error/error-handler e (:src (store/get-ctx)))
         (throw e))))

(defn register-plugin! [_plug-in-name sci-opts]
  (store/swap-ctx! sci/merge-opts sci-opts))


(defn ^:export eval-script-srcs [script-srcs]
  (when-let [src (first script-srcs)]
    (p/let [url (runtime/get-url src)
            resp (fetch/get url)]
      (store/swap-ctx! assoc-in [:src src] resp)
      (sci/binding [sci/file src]
        (eval-string resp))
      (eval-script-srcs (rest script-srcs)))

    ;; (let [req (js/XMLHttpRequest.) ;;XMLHttpRequest is not defined in background
    ;;       _ (.open req "GET" src true)
    ;;       _ (gobject/set req "onload"
    ;;                      (fn [] (this-as this
    ;;                               (let [response (gobject/get this "response")]
    ;;                                 ;; (gobject/set tag "scittle_id" src)
    ;;                                 ;; save source for error messages
    ;;                                 (store/swap-ctx! assoc-in [:src src] response)
    ;;                                 (sci/binding [sci/file src]
    ;;                                   (eval-string response)))
    ;;                               (eval-script-srcs (rest script-srcs)))))]
    ;;   (.send req))
    ))




(enable-console-print!)
(sci/alter-var-root sci/print-fn (constantly *print-fn*))
;; (eval-script-srcs ["./test.cljs"]) ;; hack to see if it even works

(set! js/window js/self)

;;; scittle.nrepl
(defn ws-url [host port path]
  (str "ws://" host ":" port "/" path))

(when-let [ws-port 1340]
  (set! (.-ws_nrepl js/window)
        ;; NOTE: hardcode the host name
        (new js/WebSocket (ws-url "localhost" ws-port "_nrepl"))))

(when-let [ws (nrepl-server/nrepl-websocket)]
  (prn :ws ws)
  (set! (.-onmessage ws)
        (fn [event]
          (nrepl-server/handle-nrepl-message (edn/read-string (.-data event)))))
  (set! (.-onerror ws)
        (fn [event]
          (js/console.log event))))

;; (when-let [ws-port 1340]
;;   (let [ws (new js/WebSocket (ws-url "localhost" ws-port "_nrepl"))
;;         _ (prn :ws ws)
;;         ]
;;     (set! (.-onopen ws)
;;           (fn [event]
;;             (prn "nrepl websocket opened")))
;;     (set! (.-onmessage ws)
;;           (fn [event]
;;             (let [_ (prn "onmessage" event)
;;                   msg (edn/read-string (.-data event))
;;                   op-msg (prn (:op msg))

;;                   ]
;;               (nrepl-server/handle-nrepl-message msg))))
;;     (set! (.-onerror ws)
;;           (fn [event]
;;             (prn "onerror" event)
;;             (js/console.log event)))
;;     ))


;; (runonce
;;   (core/init!))
