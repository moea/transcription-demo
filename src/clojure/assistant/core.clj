(ns assistant.core
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.data.json  :as json]
            [clojure.pprint     :as pprint]
            [hato.websocket     :as ws])
  (:import [assistant
            CodecUtil
            AudioUtil$Recorder])
  (:gen-class))

(def API-KEY "...")
(def MODEL   "gpt-4o-realtime-preview-2024-12-17")
(def URI     (str "wss://api.openai.com/v1/realtime?model=" MODEL))

(def SESSION-UPDATE
  {:turn_detection            {:type            "server_vad"
                               :create_response false}
   :input_audio_transcription {:model "whisper-1"}})

(defn -main [& args]
  (let [samples    (async/chan
                    (async/dropping-buffer 1024)
                    (map #(CodecUtil/base64 %)))
        socket-in  (async/chan 1 (map (comp #(json/read-str % :key-fn keyword) str)))
        socket-out (async/chan 1 (map json/write-str))
        socket     @(ws/websocket
                     URI
                     {:headers    {"Authorization" (str "Bearer " API-KEY)
                                   "OpenAI-Beta"  "realtime=v1"}
                      :on-message (fn on-message [_ msg _]
                                    (async/put! socket-in msg))})]
    (async/thread
      (.start
       (AudioUtil$Recorder.)
       (reify java.util.function.Consumer
         (accept [_ sample]
           (async/put! samples sample)))))
    (async/thread
      (loop []
        (let [msg (<!! socket-out)]
          (ws/send! socket msg))
        (recur)))
    (loop [initialized false]
      (let [msg (async/poll! socket-in)]
        (when msg
          (pprint/pprint msg))
        (cond (= (:type msg) "session.created")
              (do
                (>!! socket-out {:type    "session.update"
                                 :session SESSION-UPDATE})
                (recur false))

              (= (:type msg) "session.updated")
              (recur true)

              (= (:type msg)
                 "conversation.item.input_audio_transcription.completed")
              (do
                (println (:transcript msg))
                (recur true))

              :else
              (do
                (when initialized
                  (>!! socket-out
                       {:type "input_audio_buffer.append"
                        :audio (<!! samples)}))
                (recur initialized)))))))
