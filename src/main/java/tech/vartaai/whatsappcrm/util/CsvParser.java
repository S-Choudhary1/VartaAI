package tech.vartaai.whatsappcrm.util;

import lombok.Data;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Component
public class CsvParser {

    @Data
    public static class Row {
        public String phone;
        public String name;
        public Map<String, String> variables = new HashMap<>();

    }

    public List<Row> parse(InputStream inputStream) {
        List<Row> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT
                .withHeader()
                .withTrim()
                .withIgnoreHeaderCase()
                .parse(new InputStreamReader(inputStream))) {
            
            Map<String, Integer> headerMap = parser.getHeaderMap();
            
            for (CSVRecord rec : parser) {
                // Find phone column (case insensitive handled by parser option, but map keys are lower case?)
                // Actually withIgnoreHeaderCase doesn't change map keys casing if I recall, but let's assume standard "phone" or "PhoneNumber"
                
                String phone = null;
                if (rec.isMapped("phone")) phone = rec.get("phone");
                else if (rec.isMapped("PhoneNumber")) phone = rec.get("PhoneNumber");
                else if (rec.isMapped("mobile")) phone = rec.get("mobile");
                
                if (phone == null || phone.isBlank()) {
                    continue; // skip invalid rows
                }
                
                Row row = new Row();
                row.phone = phone;
                
                if (rec.isMapped("name")) row.name = rec.get("name");
                else if (rec.isMapped("Name")) row.name = rec.get("Name");
                
                // Collect other columns as variables
                for (String header : headerMap.keySet()) {
                    String lowerHeader = header.toLowerCase();
                    if (!lowerHeader.equals("phone") && 
                        !lowerHeader.equals("phonenumber") && 
                        !lowerHeader.equals("mobile") && 
                        !lowerHeader.equals("name")) {
                        
                        if (rec.isMapped(header)) {
                            row.variables.put(header, rec.get(header));
                        }
                    }
                }
                
                rows.add(row);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }
        return rows;
    }
}


