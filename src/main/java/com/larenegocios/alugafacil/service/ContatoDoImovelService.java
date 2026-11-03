package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosContatoProprietario;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Fala com o proprietario sem expor o e-mail dele: a mensagem passa pelo sistema. O WhatsApp so aparece para quem
// esta logado como locatario, e so quando o proprietario cadastrou telefone.
@Service
public class ContatoDoImovelService {

    @Autowired
    private ImovelService imovelService;

    @Autowired
    private NotificacaoDeEventos notificador;

    public DadosContatoProprietario contato(Long imovelId) {
        Usuario locador = imovelService.buscarPorId(imovelId).getLocador();
        return new DadosContatoProprietario(locador.getNome(), whatsappDe(locador.getTelefone()));
    }

    public void enviarMensagem(Long imovelId, String mensagem, Usuario de) {
        Imovel imovel = imovelService.buscarPorId(imovelId);
        if (imovel.getLocador().getId().equals(de.getId())) {
            throw new RegraDeNegocioException("Voce nao pode escrever para si mesmo");
        }
        notificador.mensagemDeInteressado(imovel, de, mensagem.trim());
    }

    // Telefone brasileiro de 10 ou 11 digitos vira o numero com o 55 do pais; qualquer outra coisa nao gera link.
    static String whatsappDe(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            return digitos;
        }
        return digitos.length() == 10 || digitos.length() == 11 ? "55" + digitos : null;
    }
}
