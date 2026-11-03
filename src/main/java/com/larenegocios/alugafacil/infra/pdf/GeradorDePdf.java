package com.larenegocios.alugafacil.infra.pdf;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.contrato.StatusContrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.pagamento.CalculoDeAtraso;
import com.larenegocios.alugafacil.domain.pagamento.MetodoPagamento;
import com.larenegocios.alugafacil.domain.pagamento.Pagamento;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Contrato e recibo em PDF com os dados que ja estao no sistema. O contrato e um modelo gerado automaticamente:
// nao ha assinatura digital, as partes assinam no papel.
@Component
public class GeradorDePdf {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Color AZUL = new Color(0x1E, 0x3A, 0x5F);
    private static final Color CINZA = new Color(0x5F, 0x66, 0x73);

    private final Clock relogio;

    public GeradorDePdf(Clock relogio) {
        this.relogio = relogio;
    }

    public byte[] contrato(Contrato contrato) {
        Imovel imovel = contrato.getImovel();
        return montar(documento -> {
            titulo(documento, "CONTRATO DE LOCAÇÃO",
                    "Contrato nº " + contrato.getId() + " · gerado em " + DATA.format(LocalDate.now(relogio)) + " pelo AlugaFácil");
            if (contrato.getStatus() == StatusContrato.ENCERRADO) {
                documento.add(paragrafo("Este contrato está ENCERRADO.", negrito(11, new Color(0xB9, 0x1C, 0x1C)), 6));
            }

            secao(documento, "1. Partes");
            documento.add(paragrafo("LOCADOR: " + pessoa(imovel.getLocador()), normal(11), 2));
            documento.add(paragrafo("LOCATÁRIO: " + pessoa(contrato.getLocatario()), normal(11), 6));

            secao(documento, "2. Imóvel");
            documento.add(paragrafo(imovel.getTitulo() + " (" + imovel.getTipo().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")", normal(11), 2));
            documento.add(paragrafo(enderecoCompleto(imovel), normal(11), 2));
            if (imovel.getAreaM2() != null) {
                documento.add(paragrafo("Área: " + imovel.getAreaM2() + " m²", normal(11), 6));
            }

            secao(documento, "3. Prazo");
            documento.add(paragrafo("De " + DATA.format(contrato.getDataInicio()) + " até " + DATA.format(contrato.getDataFim()) + ".", normal(11), 6));

            secao(documento, "4. Valor e pagamento");
            documento.add(paragrafo("Valor mensal (aluguel + condomínio): " + moeda(contrato.getValorMensal()) + ".", normal(11), 2));
            documento.add(paragrafo("Vencimento: todo dia " + contrato.getDiaVencimento() + " de cada mês. O pagamento é feito pela plataforma AlugaFácil, "
                    + "por Pix, boleto ou cartão.", normal(11), 6));

            secao(documento, "5. Atraso");
            documento.add(paragrafo("O aluguel pago após o vencimento recebe multa de " + percentual(CalculoDeAtraso.MULTA)
                    + " e juros de mora de " + percentual(CalculoDeAtraso.JUROS_AO_MES) + " ao mês, contados por dia de atraso.", normal(11), 6));

            secao(documento, "6. Obrigações");
            documento.add(paragrafo("O locatário deve usar o imóvel para o fim a que se destina, conservá-lo e devolvê-lo ao final do contrato "
                    + "no estado em que o recebeu. Pedidos de manutenção são feitos pelo sistema e respondidos pelo locador.", normal(11), 18));

            documento.add(paragrafo("Local e data: ______________________________, ____ / ____ / ________", normal(11), 34));
            assinaturas(documento, imovel.getLocador().getNome(), contrato.getLocatario().getNome());

            documento.add(paragrafo("Documento gerado automaticamente com os dados do contrato registrados no AlugaFácil. "
                    + "Ele não substitui a orientação de um advogado.", fonte(8, CINZA, Font.ITALIC), 0));
        });
    }

    public byte[] recibo(Pagamento pagamento) {
        Contrato contrato = pagamento.getContrato();
        Imovel imovel = contrato.getImovel();
        String comprovante = String.format("PG-%06d", pagamento.getId());
        BigDecimal multa = pagamento.getMulta() == null ? BigDecimal.ZERO : pagamento.getMulta();
        BigDecimal juros = pagamento.getJuros() == null ? BigDecimal.ZERO : pagamento.getJuros();
        BigDecimal base = pagamento.getValor().subtract(multa).subtract(juros);

        return montar(documento -> {
            titulo(documento, "RECIBO DE PAGAMENTO", "Comprovante " + comprovante);

            documento.add(paragrafo("Recebemos de " + pessoa(contrato.getLocatario()) + " o valor de " + moeda(pagamento.getValor())
                    + ", referente ao aluguel de " + mesExibido(pagamento.getReferenciaMes()) + " do imóvel " + imovel.getTitulo()
                    + ", localizado em " + enderecoCompleto(imovel) + ".", normal(11), 14));

            PdfPTable tabela = new PdfPTable(new float[]{1.2f, 2f});
            tabela.setWidthPercentage(100);
            linha(tabela, "Comprovante", comprovante);
            linha(tabela, "Referência", mesExibido(pagamento.getReferenciaMes()));
            linha(tabela, "Forma de pagamento", formaDePagamento(pagamento.getMetodo()));
            linha(tabela, "Vencimento", DATA.format(pagamento.getVencimento()));
            if (pagamento.getPagoEm() != null) {
                linha(tabela, "Pago em", DATA_E_HORA.format(pagamento.getPagoEm()));
            }
            linha(tabela, "Aluguel", moeda(base));
            if (multa.signum() > 0 || juros.signum() > 0) {
                linha(tabela, "Multa por atraso", moeda(multa));
                linha(tabela, "Juros por atraso", moeda(juros));
            }
            linha(tabela, "Total pago", moeda(pagamento.getValor()));
            if (pagamento.getIdTransacaoGateway() != null) {
                linha(tabela, "Código da transação", pagamento.getIdTransacaoGateway());
            }
            documento.add(tabela);

            documento.add(paragrafo("Recebedor (locador): " + pessoa(imovel.getLocador()), normal(11), 24));
            documento.add(paragrafo("Recibo gerado em " + DATA.format(LocalDate.now(relogio)) + " pelo AlugaFácil. "
                    + "Vale como comprovante do pagamento registrado no sistema.", fonte(8, CINZA, Font.ITALIC), 0));
        });
    }

