(ns dirac.automation.devtools
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [dirac.settings :refer [get-browser-tests-dirac-agent-port]])
  (:require [cljs.core.async :refer [put! <! chan timeout alts! close!]]
            [chromex.support :refer-macros [oget oset ocall oapply]]
            [chromex.logging :refer-macros [log warn error info]]
            [devtools.core :as devtools]
            [devtools.prefs :as devtools-prefs]))

(defn init-devtools! [& [config]]
  (when-let [devtools-prefs (:devtools-prefs config)]                                                                         ; override devtools prefs
    (log "devtools override: set prefs " devtools-prefs)
    (devtools-prefs/merge-prefs! devtools-prefs))
  (if-not (:do-not-install-devtools config)                                                                                   ; override devtools features/installation
    (let [features-to-enable (cond-> []
                               (not (:do-not-enable-custom-formatters config)) (conj :custom-formatters)
                               (not (:do-not-enable-sanity-hints config)) (conj :sanity-hints))]
      (devtools/install! features-to-enable))
    (log "devtools override: do not install")))