package com.luancal.calflow.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleCalendarConfig {

    @Value("${GOOGLE_SERVICE_ACCOUNT_JSON:}")
    private String googleKeyJson;

    @Bean
    public Calendar googleCalendar() throws IOException, GeneralSecurityException {
        InputStream is;

        // 1. Tenta carregar da Variável de Ambiente (Ideal para Railway)
        if (googleKeyJson != null && !googleKeyJson.isBlank()) {
            is = new ByteArrayInputStream(googleKeyJson.getBytes(StandardCharsets.UTF_8));
        }
        // 2. Se não tiver variável, tenta os arquivos físicos (Local)
        else {
            File dockerFile = new File("/app/service-account-key.json");
            File raizFile = new File("service-account-key.json");

            if (dockerFile.exists()) {
                is = new FileInputStream(dockerFile);
            } else if (raizFile.exists()) {
                is = new FileInputStream(raizFile);
            } else {
                is = getClass().getResourceAsStream("/service-account-key.json");
            }
        }

        if (is == null) {
            throw new FileNotFoundException("Chave do Google não encontrada (nem variável GOOGLE_SERVICE_ACCOUNT_JSON nem arquivo .json)!");
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(is)
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("CalFlow-SaaS")
                .build();
    }
}