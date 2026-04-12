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

                    <p><strong>Acesse:</strong> <a href="https://calflow.pages.dev/cliente.html" style="color:#F59E0B;">calflow.pages.dev/cliente.html</a></p>

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

    @Async
    public void enviarCredenciaisAfiliado(String destinatario, String nome, String codigo, String senha) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(destinatario);
            helper.setSubject("🎉 Bem-vindo ao Programa de Afiliados CalFlow!");

            String link = "https://calflow.pages.dev/afiliados.html";
            String linkAfiliado = "https://calflow.pages.dev/checkout?ref=" + codigo;

            String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0a0a0a;color:#fff;padding:40px;border-radius:16px;">
                <h1 style="color:#F59E0B;">Bem-vindo ao time CalFlow! 🚀</h1>
                <p>Olá, %s!</p>
                <p>Seu cadastro como afiliado foi realizado com sucesso. Aqui estão seus dados de acesso:</p>
                
                <div style="background:#141414;border:1px solid rgba(245,158,11,0.3);border-radius:12px;padding:20px;margin:20px 0;">
                    <h3 style="color:#F59E0B;">🔑 Suas Credenciais:</h3>
                    <p><strong>Código de afiliado:</strong> <code style="color:#F59E0B;">%s</code></p>
                    <p><strong>Senha:</strong> <code style="color:#F59E0B;">%s</code></p>
                </div>
                
                <div style="background:#141414;border:1px solid rgba(34,197,94,0.3);border-radius:12px;padding:20px;margin:20px 0;">
                    <h3 style="color:#22c55e;">🔗 Seu link de afiliado:</h3>
                    <p style="word-break:break-all;color:#F59E0B;">%s</p>
                </div>
                
                <p><strong>Comissões:</strong></p>
                <ul>
                    <li>R$ 150 por venda (implantação)</li>
                    <li>R$ 40/mês por cliente ativo</li>
                    <li>R$ 550 por venda anual</li>
                </ul>
                
                <p>Pagamentos todo dia 5 via PIX.</p>
                
                <a href="%s" style="display:inline-block;background:#F59E0B;color:#000;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:700;margin-top:16px;">Acessar Meu Painel →</a>
            </div>
        """.formatted(nome, codigo, senha, linkAfiliado, link);

            helper.setText(html, true);
            mailSender.send(message);

            log.info("Email de afiliado enviado: {}", destinatario);
        } catch (Exception e) {
            log.error("Erro ao enviar email de afiliado: {}", destinatario, e);
        }
    }

    @Async
    public void enviarEmailAdmin(String destinatario, String assunto, String corpo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(corpo, false);
            mailSender.send(message);
            log.info("Email admin enviado: {}", assunto);
        } catch (Exception e) {
            log.error("Erro ao enviar email admin", e);
        }
    }
}
