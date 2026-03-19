package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WhatsApp contact card structure per Cloud API spec.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactCardPayload {

    private Name name;
    private List<Phone> phones;
    private List<Email> emails;
    private List<Address> addresses;
    private Org org;
    private List<Url> urls;
    private String birthday;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Name {
        private String formattedName;
        private String firstName;
        private String lastName;
        private String middleName;
        private String prefix;
        private String suffix;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Phone {
        private String phone;
        private String type;
        private String waId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Email {
        private String email;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zip;
        private String country;
        private String countryCode;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Org {
        private String company;
        private String department;
        private String title;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Url {
        private String url;
        private String type;
    }
}
