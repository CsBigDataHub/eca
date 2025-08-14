(ns integration.chat.ollama-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [integration.eca :as eca]
   [integration.fixture :as fixture]
   [integration.helper :refer [match-content] :as h]
   [llm-mock.mocks :as llm.mocks]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(eca/clean-after-test)

(deftest simple-text
  (eca/start-process!)

  (is (match?
       {:models (m/embeds ["ollama/qwen3"])}
       (eca/request! (fixture/initialize-request {:initializationOptions (merge fixture/default-init-options
                                                                                {:ollamaApiUrl (str fixture/base-llm-mock-url "/ollama")})
                                                  :capabilities {:codeAssistant {:chat {}}}}))))
  (eca/notify! (fixture/initialized-notification))
  (let [chat-id* (atom nil)]
    (testing "We send a simple hello message"
      (llm.mocks/set-case! :simple-text-0)
      (let [req-id 0
            resp (eca/request! (fixture/chat-prompt-request
                                {:request-id req-id
                                 :model "ollama/qwen3"
                                 :message "Tell me a joke!"}))
            chat-id (reset! chat-id* (:chatId resp))]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "Tell me a joke!\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Knock"})
        (match-content chat-id req-id "assistant" {:type "text" :text " knock!"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:model "qwen3"
              :messages [{:role "system" :content (m/pred string?)}
                         {:role "user" :content [{:type "text" :text "Tell me a joke!"}]}]}
             llm.mocks/*last-req-body*))))

    (testing "We reply"
      (llm.mocks/set-case! :simple-text-1)
      (let [req-id 1
            resp (eca/request! (fixture/chat-prompt-request
                                {:chat-id @chat-id*
                                 :request-id req-id
                                 :model "ollama/qwen3"
                                 :message "Who's there?"}))
            chat-id @chat-id*]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "Who's there?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Foo"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})))

    (testing "model reply again keeping context"
      (llm.mocks/set-case! :simple-text-2)
      (let [req-id 2
            resp (eca/request! (fixture/chat-prompt-request
                                {:chat-id @chat-id*
                                 :request-id req-id
                                 :model "ollama/qwen3"
                                 :message "What foo?"}))
            chat-id @chat-id*]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "What foo?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Foo"})
        (match-content chat-id req-id "assistant" {:type "text" :text " bar!"})
        (match-content chat-id req-id "assistant" {:type "text" :text "\n\n"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Ha!"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:model "qwen3"
              :messages [{:role "system" :content (m/pred string?)}
                         {:role "user" :content [{:type "text" :text "Tell me a joke!"}]}
                         {:role "assistant" :content [{:type "text" :text "Knock knock!"}]}
                         {:role "user" :content [{:type "text" :text "Who's there?"}]}
                         {:role "assistant" :content [{:type "text" :text "Foo"}]}
                         {:role "user" :content [{:type "text" :text "What foo?"}]}]}
             llm.mocks/*last-req-body*))))))

(deftest reasoning-text
  (eca/start-process!)

  (is (match?
       {:models (m/embeds ["ollama/qwen3"])}
       (eca/request! (fixture/initialize-request {:initializationOptions (merge fixture/default-init-options
                                                                                {:ollamaApiUrl (str fixture/base-llm-mock-url "/ollama")})
                                                  :capabilities {:codeAssistant {:chat {}}}}))))
  (eca/notify! (fixture/initialized-notification))
  (let [chat-id* (atom nil)]
    (testing "We send a hello message"
      (llm.mocks/set-case! :reasoning-0)
      (let [req-id 0
            resp (eca/request! (fixture/chat-prompt-request
                                {:request-id req-id
                                 :model "ollama/qwen3"
                                 :message "hello!"}))
            chat-id (reset! chat-id* (:chatId resp))]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "hello!\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "reasonStarted" :id (m/pred string?)})
        (match-content chat-id req-id "assistant" {:type "reasonText" :id (m/pred string?) :text "I should say"})
        (match-content chat-id req-id "assistant" {:type "reasonText" :id (m/pred string?) :text " hello"})
        (match-content chat-id req-id "assistant" {:type "reasonFinished" :id (m/pred string?)})
        (match-content chat-id req-id "assistant" {:type "text" :text "hello"})
        (match-content chat-id req-id "assistant" {:type "text" :text " there!"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:model "qwen3"
              :messages [{:role "system" :content (m/pred string?)}
                         {:role "user" :content [{:type "text" :text "hello!"}]}]}
             llm.mocks/*last-req-body*))))

    (testing "We reply"
      (llm.mocks/set-case! :reasoning-1)
      (let [req-id 1
            resp (eca/request! (fixture/chat-prompt-request
                                {:request-id req-id
                                 :chat-id @chat-id*
                                 :model "ollama/qwen3"
                                 :message "how are you?"}))
            chat-id @chat-id*]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "how are you?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "reasonStarted" :id (m/pred string?)})
        (match-content chat-id req-id "assistant" {:type "reasonText" :id (m/pred string?) :text "I should say"})
        (match-content chat-id req-id "assistant" {:type "reasonText" :id (m/pred string?) :text " fine"})
        (match-content chat-id req-id "assistant" {:type "reasonFinished" :id (m/pred string?)})
        (match-content chat-id req-id "assistant" {:type "text" :text "I'm "})
        (match-content chat-id req-id "assistant" {:type "text" :text " fine"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:model "qwen3"
              :messages [{:role "system" :content (m/pred string?)}
                         {:role "user" :content [{:type "text" :text "hello!"}]}
                         {:role "assistant" :content "I should say hello"}
                         {:role "assistant" :content [{:type "text" :text "hello there!"}]}
                         {:role "user" :content [{:type "text" :text "how are you?"}]}]}
             llm.mocks/*last-req-body*))))))

(deftest tool-calling
  (eca/start-process!)

  (is (match?
       {:models (m/embeds ["ollama/qwen3"])}
       (eca/request! (fixture/initialize-request {:initializationOptions (merge fixture/default-init-options
                                                                                {:ollamaApiUrl (str fixture/base-llm-mock-url "/ollama")})
                                                  :capabilities {:codeAssistant {:chat {}}}}))))
  (eca/notify! (fixture/initialized-notification))
  (let [chat-id* (atom nil)]
    (testing "We ask what files LLM see"
      (llm.mocks/set-case! :tool-calling-0)
      (let [req-id 0
            resp (eca/request! (fixture/chat-prompt-request
                                {:request-id req-id
                                 :model "ollama/qwen3"
                                 :message "What files you see?"}))
            chat-id (reset! chat-id* (:chatId resp))]

        (is (match?
             {:chatId (m/pred string?)
              :model "ollama/qwen3"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "What files you see?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "I will list files"})
        (match-content chat-id req-id "assistant" {:type "toolCallPrepare"
                                                   :origin "native"
                                                   :id (m/pred string?)
                                                   :name "eca_directory_tree"
                                                   :argumentsText ""
                                                   :manualApproval false
                                                   :summary "Listing file tree"})
        (match-content chat-id req-id "assistant" {:type "toolCallRun"
                                                   :origin "native"
                                                   :id (m/pred string?)
                                                   :name "eca_directory_tree"
                                                   :arguments {:path (h/project-path->canon-path "resources")}
                                                   :manualApproval false
                                                   :summary "Listing file tree"})
        (match-content chat-id req-id "assistant" {:type "toolCalled"
                                                   :origin "native"
                                                   :id (m/pred string?)
                                                   :name "eca_directory_tree"
                                                   :arguments {:path (h/project-path->canon-path "resources")}
                                                   :summary "Listing file tree"
                                                   :error false
                                                   :outputs [{:type "text" :text (str "[FILE] " (h/project-path->canon-path "resources/file1.md\n")
                                                                                      "[FILE] " (h/project-path->canon-path "resources/file2.md\n"))}]})
        (match-content chat-id req-id "assistant" {:type "text" :text "The files I see:\n"})
        (match-content chat-id req-id "assistant" {:type "text" :text "file1\nfile2\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:model "qwen3"
              :messages [{:role "user" :content [{:type "text" :text "What files you see?"}]}
                         {:role "assistant" :content [{:type "text" :text "I will list files"}]}
                         {:role "assistant" :tool-calls [{:type "function"
                                                          :function {:id (m/pred string?)
                                                                     :name "eca_directory_tree"
                                                                     :arguments {:path (h/project-path->canon-path "resources")}
                                                                     :summary "Listing file tree"
                                                                     :origin "native"}}]}
                         {:role "tool" :content (str "[FILE] " (h/project-path->canon-path "resources/file1.md\n")
                                                     "[FILE] " (h/project-path->canon-path "resources/file2.md\n\n"))}]
              :tools (m/embeds [{:type "function" :function {:name "eca_directory_tree"}}])}
             llm.mocks/*last-req-body*))))))
