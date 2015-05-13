(ns org.zalando.stups.friboo.user
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.ring :as r]
            [io.sarnowski.swagger1st.util.api :as api]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.log :as log]))

(defn require-teams
  "Returns a set of teams, a user is part of or throws an exception if user is in no team."
  ([request]
   (require-teams (:tokeninfo request) (require-config (:configuration request) :team-service-url)))
  ([tokeninfo team-service-url]
   (require-teams (get tokeninfo "uid") (get tokeninfo "access_token") team-service-url))
  ([user-id token team-service-url]
   (when-not user-id
     (log/warn "ACCESS DENIED (unauthenticated) because token does not contain user information.")
     (api/throw-error 403 "no user information available"))
   (let [response (http/get (r/conpath team-service-url "/user/" user-id)
                            {:oauth-token token
                             :as          :json})
         teams (:body response)]
     (if (empty? teams)
       (do
         (log/warn "ACCESS DENIED (unauthorized) because user is not any team.")
         (api/throw-error 403 "user has no teams"
                          {:user user-id}))
       (into #{} (map :id teams))))))

(defn require-team
  "Throws an exception if user is not in the given team, else returns nil."
  ([team request]
   (require-team team (:tokeninfo request) (require-config (:configuration request) :team-service-url)))
  ([team tokeninfo team-service-url]
   (require-team team (get tokeninfo "uid") (get tokeninfo "access_token") team-service-url))
  ([team user-id token team-service-url]
   (let [in-team? (require-teams user-id token team-service-url)]
     (when-not (in-team? team)
       (log/warn "ACCESS DENIED (unauthorized) because user is not in team %s." team)
       (api/throw-error 403 (str "user not in team '" team "'")
                        {:user          user-id
                         :required-team team
                         :user-teams    in-team?})))))
