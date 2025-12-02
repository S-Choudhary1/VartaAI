package tech.vartaai.VartaAI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"tech.vartaai"})
@EnableJpaRepositories(basePackages = {"tech.vartaai.whatsappcrm.repository"})
@EntityScan(basePackages = {"tech.vartaai.whatsappcrm.entity"})
@EnableScheduling
public class VartaAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(VartaAiApplication.class, args);
	}

}
