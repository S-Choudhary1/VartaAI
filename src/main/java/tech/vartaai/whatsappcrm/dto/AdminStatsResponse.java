package tech.vartaai.whatsappcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminStatsResponse {
    private long totalClients;
    private long totalContacts;
    private long totalMessages;
    private long totalCampaigns;
    private long activeFlows;
}
