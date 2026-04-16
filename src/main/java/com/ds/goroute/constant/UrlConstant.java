package com.ds.goroute.constant;

public class UrlConstant {

    private UrlConstant() {}

    public static final String HEALTH_CHECK_URL = "/actuator/health";

    public enum ExampleServiceApi {
        EXAMPLE("internal/example/"),
        ;

        private String value;

        private ExampleServiceApi(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}