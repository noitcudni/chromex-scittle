(ns chromex-scittle.content-script
  (:require-macros [chromex.support :refer [runonce]])
  (:require [chromex-scittle.content-script.core :as core]))

(runonce
  (core/init!))
