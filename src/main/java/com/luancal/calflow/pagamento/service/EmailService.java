package com.luancal.calflow.pagamento.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void enviarCredenciais(String destinatario, String nome, String usuario, String senha) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(destinatario);
            helper.setSubject("🎉 Bem-vindo ao CalFlow! Suas credenciais de acesso");

            String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0a0a0a;color:#fff;padding:40px;border-radius:16px;">
                    <h1 style="color:#F59E0B;">Bem-vindo ao CalFlow!</h1>
                    <p>Olá, %s!</p>
                    <p>Seu sistema de agendamento automático foi ativado.</p>

                    <div style="background:#141414;border:1px solid rgba(245,158,11,0.3);border-radius:12px;padding:20px;margin:20px 0;">
                        <h3 style="color:#F59E0B;">🔑 Suas Credenciais:</h3>
                        <p><strong>Usuário:</strong> <code style="color:#F59E0B;">%s</code></p>
                        <p><strong>Senha:</strong> <code style="color:#F59E0B;">%s</code></p>
                    </div>

                    <p><strong>Acesse:</strong> <a href="https://calflow.app.br/cliente.html" style="color:#F59E0B;">calflow.app.br/cliente.html</a></p>

                    <h3>Próximos passos:</h3>
                    <ol>
                        <li>Faça login na área do cliente</li>
                        <li>Escaneie o QR Code para conectar seu WhatsApp</li>
                        <li>Configure seus serviços e horários</li>
                        <li>Pronto! Bot ativo 24/7</li>
                    </ol>
                </div>
                """.formatted(nome, usuario, senha);

            helper.setText(html, true);
            mailSender.send(message);

            log.info("Email de credenciais enviado: {}", destinatario);
        } catch (Exception e) {
            log.error("Erro ao enviar email: {}", destinatario, e);
        }
    }
}
