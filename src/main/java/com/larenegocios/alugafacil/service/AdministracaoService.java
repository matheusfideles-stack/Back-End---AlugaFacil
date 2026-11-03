package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.ContratoRepository;
import com.larenegocios.alugafacil.domain.avaliacao.AvaliacaoRepository;
import com.larenegocios.alugafacil.domain.imovel.ImovelRepository;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.PagamentoRepository;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.dto.DadosAvaliacaoAdmin;
import com.larenegocios.alugafacil.dto.DadosContrato;
import com.larenegocios.alugafacil.dto.DadosImovel;
import com.larenegocios.alugafacil.dto.DadosPagamento;
import com.larenegocios.alugafacil.dto.DadosUsuario;
import com.larenegocios.alugafacil.infra.email.EnvioDeEmail;
import com.larenegocios.alugafacil.infra.exception.RecursoNaoEncontradoException;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Visao geral para o administrador: so consulta, exceto ativar/desativar contas e imoveis, apagar avaliacoes
// e mandar lembrete de pagamento pendente.
@Service
public class AdministracaoService {

    private static final Logger log = LoggerFactory.getLogger(AdministracaoService.class);
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PRAZO_EM_DIAS = 5;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ImovelRepository imovelRepository;

    @Autowired
    private ImovelService imovelService;

    @Autowired
    private AvaliacaoService avaliacaoService;

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private EnvioDeEmail envioDeEmail;

    @Value("${app.url-base}")
    private String urlBase;

    public Page<DadosUsuario> listarUsuarios(Perfil perfil, Pageable pageable) {
        Page<Usuario> usuarios = perfil == null
                ? usuarioRepository.findAll(pageable)
                : usuarioRepository.findByPerfil(perfil, pageable);
        return usuarios.map(DadosUsuario::new);
    }

    // Desativar a propria conta tiraria o unico administrador do sistema: isso nao e permitido.
    @Transactional
    public void definirAtivoDoUsuario(Long id, boolean ativo, Usuario solicitante) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado"));
        if (!ativo && usuario.getId().equals(solicitante.getId())) {
            throw new RegraDeNegocioException("Voce nao pode desativar a propria conta");
        }
        usuario.setAtivo(ativo);
    }

    public Page<DadosImovel> listarImoveis(Pageable pageable) {
        return avaliacaoService.comResumos(imovelRepository.findAll(pageable));
    }

    // "Disponivel" e o mesmo controle que o locador usa: desligado, o imovel some da busca publica.
    @Transactional
    public void definirDisponibilidadeDoImovel(Long id, boolean disponivel, Usuario solicitante) {
        if (disponivel) {
            imovelService.reativar(id, solicitante);
        } else {
            imovelService.desativar(id, solicitante);
        }
    }

    public Page<DadosAvaliacaoAdmin> listarAvaliacoes(Pageable pageable) {
        return avaliacaoRepository.findAll(pageable).map(DadosAvaliacaoAdmin::new);
    }

    public Page<DadosContrato> listarContratos(Pageable pageable) {
        return contratoRepository.findAll(pageable).map(DadosContrato::new);
    }

    public Page<DadosPagamento> listarPagamentos(Pageable pageable) {
        return pagamentoRepository.findAll(pageable).map(DadosPagamento::new);
    }

    // So lembra quem ainda nao pagou: pagamento ja confirmado ou em processamento nao recebe aviso.
    public void enviarLembreteDePagamento(Long id) {
        Pagamento pagamento = pagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado"));
        if (pagamento.getStatus() != StatusPagamento.PENDENTE) {
            throw new RegraDeNegocioException("So e possivel lembrar pagamentos que estao aguardando");
        }

        Contrato contrato = pagamento.getContrato();
        Usuario locatario = contrato.getLocatario();
        String mes = mesExibido(pagamento.getReferenciaMes());
        String prazo = DATA.format(LocalDate.now(ZONA_BRASIL).plusDays(PRAZO_EM_DIAS));
        String assunto = "AlugaFacil - lembrete: pagamento do aluguel de " + mes + " pendente";
        String texto = "Ola, " + locatario.getNome() + "!\n\n"
                + "Ainda nao identificamos o pagamento do aluguel de " + mes + " do imovel "
                + contrato.getImovel().getTitulo() + ".\n"
                + "Valor: " + moeda(pagamento) + "\n\n"
                + "Pague ate " + prazo + " (em ate " + PRAZO_EM_DIAS + " dias) pela tela de pagamentos:\n"
                + urlBase + "/pagamentos.html\n\n"
                + "Se voce ja pagou, pode ignorar este aviso: a confirmacao pode levar algum tempo.";
        try {
            envioDeEmail.enviar(locatario.getEmail(), assunto, texto);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar lembrete do pagamento {}", id, e);
            throw new RegraDeNegocioException("Nao foi possivel enviar o e-mail agora. Tente de novo em instantes.");
        }
    }

    // "2026-12" vira "12/2026".
    private static String mesExibido(String referenciaMes) {
        String[] partes = referenciaMes.split("-");
        return partes.length == 2 ? partes[1] + "/" + partes[0] : referenciaMes;
    }

    private static String moeda(Pagamento pagamento) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(pagamento.getValor())
                .replace(' ', ' ');
    }
}
