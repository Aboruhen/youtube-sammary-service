package com.youtube.summary.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds youtube.summary.llm.prompts.* from application.yml for summary, agenda, and translation prompts.
 */
@ConfigurationProperties(prefix = "youtube.summary.llm.prompts")
public class LlmPromptProperties {

    private Summary summary = new Summary();
    private Agenda agenda = new Agenda();
    private Translation translation = new Translation();

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public Agenda getAgenda() {
        return agenda;
    }

    public void setAgenda(Agenda agenda) {
        this.agenda = agenda;
    }

    public Translation getTranslation() {
        return translation;
    }

    public void setTranslation(Translation translation) {
        this.translation = translation;
    }

    public static class Summary {
        private String system = "";
        private String userPrefix = "";

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system != null ? system : "";
        }

        public String getUserPrefix() {
            return userPrefix;
        }

        public void setUserPrefix(String userPrefix) {
            this.userPrefix = userPrefix != null ? userPrefix : "";
        }
    }

    public static class Agenda {
        private String system = "";
        private String userPrefix = "";

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system != null ? system : "";
        }

        public String getUserPrefix() {
            return userPrefix;
        }

        public void setUserPrefix(String userPrefix) {
            this.userPrefix = userPrefix != null ? userPrefix : "";
        }
    }

    public static class Translation {
        private String system = "";
        private String userPrefix = "";

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system != null ? system : "";
        }

        public String getUserPrefix() {
            return userPrefix;
        }

        public void setUserPrefix(String userPrefix) {
            this.userPrefix = userPrefix != null ? userPrefix : "";
        }
    }
}
