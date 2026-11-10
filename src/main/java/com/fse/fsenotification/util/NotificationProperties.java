package com.fse.fsenotification.util;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private Sms sms = new Sms();
    private Email email = new Email();

    @Data
    public static class Sms {
        private String provider = "AFRICASTALKINGSMS";
    }

    @Data
    public static class Email {
        private String provider = "SMTP";
        private boolean enabled;
        private String from = "";
        private Smtp smtp = new Smtp();
        private Mailtrap mailtrap = new Mailtrap();
    }

    @Data
    public static class Smtp {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean auth = true;
        private boolean starttls = true;
    }

    @Data
    public static class Mailtrap {
        private String host = "sandbox.smtp.mailtrap.io";
        private int port = 2525;
        private String username = "";
        private String password = "";
        private String from = "";
        private boolean starttls = true;
    }
}
