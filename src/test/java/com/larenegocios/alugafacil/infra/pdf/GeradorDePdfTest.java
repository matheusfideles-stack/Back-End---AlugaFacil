package com.larenegocios.alugafacil.infra.pdf;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.imovel.TipoImovel;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.pagamento.StatusPagamento;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeradorDePdfTest {

    private GeradorDePdf gerador;
    private Contrato contrato;

    @BeforeEach
    void montar() {
        ZoneId zona = ZoneId.of("America/Sao_Paulo");
        gerador = new GeradorDePdf(Clock.fixed(LocalDate.of(2026, 11, 2).atTime(12, 0).atZone(zona).toInstant(), zona));

        Usuario locador = pessoa(1, "Paulo Proprietario", "52998224725", "paulo@teste.com", Perfil.LOCADOR);
        Usuario locatario = pessoa(2, "Carla Locataria", "11144477735", "carla@teste.com", Perfil.LOCATARIO);

        Imovel imovel = new Imovel();
        imovel.setId(10L);
        imovel.setLocador(locador);
        imovel.setTitulo("Apartamento Vila Nova");
        imovel.setTipo(TipoImovel.APARTAMENTO);
        imovel.setLogradouro("Rua das Palmeiras");
        imovel.setNumero("88");
        imovel.setComplemento("Apto 302");
        imovel.setBairro("Vila Nova");
        imovel.setCidade("Patos de Minas");
        imovel.setUf("MG");
        imovel.setCep("38700-000");
        imovel.setAreaM2(95);

        contrato = new Contrato();
        contrato.setId(7L);
        contrato.setImovel(imovel);
        contrato.setLocatario(locatario);
        contrato.setDataInicio(LocalDate.of(2026, 11, 3));
        contrato.setDataFim(LocalDate.of(2027, 11, 3));
        contrato.setDiaVencimento(10);
        contrato.setValorMensal(new BigDecimal("3150.00"));
        contrato.setStatus(StatusContrato.ATIVO);
    }

    private static Usuario pessoa(long id, String nome, String cpf, String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setCpf(cpf);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);
        return usuario;
    }

    private static String texto(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        StringBuilder tudo = new StringBuilder();
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
            tudo.append(extrator.getTextFromPage(pagina)).append('\n');
        }
        leitor.close();
        // O PDF quebra linhas no meio das frases: compara-se com os espacos normalizados.
        return tudo.toString().replaceAll("\\s+", " ");
    }

    private Pagamento pagamento(BigDecimal valor, BigDecimal multa, BigDecimal juros) {
        Pagamento pagamento = new Pagamento();
        pagamento.setId(123L);
        pagamento.setContrato(contrato);
        pagamento.setReferenciaMes("2026-12");
        pagamento.setValor(valor);
        pagamento.setMulta(multa);
        pagamento.setJuros(juros);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setVencimento(LocalDate.of(2026, 12, 10));
        pagamento.setPagoEm(LocalDateTime.of(2026, 12, 9, 14, 30));
        pagamento.setIdTransacaoGateway("MP-998877");
        return pagamento;
    }

    @Test
    void contratoEUmPdfDeVerdade() {
        byte[] pdf = gerador.contrato(contrato);

        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
        assertTrue(pdf.length > 1000);
    }

    @Test
    void contratoTrazPartesImovelPrazoEValores() throws IOException {
        String texto = texto(gerador.contrato(contrato));

        assertTrue(texto.contains("CONTRATO DE LOCAÇÃO"));
        assertTrue(texto.contains("Contrato nº 7"));
        assertTrue(texto.contains("Paulo Proprietario"));
        assertTrue(texto.contains("529.982.247-25"), "CPF do locador formatado");
        assertTrue(texto.contains("Carla Locataria"));
        assertTrue(texto.contains("111.444.777-35"));
        assertTrue(texto.contains("Apartamento Vila Nova"));
        assertTrue(texto.contains("Rua das Palmeiras, 88 - Apto 302"));
        assertTrue(texto.contains("Patos de Minas/MG"));
        assertTrue(texto.contains("03/11/2026"));
        assertTrue(texto.contains("03/11/2027"));
        assertTrue(texto.contains("R$ 3.150,00"));
        assertTrue(texto.contains("todo dia 10"));
    }

    @Test
    void contratoCitaAMultaEOsJurosQueOSistemaDeFatoCobra() throws IOException {
        String texto = texto(gerador.contrato(contrato));

        assertTrue(texto.contains("multa de 2%"));
        assertTrue(texto.contains("juros de mora de 1% ao mês"));
    }

    @Test
    void contratoTemLinhasDeAssinaturaESemAssinaturaDigital() throws IOException {
        String texto = texto(gerador.contrato(contrato));

        assertTrue(texto.contains("Locador"));
        assertTrue(texto.contains("Locatário"));
        assertTrue(texto.contains("não substitui a orientação de um advogado"));
        assertFalse(texto.toLowerCase().contains("assinado digitalmente"));
    }

    @Test
    void contratoEncerradoAvisaNoTopo() throws IOException {
        contrato.setStatus(StatusContrato.ENCERRADO);

        assertTrue(texto(gerador.contrato(contrato)).contains("ENCERRADO"));
        contrato.setStatus(StatusContrato.ATIVO);
        assertFalse(texto(gerador.contrato(contrato)).contains("ENCERRADO"));
    }

    @Test
    void reciboTrazComprovanteValorFormaEDatas() throws IOException {
        String texto = texto(gerador.recibo(pagamento(new BigDecimal("3150.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        assertTrue(texto.contains("RECIBO DE PAGAMENTO"));
        assertTrue(texto.contains("PG-000123"));
        assertTrue(texto.contains("Carla Locataria"));
        assertTrue(texto.contains("R$ 3.150,00"));
        assertTrue(texto.contains("12/2026"));
        assertTrue(texto.contains("Pix"));
        assertTrue(texto.contains("10/12/2026"));
        assertTrue(texto.contains("09/12/2026"));
        assertTrue(texto.contains("MP-998877"));
        assertTrue(texto.contains("Paulo Proprietario"));
    }

    @Test
    void reciboSemAtrasoNaoMostraMultaNemJuros() throws IOException {
        String texto = texto(gerador.recibo(pagamento(new BigDecimal("3150.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        assertFalse(texto.contains("Multa por atraso"));
        assertFalse(texto.contains("Juros por atraso"));
    }

    @Test
    void reciboComAtrasoDetalhaAluguelMultaJurosETotal() throws IOException {
        String texto = texto(gerador.recibo(pagamento(new BigDecimal("3213.00"), new BigDecimal("63.00"), new BigDecimal("0.00"))));

        assertTrue(texto.contains("Multa por atraso"));
        assertTrue(texto.contains("R$ 63,00"));
        assertTrue(texto.contains("Aluguel"));
        assertTrue(texto.contains("R$ 3.150,00"));
        assertTrue(texto.contains("Total pago"));
        assertTrue(texto.contains("R$ 3.213,00"));
    }

    @Test
    void reciboDePagamentoAntigoSemCamposDeAtrasoAindaFunciona() throws IOException {
        Pagamento antigo = pagamento(new BigDecimal("3150.00"), null, null);

        assertTrue(texto(gerador.recibo(antigo)).contains("R$ 3.150,00"));
    }

    @Test
    void cpfSemOsOnzeDigitosNaoQuebra() {
        assertEquals("123.456.789-09", GeradorDePdf.formatarCpf("12345678909"));
        assertEquals("123", GeradorDePdf.formatarCpf("123"));
        assertEquals("não informado", GeradorDePdf.formatarCpf(null));
    }
}
