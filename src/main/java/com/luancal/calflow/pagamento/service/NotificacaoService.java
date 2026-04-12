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

    // Nome da instância que VOCÊ (Dono do CalFlow) vai usar para mandar avisos
    private static final String INSTANCIA_ADMIN = "calflow_admin";

    @Async
    public void enviarBoasVindasCliente(Cliente cliente) {
        String mensagem = String.format(
                "🎉 *Bem-vindo ao CalFlow!*\n\n" +
                        "Olá %s!\n\nSeu sistema está *ATIVO*! 🚀\n\n" +
                        "🔗 *Acesse:* calflow.pages.dev/cliente.html\n" +
                        "👤 *Usuário:* %s\n\n" +
                        "Próximos passos:\n" +
                        "1️⃣ Faça login\n" +
                        "2️⃣ Escaneie o QR Code\n" +
                        "3️⃣ Configure serviços e horários\n\n" +
                        "Dúvidas? Responda aqui! 💬",
                cliente.getNome(), cliente.getEmail()
        );

        enviar(cliente.getTelefone(), mensagem);
    }

    @Async
    public void notificarAfiliadoVendaAprovada(Venda venda) {
        if (venda.getAfiliado() == null) return;

        Afiliado afiliado = venda.getAfiliado();
        // ✅ CORREÇÃO: Pegando a comissão direto do produto da venda
        BigDecimal comissao = venda.getTipo() == TipoVenda.IMPLANTACAO
                ? venda.getProduto().getComissaoAfiliadoImplantacao()
                : venda.getProduto().getComissaoAfiliadoRecorrente();

        String mensagem = String.format(
                "🎉 *VENDA APROVADA!*\n\n" +
                        "Parabéns %s! 🔥\n\n" +
                        "*Cliente:* %s\n" +
                        "*Sua comissão:* R$ %.2f\n\n" +
                        "Obrigado por vender o CalFlow! 🚀",
                afiliado.getNome(),
                venda.getCliente().getNome(),
                comissao
        );

        enviar(afiliado.getTelefone(), mensagem);
    }

    @Async
    public void notificarGestorVendaAprovada(Venda venda) {
        // ✅ Segurança: Verifica se existe afiliado e se esse afiliado tem gestor
        if (venda.getAfiliado() == null || venda.getAfiliado().getGestor() == null) return;

        Gestor gestor = venda.getAfiliado().getGestor();
        BigDecimal comissao = venda.getTipo() == TipoVenda.IMPLANTACAO
                ? venda.getProduto().getComissaoGestorImplantacao()
                : venda.getProduto().getComissaoGestorRecorrente();

        String mensagem = String.format(
                "💰 *COMISSÃO DE GESTOR*\n\n" +
                        "Seu afiliado *%s* fechou uma venda!\n\n" +
                        "*Cliente:* %s\n" +
                        "*Sua comissão:* R$ %.2f\n\n" +
                        "Continue treinando sua equipe! 🚀",
                venda.getAfiliado().getNome(),
                venda.getCliente().getNome(),
                comissao
        );

        enviar(gestor.getTelefone(), mensagem);
    }

    @Async
    public void enviarCredenciaisWhatsApp(String telefone, String nome, String usuario, String senha) {
        String mensagem = String.format(
                "🔑 *Seu acesso ao CalFlow*\n\n" +
                        "Olá %s, aqui estão seus dados:\n\n" +
                        "👤 *Usuário:* %s\n" +
                        "🔒 *Senha:* %s\n\n" +
                        "🌐 https://calflow.pages.dev/cliente.html",
                nome, usuario, senha
        );

        enviar(telefone, mensagem);
    }
    @Async
    public void notificarPagamentoRealizado(Comissao comissao, BigDecimal total) {
        String telefone;
        String nome;

        // Verifica se a comissão é de um Afiliado ou de um Gestor
        if (comissao.getAfiliado() != null) {
            telefone = comissao.getAfiliado().getTelefone();
            nome = comissao.getAfiliado().getNome();
        } else if (comissao.getGestor() != null) {
            telefone = comissao.getGestor().getTelefone();
            nome = comissao.getGestor().getNome();
        } else {
            return; // Se não tiver nenhum dos dois, cancela o envio
        }

        String mensagem = String.format(
                "💸 *PAGAMENTO REALIZADO!*\n\n" +
                        "Olá %s!\n\n" +
                        "Suas comissões no valor total de *R$ %.2f* acabam de ser pagas via PIX! 🚀\n\n" +
                        "Confira sua conta bancária.",
                nome, total
        );

        enviar(telefone, mensagem);
    }
    @Async
    public void enviarCredenciaisAfiliado(String telefone, String nome, String codigo, String senha) {
        String mensagem = String.format(
                "🎉 *Bem-vindo ao Programa de Afiliados CalFlow!*\n\n" +
                        "Olá %s!\n\n" +
                        "🔑 *Seus dados de acesso:*\n" +
                        "• Código: `%s`\n" +
                        "• Senha: `%s`\n\n" +
                        "🔗 *Seu link:*\n" +
                        "calflow.pages.dev/checkout?ref=%s\n\n" +
                        "💰 *Suas comissões:*\n" +
                        "• R$ 150 por venda\n" +
                        "• R$ 40/mês por cliente\n" +
                        "• R$ 550 por anual\n\n" +
                        "📊 *Painel:* calflow.pages.dev/afiliados.html\n\n" +
                        "Qualquer dúvida, me chame aqui! 🚀",
                nome, codigo, senha, codigo
        );
        enviar(telefone, mensagem);
    }

    // Método privado para evitar repetição de código
    private void enviar(String telefone, String mensagem) {
        try {
            evolutionServiceCF.enviarMensagem(INSTANCIA_ADMIN, telefone, mensagem);
            log.info("✅ Notificação enviada para: {}", telefone);
        } catch (Exception e) {
            log.error("❌ Falha ao enviar notificação para {}: {}", telefone, e.getMessage());
        }
    }
}
