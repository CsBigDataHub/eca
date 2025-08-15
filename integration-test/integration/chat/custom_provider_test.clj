(ns integration.chat.custom-provider-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [integration.eca :as eca]
   [integration.fixture :as fixture]
   [integration.helper :refer [match-content] :as h]
   [llm-mock.mocks :as llm.mocks]
   [llm-mock.server :as llm-mock.server]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(eca/clean-after-test)

(deftest simple-text
  (eca/start-process!)

  (testing "We use the default model from custom provider"
    (is (match?
         {:models (m/embeds ["myProvider/foo-1"])
          :chatDefaultModel "myProvider/foo-1"}
         (eca/request! (fixture/initialize-request
                        {:initializationOptions
                         (merge fixture/default-init-options
                                {:customProviders
                                 {"myProvider"
                                  {:api "openai-responses"
                                   :url (str "http://localhost:" llm-mock.server/port "/openai")
                                   :key "foobar"
                                   :models ["foo-0" "foo-1"]
                                   :defaultModel "foo-1"}}})
                         :capabilities {:codeAssistant {:chat {}}}})))))
  (eca/notify! (fixture/initialized-notification))
  (let [chat-id* (atom nil)]
    (testing "We send a simple hello message"
      (llm.mocks/set-case! :simple-text-0)
      (let [req-id 0
            resp (eca/request! (fixture/chat-prompt-request
                                {:request-id req-id
                                 :model "myProvider/foo-1"
                                 :message "Tell me a joke!"}))
            chat-id (reset! chat-id* (:chatId resp))]

        (is (match?
             {:chatId (m/pred string?)
              :model "myProvider/foo-1"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "Tell me a joke!\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Knock"})
        (match-content chat-id req-id "assistant" {:type "text" :text " knock!"})
        (match-content chat-id req-id "system" {:type "usage"
                                                :messageInputTokens 10
                                                :messageOutputTokens 20
                                                :sessionTokens 30
                                                :messageCost m/absent
                                                :sessionCost m/absent})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:input [{:role "user" :content [{:type "input_text" :text "Tell me a joke!"}]}]
              :instructions (m/pred string?)}
             llm.mocks/*last-req-body*))))

    (testing "We reply"
      (llm.mocks/set-case! :simple-text-1)
      (let [req-id 1
            resp (eca/request! (fixture/chat-prompt-request
                                {:chat-id @chat-id*
                                 :request-id req-id
                                 :model "myProvider/foo-1"
                                 :message "Who's there?"}))
            chat-id @chat-id*]

        (is (match?
             {:chatId (m/pred string?)
              :model "myProvider/foo-1"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "Who's there?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Foo"})
        (match-content chat-id req-id "system" {:type "usage"
                                                :messageInputTokens 10
                                                :messageOutputTokens 5
                                                :sessionTokens 45
                                                :messageCost m/absent
                                                :sessionCost m/absent})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:input [{:role "user" :content [{:type "input_text" :text "Tell me a joke!"}]}
                      {:role "assistant" :content [{:type "output_text" :text "Knock knock!"}]}
                      {:role "user" :content [{:type "input_text" :text "Who's there?"}]}]}
             llm.mocks/*last-req-body*))))

    (testing "model reply again keeping context"
      (llm.mocks/set-case! :simple-text-2)
      (let [req-id 2
            resp (eca/request! (fixture/chat-prompt-request
                                {:chat-id @chat-id*
                                 :request-id req-id
                                 :model "myProvider/foo-1"
                                 :message "What foo?"}))
            chat-id @chat-id*]

        (is (match?
             {:chatId (m/pred string?)
              :model "myProvider/foo-1"
              :status "success"}
             resp))

        (match-content chat-id req-id "user" {:type "text" :text "What foo?\n"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Waiting model"})
        (match-content chat-id req-id "system" {:type "progress" :state "running" :text "Generating"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Foo"})
        (match-content chat-id req-id "assistant" {:type "text" :text " bar!"})
        (match-content chat-id req-id "assistant" {:type "text" :text "\n\n"})
        (match-content chat-id req-id "assistant" {:type "text" :text "Ha!"})
        (match-content chat-id req-id "system" {:type "usage"
                                                :messageInputTokens 5
                                                :messageOutputTokens 15
                                                :sessionTokens 65
                                                :messageCost m/absent
                                                :sessionCost m/absent})
        (match-content chat-id req-id "system" {:type "progress" :state "finished"})
        (is (match?
             {:input [{:role "user" :content [{:type "input_text" :text "Tell me a joke!"}]}
                      {:role "assistant" :content [{:type "output_text" :text "Knock knock!"}]}
                      {:role "user" :content [{:type "input_text" :text "Who's there?"}]}
                      {:role "assistant" :content [{:type "output_text" :text "Foo"}]}
                      {:role "user" :content [{:type "input_text" :text "What foo?"}]}]}
             llm.mocks/*last-req-body*))))))
