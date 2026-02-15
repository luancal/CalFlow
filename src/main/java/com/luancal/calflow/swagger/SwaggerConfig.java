package com.luancal.calflow.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
  @Bean
  public OpenAPI OpenAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("CalFlow - Meu Portfólio")
                    .description("API de agendamento integrada com WhatsApp e Google Calendar")
                    .version("1.0.0")
                    .contact(new Contact()
                            .name("Luan Nicolas")
                            .url("https://github.com/luancal")
                            .email("calluann11@gmail.com")));
  }
}