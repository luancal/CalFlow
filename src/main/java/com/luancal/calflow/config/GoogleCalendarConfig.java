package com.luancal.calflow.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleCalendarConfig {

    @Bean
    public Calendar googleCalendar() throws IOException, GeneralSecurityException {
        InputStream is = null;

        // 1. Caminho docker
        File dockerFile = new File("/app/service-account-key.json");
        // 2. Tenta na Raiz do projeto
        File raizFile = new File("service-account-key.json");

        if (dockerFile.exists()) {
            is = new FileInputStream(dockerFile);
        } else if (raizFile.exists()) {
            is = new FileInputStream(raizFile);
        } else {
            is = getClass().getResourceAsStream("/service-account-key.json");
        }

        if (is == null) {
            throw new FileNotFoundException("Arquivo service-account-key.json não encontrado na raiz ou resources!");
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