package tech.vartaai.whatsappcrm.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload for interactive messages: button, list, cta_url, product, product_list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InteractivePayload {

    /**
     * Interactive message sub-type: "button", "list", "cta_url", "product", "product_list"
     */
    private String type;

    private Header header;
    private Body body;
    private Footer footer;
    private Action action;

    // ─── Header ──────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        /** "text", "image", "video", "document" */
        private String type;
        private String text;
        private MediaRef image;
        private MediaRef video;
        private MediaRef document;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaRef {
        private String link;
        private String id;
    }

    // ─── Body / Footer ──────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Footer {
        private String text;
    }

    // ─── Action ─────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        /** For reply buttons (max 3) */
        private List<ReplyButton> buttons;

        /** For list messages: button text that opens the list */
        private String button;

        /** For list messages */
        private List<Section> sections;

        /** For CTA URL button */
        private String name;
        private Parameters parameters;

        /** For product messages */
        private String catalogId;
        private String productRetailerId;
    }

    // ─── Reply Button (for type=button) ─────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyButton {
        private String type;
        private Reply reply;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reply {
        private String id;
        private String title;
    }

    // ─── List Section (for type=list) ───────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String title;
        private List<Row> rows;
        /** For product_list: product items */
        private List<ProductItem> productItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private String id;
        private String title;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        private String productRetailerId;
    }

    // ─── CTA URL Parameters (for type=cta_url) ─────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameters {
        private String displayText;
        private String url;
    }
}
