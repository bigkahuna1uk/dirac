(ns dirac.implant
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [put! <! chan timeout alts! close!]]
            [devtools.toolbox :refer [envelope]]
            [devtools.preload]
            [chromex.support :refer-macros [oget oset ocall oapply]]
            [chromex.logging :refer-macros [log warn error info]]
            [cljs.repl]
            [goog.async.Debouncer]
            [dirac.utils :refer-macros [runonce]]
            [dirac.implant.editor :as editor]
            [dirac.implant.intercom :as intercom]
            [dirac.implant.automation :as automation]
            [dirac.implant.version :refer [version]]
            [dirac.implant.eval :as eval]
            [dirac.implant.feedback :as feedback]
            [dirac.implant.analyzer :as analyzer]
            [dirac.implant.munging :as munging]
            [dirac.implant.helpers :as helpers]
            [dirac.implant.reporter :as reporter]
            [dirac.implant.repl :as repl]))

(defonce ^:dynamic *console-initialized* false)
(defonce ^:dynamic *implant-initialized* false)
(defonce ^:dynamic *namespaces-cache-debouncer* nil)

; -- public API -------------------------------------------------------------------------------------------------------------
; following functions will be exposed as helpers for devtools javascript code
; they should be called via dirac.something object, see the mapping in dirac-api-to-export below

(defn post-feedback! [& args]
  (apply feedback/post! args))

(defn init-console! []
  (when-not *console-initialized*
    (assert *implant-initialized*)
    (set! *console-initialized* true)
    (intercom/init!)
    (feedback/post! "console initialized")))

(defn init-repl! []
  (assert *implant-initialized*)
  (assert *console-initialized*)
  (intercom/init-repl!)
  (feedback/post! "repl initialized"))

(defn adopt-prompt! [text-area-element use-parinfer?]
  (feedback/post! (str "adopt-prompt-element" " use-parinfer? " use-parinfer?))
  (let [editor (editor/create-editor! text-area-element :prompt use-parinfer?)]
    (editor/start-editor-sync!)
    editor))

(defn send-eval-request! [request-id code scope-info]
  (feedback/post! (str "send-eval-request: " code))
  (intercom/send-eval-request! request-id code scope-info))

(defn get-version []
  version)

(defn get-runtime-tag [callback]
  (go
    (let [tag (<! (eval/get-runtime-tag))]
      (callback tag))))

(defn parse-ns-from-source [source]
  (try
    (analyzer/parse-ns-from-source source)
    (catch :default e
      (error "Unable to parse namespace from source" (envelope source) "\n" e))))

(defn ns-to-relpath [ns ext]
  (munging/ns-to-relpath ns ext))

(defn get-function-name [munged-name]
  (if (munging/is-cljs-function-name? munged-name)
    (munging/present-function-name munged-name)
    munged-name))

(defn get-full-function-name [munged-name]
  (if (munging/is-cljs-function-name? munged-name)
    (munging/present-function-name munged-name true true)
    munged-name))

(defn get-repl-specials-async []
  (helpers/resolved-promise repl/all-specials))                                                                               ; hard-coded for now

(defn notify-panel-switch [panel]
  (let [panel-name (oget panel "name")]
    (post-feedback! (str "setCurrentPanel: " panel-name))
    (helpers/warm-up-namespace-cache!)))

(defn trigger-internal-error! []
  ; timeout is needed for testing from console
  ; see http://stackoverflow.com/a/27257742/84283
  (ocall js/window "setTimeout" helpers/throw-internal-error! 0))

(defn trigger-internal-error-in-promise! []
  (let [delayed-promise (js/Promise. #(ocall js/window "setTimeout" % 0))]
    (ocall delayed-promise "then" #(throw (ex-info "fake async error in promise" {:val %})))
    true))

(defn trigger-internal-error-as-error-log! []
  ; timeout is needed for testing from console
  ; see http://stackoverflow.com/a/27257742/84283
  (ocall js/window "setTimeout" #(error "a fake error log" 1 2 3) 0))

(defn report-namespaces-cache-cool-down! []
  (post-feedback! "namespacesCache is cool now")
  (.pause *namespaces-cache-debouncer*))

(defn get-namespaces-cache-debouncer []
  (if-not *namespaces-cache-debouncer*
    (set! *namespaces-cache-debouncer* (goog.async.Debouncer. report-namespaces-cache-cool-down! 1000)))
  *namespaces-cache-debouncer*)

(defn report-namespaces-cache-mutation! []
  (let [debouncer (get-namespaces-cache-debouncer)]
    (.fire debouncer)))

; -- dirac object augumentation ---------------------------------------------------------------------------------------------

; !!! don't forget to update externs.js when touching this !!!
(def dirac-api-to-export
  {"feedback"                       post-feedback!
   "initConsole"                    init-console!
   "initRepl"                       init-repl!
   "notifyPanelSwitch"              notify-panel-switch
   "adoptPrompt"                    adopt-prompt!
   "sendEvalRequest"                send-eval-request!
   "getVersion"                     get-version
   "getRuntimeTag"                  get-runtime-tag
   "parseNsFromSource"              parse-ns-from-source
   "nsToRelpath"                    ns-to-relpath
   "triggerInternalError"           trigger-internal-error!
   "triggerInternalErrorInPromise"  trigger-internal-error-in-promise!
   "triggerInternalErrorAsErrorLog" trigger-internal-error-as-error-log!
   "getFunctionName"                get-function-name
   "getFullFunctionName"            get-full-function-name
   "getReplSpecialsAsync"           get-repl-specials-async
   "reportNamespacesCacheMutation"  report-namespaces-cache-mutation!})

(defn enhance-dirac-object! [dirac]
  (doseq [[name fn] dirac-api-to-export]
    (oset dirac [name] fn)))

; -- init code --------------------------------------------------------------------------------------------------------------

(defn init-implant! []
  (when-not *implant-initialized*
    (set! *implant-initialized* true)
    (assert (not *console-initialized*))
    (enhance-dirac-object! (oget js/window "dirac"))                                                                          ; see front_end/dirac/dirac.js
    (reporter/install!)
    (automation/install!)
    (feedback/install!)
    (eval/start-eval-request-queue-processing-loop!)
    (feedback/post! "implant initialized")
    (info (str "Dirac implant v" (get-version) " initialized"))))

; -- intialization ----------------------------------------------------------------------------------------------------------

(runonce (init-implant!))
