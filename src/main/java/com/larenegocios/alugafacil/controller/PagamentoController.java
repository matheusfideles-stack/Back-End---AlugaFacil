package com.larenegocios.alugafacil.controller;

import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.gateway.mercadopago.MercadoPagoProperties;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.dto.DadosConfiguracaoPagamento;
import com.larenegocios.alugafacil.dto.DadosPagamento;
import com.larenegocios.alugafacil.dto.DadosSolicitacaoPagamento;
import com.larenegocios.alugafacil.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private MercadoPagoProperties mercadoPagoProperties;

    @Value("${pagamento.gateway.provider:fake}")
    private String provider;

    @GetMapping("/config")
    public ResponseEntity<DadosConfiguracaoPagamento> configuracao() {
        return ResponseEntity.ok(new DadosConfiguracaoPagamento(provider, mercadoPagoProperties.getPublicKey()));
    }

    @PostMapping
    public ResponseEntity<DadosPagamento> gerarCobranca(@RequestBody @Valid DadosSolicitacaoPagamento dados,
                                                        @AuthenticationPrincipal Usuario solicitante) {
        Pagamento pagamento = pagamentoService.gerarCobranca(dados, solicitante);
        return ResponseEntity.ok(new DadosPagamento(pagamento));
    }

    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<Page<DadosPagamento>> listarDoContrato(@PathVariable Long contratoId,
                                                                 @AuthenticationPrincipal Usuario solicitante,
                                                                 Pageable pageable) {
        Page<Pagamento> pagamentos = pagamentoService.listarDoContrato(contratoId, solicitante, pageable);
        return ResponseEntity.ok(pagamentos.map(DadosPagamento::new));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DadosPagamento> detalhar(@PathVariable Long id) {
        return ResponseEntity.ok(new DadosPagamento(pagamentoService.buscarPorId(id)));
    }

    @PostMapping("/{id}/sincronizar")
    public ResponseEntity<DadosPagamento> sincronizar(@PathVariable Long id,
                                                      @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(new DadosPagamento(pagamentoService.sincronizarComGateway(id, solicitante)));
    }

    @PostMapping("/{id}/estornar")
    public ResponseEntity<Void> estornar(@PathVariable Long id, @AuthenticationPrincipal Usuario solicitante) {
        pagamentoService.estornar(id, solicitante);
        return ResponseEntity.noContent().build();
    }
}