    // ----- montagem

    private interface Conteudo {
        void escrever(Document documento) throws DocumentException;
    }

    private byte[] montar(Conteudo conteudo) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(documento, saida);
            documento.open();
            conteudo.escrever(documento);
        } catch (DocumentException e) {
            throw new IllegalStateException("Nao foi possivel gerar o PDF", e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return saida.toByteArray();
    }

    private void titulo(Document documento, String titulo, String subtitulo) throws DocumentException {
        Paragraph nome = new Paragraph("AlugaFácil", fonte(11, AZUL, Font.BOLD));
        nome.setSpacingAfter(10);
        documento.add(nome);
        Paragraph principal = new Paragraph(titulo, fonte(20, AZUL, Font.BOLD));
        principal.setSpacingAfter(2);
        documento.add(principal);
        Paragraph secundario = new Paragraph(subtitulo, fonte(9, CINZA, Font.NORMAL));
        secundario.setSpacingAfter(16);
        documento.add(secundario);
    }

    private void secao(Document documento, String texto) throws DocumentException {
        Paragraph secao = new Paragraph(texto, fonte(12, AZUL, Font.BOLD));
        secao.setSpacingBefore(8);
        secao.setSpacingAfter(3);
        documento.add(secao);
    }

    private void assinaturas(Document documento, String locador, String locatario) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(24);
        for (String[] parte : new String[][]{{"Locador", locador}, {"Locatário", locatario}}) {
            PdfPCell celula = new PdfPCell();
            celula.setBorder(Rectangle.TOP);
            celula.setBorderColor(CINZA);
            celula.setPadding(6);
            celula.setHorizontalAlignment(Element.ALIGN_CENTER);
            celula.addElement(new Paragraph(parte[1], normal(10)));
            celula.addElement(new Paragraph(parte[0], fonte(9, CINZA, Font.NORMAL)));
            tabela.addCell(celula);
        }
        documento.add(tabela);
    }

    private void linha(PdfPTable tabela, String rotulo, String valor) {
        adicionarCelula(tabela, rotulo, fonte(10, CINZA, Font.NORMAL));
        adicionarCelula(tabela, valor, negrito(10, Color.BLACK));
    }

    private void adicionarCelula(PdfPTable tabela, String texto, Font fonte) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte));
        celula.setBorder(Rectangle.BOTTOM);
        celula.setBorderColor(new Color(0xE4, 0xE0, 0xD8));
        celula.setPadding(7);
        tabela.addCell(celula);
    }

    private Paragraph paragrafo(String texto, Font fonte, float depois) {
        Paragraph paragrafo = new Paragraph(texto, fonte);
        paragrafo.setSpacingAfter(depois);
        paragrafo.setLeading(0, 1.35f);
        return paragrafo;
    }

    // ----- texto

    private static String pessoa(Usuario usuario) {
        return usuario.getNome() + ", CPF " + formatarCpf(usuario.getCpf()) + ", e-mail " + usuario.getEmail();
    }

    static String formatarCpf(String cpf) {
        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        return digitos.length() == 11
                ? digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-" + digitos.substring(9)
                : (cpf == null ? "não informado" : cpf);
    }

    private static String enderecoCompleto(Imovel i) {
        return i.getLogradouro() + ", " + i.getNumero() + (i.getComplemento() == null || i.getComplemento().isBlank() ? "" : " - " + i.getComplemento())
                + ", " + i.getBairro() + ", " + i.getCidade() + "/" + i.getUf() + ", CEP " + i.getCep();
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace(' ', ' ');
    }

    private static String percentual(BigDecimal fracao) {
        return fracao.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    // "2026-12" vira "12/2026".
    private static String mesExibido(String referenciaMes) {
        String[] partes = referenciaMes.split("-");
        return partes.length == 2 ? partes[1] + "/" + partes[0] : referenciaMes;
    }

    private static String formaDePagamento(MetodoPagamento metodo) {
        return switch (metodo) {
            case PIX -> "Pix";
            case BOLETO -> "Boleto";
            case CARTAO_DEBITO -> "Cartão de débito";
            case CARTAO_CREDITO -> "Cartão de crédito";
        };
    }

    // ----- fontes

    private static Font normal(float tamanho) {
        return fonte(tamanho, Color.BLACK, Font.NORMAL);
    }

    private static Font negrito(float tamanho, Color cor) {
        return fonte(tamanho, cor, Font.BOLD);
    }

    private static Font fonte(float tamanho, Color cor, int estilo) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamanho, estilo, cor);
    }
}
