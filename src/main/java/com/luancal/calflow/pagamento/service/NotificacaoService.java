package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.service.EvolutionService;
import com.luancal.calflow.pagamento.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    @Autowired
    private EvolutionServiceCF evolutionServiceCF;

    /**
     * Envia boas-vindas ao cliente que acabou de comprar
     */
    @Async
    public void enviarBoasVindasCliente(Cliente cliente, String instanceAdmin) {
        String mensagem = String.format(
                "🎉 *Bem-vindo ao CalFlow!*\n\n" +
                        "Olá %s!\n\nSeu sistema está *ATIVO*! 🚀\n\n" +
                        "Próximos passos:\n" +
                        "1️⃣ Acesse calflow.app.br/cliente.html\n" +
                        "2️⃣ Escaneie o QR Code\n" +
                        "3️⃣ Configure serviços e horários\n\n" +
                        "Dúvidas? Responda aqui! 💬",
                cliente.getNome()
        );

        // ✅ REALMENTE ENVIAR
        try {
            evolutionServiceCF.enviarMensagem(instanceAdmin, cliente.getTelefone(), mensagem);
            log.info("Boas-vindas WhatsApp enviadas: {}", cliente.getTelefone());
        } catch (Exception e) {
            log.error("Erro ao enviar boas-vindas WhatsApp", e);
        }
    }

    /**
     * Notifica afiliado que vendeu
     */
    public void notificarAfiliadoVendaAprovada(Venda venda) {

        Afiliado afiliado = venda.getAfiliado();
        BigDecimal comissao = venda.getTipo() == TipoVenda.IMPLANTACAO
                ? venda.getProduto().getComissaoAfiliadoImplantacao()
                : venda.getProduto().getComissaoAfiliadoRecorrente();

        String mensagem = String.format(
                "🎉 *VENDA APROVADA!*\n\n" +
                        "Parabéns %s! 🔥\n\n" +
                        "*Cliente:* %s\n" +
                        "*Produto:* CalFlow\n" +
                        "*Sua comissão:* R$ %.2f\n\n" +
                        "Pagamento será feito dia *05* do próximo mês via PIX.\n\n" +
                        "Continue vendendo! 💰🚀",
                afiliado.getNome(),
                venda.getCliente().getNome(),
                comissao
        );

        try {

            log.info("Notificação afiliado enviada: afiliado={}, comissao={}",
                    afiliado.getNome(), comissao);

        } catch (Exception e) {
            log.error("Erro ao notificar afiliado: afiliadoId={}", afiliado.getId(), e);
        }
    }

    /**
     * Notifica gestor que afiliado dele vendeu
     */
    public void notificarGestorVendaAprovada(Venda venda) {

        Gestor gestor = venda.getAfiliado().getGestor();
        BigDecimal comissao = venda.getTipo() == TipoVenda.IMPLANTACAO
                ? venda.getProduto().getComissaoGestorImplantacao()
                : venda.getProduto().getComissaoGestorRecorrente();

        String mensagem = String.format(
                "💰 *COMISSÃO DE GESTOR*\n\n" +
                        "Seu afiliado *%s* fechou uma venda!\n\n" +
                        "*Cliente:* %s\n" +
                        "*Sua comissão:* R$ %.2f\n\n" +
                        "Pagamento dia *05* do próximo mês.\n\n" +
                        "Continue treinando sua equipe! 🚀",
                venda.getAfiliado().getNome(),
                venda.getCliente().getNome(),
                comissao
        );

        try {

            log.info("Notificação gestor enviada: gestor={}, comissao={}",
                    gestor.getNome(), comissao);

        } catch (Exception e) {
            log.error("Erro ao notificar gestor: gestorId={}", gestor.getId(), e);
        }
    }

    /**
     * Notifica afiliado/gestor sobre pagamento realizado
     */
    public void notificarPagamentoRealizado(Comissao comissao, BigDecimal total) {

        String destinatario = comissao.getAfiliado() != null
                ? comissao.getAfiliado().getNome()
                : comissao.getGestor().getNome();

        String mensagem = String.format(
                "💸 *PAGAMENTO REALIZADO!*\n\n" +
                        "Olá %s!\n\n" +
                        "*Valor:* R$ %.2f\n" +
                        "*Comprovante:* %s\n\n" +
                        "Confira sua conta! 🎉\n\n" +
                        "Obrigado por fazer parte do CalFlow! 🚀",
                destinatario,
                total,
                comissao.getComprovantePagamento()
        );

        try {

            log.info("Notificação pagamento enviada: destinatario={}, valor={}",
                    destinatario, total);

        } catch (Exception e) {
            log.error("Erro ao notificar pagamento: comissaoId={}", comissao.getId(), e);
        }
    }
    @Async
    public void enviarCredenciaisWhatsApp(String telefone, String nome, String usuario, String senha) {
        String mensagem = String.format(
                "🎉 *CalFlow Ativado!*\n\n" +
                        "Olá, %s!\n\n" +
                        "🔑 *Suas credenciais de acesso:*\n" +
                        "👤 Usuário: %s\n" +
                        "🔒 Senha: %s\n\n" +
                        "🌐 Acesse: https://calflow.app.br/cliente.html\n\n" +
                        "📋 Próximos passos:\n" +
                        "1️⃣ Faça login na área do cliente\n" +
                        "2️⃣ Escaneie o QR Code\n" +
                        "3️⃣ Configure serviços e horários\n" +
                        "4️⃣ Pronto! Seu bot estará ativo 24/7 🚀",
                nome, usuario, senha
        );

        try {
            evolutionServiceCF.enviarMensagem("calflow_admin", telefone, mensagem);
            log.info("Credenciais WhatsApp enviadas para {}", telefone);
        } catch (Exception e) {
            log.error("Erro ao enviar credenciais por WhatsApp para {}", telefone, e);
        }
    }
}