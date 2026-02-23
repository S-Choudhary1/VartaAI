package tech.vartaai.whatsappcrm.integration.provider;

public class SendResponse {
    private String id;
    private String providerMessageId;
    private String status;
    private String mediaId;
    private String mimeType;
    private String filename;

    public SendResponse() {}

    public SendResponse(String providerMessageId, String status) {
        this.providerMessageId = providerMessageId;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}


