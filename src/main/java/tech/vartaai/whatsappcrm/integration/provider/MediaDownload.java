package tech.vartaai.whatsappcrm.integration.provider;

public class MediaDownload {
    private final byte[] content;
    private final String mimeType;
    private final String filename;

    public MediaDownload(byte[] content, String mimeType, String filename) {
        this.content = content;
        this.mimeType = mimeType;
        this.filename = filename;
    }

    public byte[] getContent() {
        return content;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFilename() {
        return filename;
    }
}
