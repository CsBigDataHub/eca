(ns eca.config
  "Waterfall of ways to get eca config, deep merging from top to bottom:

  1. base: fixed config var `eca.config/initial-config`.
  2. env var: searching for a `ECA_CONFIG` env var which should contains a valid json config.
  3. local config-file: searching from a local `.eca/config.json` file.
  4. `initializatonOptions` sent in `initialize` request."
  (:require
   [cheshire.core :as json]
   [cheshire.factory :as json.factory]
   [clojure.core.memoize :as memoize]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [eca.shared :as shared])
  (:import
   [java.io File]))

(set! *warn-on-reflection* true)

(def initial-config
  {:openaiApiKey nil
   :anthropicApiKey nil
   :openaiApiUrl nil
   :anthropicApiUrl nil
   :githubCopilotApiUrl nil
   :ollamaApiUrl nil
   :rules []
   :commands []
   :nativeTools {:filesystem {:enabled true}
                 :shell {:enabled true
                         :excludeCommands []}
                 :editor {:enabled true}}
   :disabledTools []
   :mcpTimeoutSeconds 60
   :mcpServers {}
   :models {"openai/gpt-5" {}
            "openai/gpt-5-mini" {}
            "openai/gpt-5-nano" {}
            "openai/gpt-4.1" {}
            "openai/o4-mini" {}
            "openai/o3" {}
            "github-copilot/gpt-5-mini" {}
            "github-copilot/gpt-4.1" {}
            "github-copilot/gpt-4o" {}
            "github-copilot/claude-3.5-sonnet" {}
            "github-copilot/gemini-2.0-flash-001" {}
            "anthropic/claude-sonnet-4" {:extraPayload {:thinking {:type "enabled" :budget_tokens 2048}}}
            "anthropic/claude-opus-4.1" {:extraPayload {:thinking {:type "enabled" :budget_tokens 2048}}}
            "anthropic/claude-opus-4" {:extraPayload {:thinking {:type "enabled" :budget_tokens 2048}}}
            "anthropic/claude-3-5-haiku" {:extraPayload {:thinking {:type "enabled" :budget_tokens 2048}}}}
   :ollama {:useTools true
            :think true}
   :chat {:welcomeMessage "Welcome to ECA!\n\nType '/' for commands\n\n"}
   :agentFileRelativePath "AGENT.md"
   :customProviders {}
   :index {:ignoreFiles [{:type :gitignore}]
           :repoMap {:maxTotalEntries 800
                     :maxEntriesPerDir 50}}})

(defn get-env [env] (System/getenv env))
(defn get-property [property] (System/getProperty property))

(def ^:private ttl-cache-config-ms 5000)

(defn ^:private safe-read-json-string [raw-string]
  (try
    (binding [json.factory/*json-factory* (json.factory/make-json-factory
                                           {:allow-comments true})]
      (json/parse-string raw-string true))
    (catch Exception _
      nil)))

(defn ^:private config-from-envvar* []
  (some-> (System/getenv "ECA_CONFIG")
          (safe-read-json-string)))

(def ^:private config-from-envvar (memoize config-from-envvar*))

(defn global-config-dir ^File []
  (let [xdg-config-home (or (get-env "XDG_CONFIG_HOME")
                            (io/file (get-property "user.home") ".config"))]
    (io/file xdg-config-home "eca")))

(defn ^:private config-from-global-file* []
  (let [config-file (io/file (global-config-dir) "config.json")]
    (when (.exists config-file)
      (safe-read-json-string (slurp config-file)))))

(def ^:private config-from-global-file (memoize/ttl config-from-global-file* :ttl/threshold ttl-cache-config-ms))

(defn ^:private config-from-local-file* [roots]
  (reduce
   (fn [final-config {:keys [uri]}]
     (merge
      final-config
      (let [config-file (io/file (shared/uri->filename uri) ".eca" "config.json")]
        (when (.exists config-file)
          (safe-read-json-string (slurp config-file))))))
   {}
   roots))

(def ^:private config-from-local-file (memoize/ttl config-from-local-file* :ttl/threshold ttl-cache-config-ms))

(def initialization-config* (atom {}))

(defn ^:private deep-merge [& maps]
  (apply merge-with (fn [& args]
                      (if (every? #(or (map? %) (nil? %)) args)
                        (apply deep-merge args)
                        (last args)))
         maps))

(defn ^:private eca-version* []
  (string/trim (slurp (io/resource "ECA_VERSION"))))

(def eca-version (memoize eca-version*))

(def ollama-model-prefix "ollama/")

(defn all [db]
  (let [initialization-config @initialization-config*
        pure-config? (:pureConfig initialization-config)]
    (deep-merge initial-config
                initialization-config
                (when-not pure-config? (config-from-envvar))
                (when-not pure-config? (config-from-global-file))
                (when-not pure-config? (config-from-local-file (:workspace-folders db))))))
