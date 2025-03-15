(ns assistant.core
  (:require [clojure.core.async      :as async :refer [<!! >!!]]
            [clojure.data.json       :as json]
            [clojure.pprint          :as pprint]
            [hato.websocket          :as ws]
            [wkok.openai-clojure.api :as api])
  (:import [assistant
            CodecUtil
            ScreenshotUtil
            AudioUtil$Recorder])
  (:gen-class))


(def HIST-LEN 1000)
(def API-KEY  (System/getenv "OPENAI_API_KEY"))
(def RT-MODEL "gpt-4o-realtime-preview-2024-12-17")
(def MODEL    "chatgpt-4o-latest")
(def RT-URI   (str "wss://api.openai.com/v1/realtime?model=" RT-MODEL))

(def SYSTEM-PROMPT
  "You are an intelligent assistant capable of interpreting screen-captures
from a desktop operating system.  You answer succintly in plain text, no markdown.")

(def USER-PREAMBLE
  "The following text was received via voice; it may be an imperfect transcription.
  Interpret it as an instruction in relation to the previous image.  Your answer will
  be passed to a text-to-speech program, so do not use any code blocks, markdown, or
  other formatting.  Transcription: ")

(defonce llm-hist (atom []))

(def SESSION-UPDATE
  {:turn_detection            {:type            "server_vad"
                               :create_response false}
   :input_audio_transcription {:model "whisper-1"}})

(defn- history-conj+evict! [item & [{limit :limit :or {limit HIST-LEN}}]]
  (swap!
   llm-hist
   (fn swapper [items]
     (let [out (conj items item)]
       (cond-> out (< limit (count out)) (subvec 1))))))


(defn- transcript-recv [text]
  (let [image (-> (ScreenshotUtil/captureImage)
                  (ScreenshotUtil/scale 0.25)
                  ScreenshotUtil/toPNG
                  CodecUtil/base64)
        msgs  [{:role    "user"
                :content [{:type      "image_url"
                           :image_url {:url (str "data:image/png;base64," image)}}]}
               {:role    "user"
                :content (str USER-PREAMBLE text)}]
        out   (as-> @llm-hist $
                (into [{:role "system" :content SYSTEM-PROMPT}] $)
                (concat $ msgs))]
    (println "Creating chat completion")
    (let [compl (api/create-chat-completion {:model MODEL :messages out})]
      (when-let [msg (some-> compl :choices first :message (select-keys #{:role :content}))]
        (history-conj+evict! msg)
        (println (:content msg))))))

(defn- stream-audio! [>wsocket]
  (let [recorder (AudioUtil$Recorder.)]
    (async/thread
      (.start
       recorder
       (reify java.util.function.Consumer
         (accept [_ sample]
           (when-not (>!! >wsocket {:type "input_audio_buffer.append"
                                    :audio (CodecUtil/base64 sample)})
             (.stop recorder))))))))

(defn start-streaming! []
  (let [<wsocket    (async/chan 1 (map (comp #(json/read-str % :key-fn keyword) str)))
        >wsocket    (async/chan (async/sliding-buffer 128) (map json/write-str))
        wsocket    @(ws/websocket
                     RT-URI
                     {:headers    {"Authorization" (str "Bearer " API-KEY)
                                   "OpenAI-Beta"  "realtime=v1"}
                      :on-message (fn on-message [_ msg _]
                                    (async/put! <wsocket msg))})
        transcripts (async/chan (async/sliding-buffer 128))]
    (async/thread
      (loop []
        (when-let [text (<!! transcripts)]
          (transcript-recv text)
          (recur))))
    (async/thread
      (loop []
        (if-let [msg (<!! >wsocket)]
          (do
            (ws/send! wsocket msg)
            (recur))
          (ws/close! wsocket))))
    (async/thread
      (loop []
        (when-let [msg (<!! <wsocket)]
          (cond (= (:type msg) "session.created")
                (>!! >wsocket {:type    "session.update"
                               :session SESSION-UPDATE})

                (= (:type msg) "session.updated")
                (stream-audio! >wsocket)

                (= (:type msg)
                   "conversation.item.input_audio_transcription.completed")
                (>!! transcripts (:transcript msg)))
          (recur))))
    (fn stop-streaming! []
      (async/close! <wsocket)
      (async/close! >wsocket)
      (async/close! transcripts))))

(defn -main [& args]
  (start-streaming!))
