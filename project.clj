(defproject assistant "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies      [[org.clojure/clojure             "1.11.1"]
                      [org.clojure/core.async          "1.7.701"]
                      [org.clojure/data.json           "2.5.1"]
                      [net.clojars.wkok/openai-clojure "0.22.0"]
                      [hato                            "1.0.0"]
                      [org.imgscalr/imgscalr-lib       "4.2"]]
  :java-source-paths ["src/java"]
  :source-paths      ["src/clojure"]
  :repl-options      {:init-ns assistant.core}
  :main              assistant.core)
