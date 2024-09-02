(ns chromex-scittle.popup
  (:require-macros [chromex.support :refer [runonce]])
  (:require [chromex-scittle.popup.core :as core]))

(runonce
  (core/init!))
