package tech.vartaai.whatsappcrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tech.vartaai.whatsappcrm.entity.McpServerRegistry;
import tech.vartaai.whatsappcrm.repository.McpServerRegistryRepository;

import java.util.List;

@Component
@Slf4j
public class McpServerSeeder implements CommandLineRunner {

    private final McpServerRegistryRepository repository;

    public McpServerSeeder(McpServerRegistryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("MCP_SEEDER skipped — {} servers already exist", repository.count());
            return;
        }

        log.info("MCP_SEEDER seeding MCP server registry...");

        List<McpServerRegistry> servers = List.of(
                buildServer("Google Calendar", "google-calendar",
                        "Book appointments, check availability, manage calendar events",
                        "https://mcp.googleapis.com/calendar/sse",
                        "oauth2", "calendar",
                        """
                        {"scopes":"calendar.events,calendar.readonly","auth_url":"https://accounts.google.com/o/oauth2/auth","token_url":"https://oauth2.googleapis.com/token"}
                        """,
                        """
                        ["create_event","list_events","update_event","delete_event","check_availability"]
                        """,
                        "[]"),

                buildServer("Shopify", "shopify",
                        "Track orders, browse products, check shipment status",
                        "https://mcp.shopify.com/sse",
                        "oauth2", "ecommerce",
                        """
                        {"auth_url":"https://{store}.myshopify.com/admin/oauth/authorize","token_url":"https://{store}.myshopify.com/admin/oauth/access_token"}
                        """,
                        """
                        ["get_order_status","list_products","get_product","track_shipment"]
                        """,
                        """
                        [{"key":"store_url","label":"Shopify Store URL","type":"text"}]
                        """),

                buildServer("Notion", "notion",
                        "Search knowledge base, read pages, query databases",
                        "https://mcp.notion.com/mcp",
                        "oauth2", "knowledge_base",
                        """
                        {"auth_url":"https://api.notion.com/v1/oauth/authorize","token_url":"https://api.notion.com/v1/oauth/token"}
                        """,
                        """
                        ["search_pages","read_page","query_database"]
                        """,
                        "[]"),

                buildServer("HubSpot", "hubspot",
                        "Create contacts, manage deals, log CRM activities",
                        "https://mcp.hubspot.com/anthropic",
                        "oauth2", "crm",
                        """
                        {"auth_url":"https://app.hubspot.com/oauth/authorize","token_url":"https://api.hubapi.com/oauth/v1/token"}
                        """,
                        """
                        ["create_contact","update_contact","create_deal","log_activity"]
                        """,
                        "[]"),

                buildServer("Cal.com", "cal-com",
                        "Check availability, book and cancel appointments",
                        "https://mcp.cal.com/sse",
                        "api_key", "calendar",
                        "{}",
                        """
                        ["get_availability","create_booking","cancel_booking"]
                        """,
                        """
                        [{"key":"api_key","label":"Cal.com API Key","type":"password"}]
                        """),

                buildServer("Razorpay", "razorpay",
                        "Check payment status, create payment links, track refunds",
                        "https://mcp-proxy.vartaai.tech/razorpay/sse",
                        "api_key", "payments",
                        "{}",
                        """
                        ["check_payment_status","create_payment_link","get_refund_status"]
                        """,
                        """
                        [{"key":"key_id","label":"Razorpay Key ID","type":"text"},{"key":"key_secret","label":"Razorpay Key Secret","type":"password"}]
                        """),

                buildServer("Google Sheets", "google-sheets",
                        "Read spreadsheets, append rows, search data",
                        "https://mcp.googleapis.com/sheets/sse",
                        "oauth2", "database",
                        """
                        {"scopes":"spreadsheets.readonly,spreadsheets","auth_url":"https://accounts.google.com/o/oauth2/auth","token_url":"https://oauth2.googleapis.com/token"}
                        """,
                        """
                        ["read_sheet","append_row","update_cell","search_sheet"]
                        """,
                        "[]")
        );

        repository.saveAll(servers);
        log.info("MCP_SEEDER seeded {} MCP servers", servers.size());
    }

    private McpServerRegistry buildServer(String name, String slug, String description,
                                           String mcpUrl, String authType, String category,
                                           String authConfig, String capabilities,
                                           String requiredFields) {
        McpServerRegistry server = new McpServerRegistry();
        server.setName(name);
        server.setSlug(slug);
        server.setDescription(description);
        server.setMcpUrl(mcpUrl);
        server.setAuthType(authType);
        server.setCategory(category);
        server.setAuthConfig(authConfig.trim());
        server.setCapabilities(capabilities.trim());
        server.setRequiredFields(requiredFields.trim());
        return server;
    }
}
