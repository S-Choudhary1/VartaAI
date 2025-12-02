package tech.vartaai.whatsappcrm.integration.provider;

public class SendResponse {
    private String providerMessageId;
    private String status;

    public SendResponse() {}

    public SendResponse(String providerMessageId, String status) {
        this.providerMessageId = providerMessageId;
        this.status = status;
    }

    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}


