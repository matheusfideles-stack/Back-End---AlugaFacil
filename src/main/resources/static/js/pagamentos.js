const params = new URLSearchParams(location.search);
const el = id => document.getElementById(id);

const NOMES_METODO = { pix: 'PIX', boleto: 'Boleto', debito: 'Débito', credito: 'Crédito' };
const METODO_API = { pix: 'PIX', boleto: 'BOLETO', debito: 'CARTAO_DEBITO', credito: 'CARTAO_CREDITO' };
const ROTULO_METODO_API = { PIX: 'PIX', BOLETO: 'Boleto', CARTAO_DEBITO: 'Débito', CARTAO_CREDITO: 'Crédito' };
const ROTULO_STATUS = { PAGO: 'Pago', PENDENTE: 'Aguardando', PROCESSANDO: 'Processando', RECUSADO: 'Recusado', ESTORNADO: 'Estornado' };
const ROTULO_VER = { PIX: 'Ver QR Code', BOLETO: 'Ver boleto' };

let contratos = [];
let contrato = null;
let historico = [];
let config = { provider: 'fake' };
let pagamentoAberto = null;
let recemCriado = false;
let modoModal = 'resultado';
let cartaoMetodo = null;
let parcelasEscolhidas = 1;
let temporizador = null;
let valorAtual = null;
let consultasDeValor = 0;

const BANDEIRAS = [
  { id: 'amex', nome: 'American Express', regex: /^3[47]/, credito: 'amex', debito: null, grupos: [4, 6, 5] },
  { id: 'elo', nome: 'Elo', regex: /^(4011|4312|4389|4514|4576|5041|5066|5067|509|6277|6362|6363|650|6516|6550)/, credito: 'elo', debito: 'debelo' },
  { id: 'hipercard', nome: 'Hipercard', regex: /^(606282|3841)/, credito: 'hipercard', debito: null },
  { id: 'master', nome: 'Mastercard', regex: /^(5[1-5]|2[2-7])/, credito: 'master', debito: 'debmaster' },
  { id: 'visa', nome: 'Visa', regex: /^4/, credito: 'visa', debito: 'debvisa' }
];
const ITF = ['nnwwn', 'wnnnw', 'nwnnw', 'wwnnn', 'nnwnw', 'wnwnn', 'nwwnn', 'nnnww', 'wnnwn', 'nwnwn'];

function erro(mensagem) {
  mostrarAviso(el('erro'), mensagem);
  if (mensagem) window.scrollTo({ top: 0, behavior: 'smooth' });
}

function metodoEscolhido() {
  const marcado = document.querySelector('input[name="metodo"]:checked');
  return marcado ? marcado.value : 'pix';
}

function mesesDisponiveis() {
  const inicioContrato = contrato.dataInicio.slice(0, 7);
  const base = somarMeses(mesAtual(), -2);
  const primeiro = inicioContrato > base ? inicioContrato : base;
  const lista = [];
  for (let i = 0; i < 6; i++) lista.push(somarMeses(primeiro, i));
  return lista;
}

function mesJaPago(mes) {
  return historico.some(p => p.referenciaMes === mes && (p.status === 'PAGO' || p.status === 'PROCESSANDO'));
}

function atualizarBotao() {
  const mes = el('sel-mes').value;
  const metodo = metodoEscolhido();
  const botao = el('btn-pagar');
  botao.disabled = !mes || mesJaPago(mes);
  botao.textContent = mesJaPago(mes)
    ? 'Este mês já está pago'
    : `Pagar ${formatarMoeda(valorAtual ? valorAtual.total : contrato.valorMensal)} com ${NOMES_METODO[metodo]}`;
}

function renderizarMeses() {
  const meses = mesesDisponiveis();
  const anterior = el('sel-mes').value;
  el('sel-mes').innerHTML = meses.map(m =>
    `<option value="${m}">${nomeDoMes(m)}${mesJaPago(m) ? ' (pago)' : ''}</option>`).join('');
  const padrao = meses.includes(anterior) ? anterior : (meses.find(m => !mesJaPago(m)) || meses[0]);
  el('sel-mes').value = padrao;
  atualizarBotao();
  atualizarValor();
}

// Depois do vencimento o mes custa mais (multa e juros): o servidor calcula e a tela mostra a conta.
async function atualizarValor() {
  const consulta = ++consultasDeValor;
  const mes = el('sel-mes').value;
  let atualizado = null;
  if (mes && !mesJaPago(mes)) {
    try {
      atualizado = await api(`/pagamentos/valor-atualizado?contratoId=${contrato.id}&referenciaMes=${mes}`);
    } catch (erroDeConsulta) {
      atualizado = null;
    }
  }
  if (consulta !== consultasDeValor) return;
  valorAtual = atualizado;
  el('pg-valor').textContent = formatarMoeda(atualizado ? atualizado.total : contrato.valorMensal);
  const aviso = el('pg-atraso');
  aviso.hidden = !(atualizado && atualizado.diasEmAtraso > 0);
  if (!aviso.hidden) {
    aviso.textContent = `Em atraso há ${atualizado.diasEmAtraso} ${atualizado.diasEmAtraso === 1 ? 'dia' : 'dias'}: ` +
      `aluguel ${formatarMoeda(atualizado.valorBase)} + multa ${formatarMoeda(atualizado.multa)} + juros ${formatarMoeda(atualizado.juros)}.`;
  }
  atualizarBotao();
}

function renderizarHistorico() {
  if (historico.length === 0) {
    el('historico').innerHTML = '<p class="info">Nenhum pagamento realizado ainda.</p>';
    return;
  }
  el('historico').innerHTML = historico.map(p => `
    <div class="linha">
      <div><strong>Aluguel - ${escaparHtml(p.tituloImovel)}</strong>
        <p class="info">${escaparHtml(nomeDoMes(p.referenciaMes))} · Vencimento ${formatarData(p.vencimento)} · ${escaparHtml(ROTULO_METODO_API[p.metodo] || p.metodo)}</p>
      </div>
      <div>
        <p class="valor">${formatarMoeda(p.valor)}</p>
        <span class="selo ${p.status === 'PAGO' ? '' : (p.status === 'RECUSADO' ? 'erro' : 'alerta')}">${ROTULO_STATUS[p.status] || p.status}</span>
        <p><button type="button" class="btn btn-mini" data-pagamento="${p.id}">${ROTULO_VER[p.metodo] || 'Ver detalhes'}</button></p>
      </div>
    </div>`).join('');
}

async function carregarHistorico() {
  const pagina = await api(`/pagamentos/contrato/${contrato.id}?size=100&sort=id,desc`);
  historico = pagina.content;
  renderizarHistorico();
  renderizarMeses();
}

function mostrarContrato() {
  el('pg-rotulo').textContent = 'Aluguel mensal';
  el('pg-imovel').textContent = contrato.tituloImovel;
  el('pg-info').textContent = `Contrato nº ${contrato.id} · Vencimento todo dia ${contrato.diaVencimento} · Válido até ${formatarData(contrato.dataFim)}`;
  el('pg-valor').textContent = formatarMoeda(contrato.valorMensal);
}

async function selecionarContrato(id) {
  contrato = contratos.find(c => c.id === Number(id));
  mostrarContrato();
  el('historico').innerHTML = '<p class="info">Carregando...</p>';
  await carregarHistorico();
}

async function mostrarContratacao(imovelId) {
  const imovel = await api(`/imoveis/${imovelId}`);
  if (!imovel.disponivel) {
    erro('Este imóvel não está mais disponível para locação.');
    return false;
  }
  el('ct-titulo').textContent = imovel.titulo;
  el('ct-endereco').textContent = `${enderecoCompleto(imovel)} · ${imovel.bairro}, ${imovel.cidade}/${imovel.uf}`;
  const total = Number(imovel.valorAluguel) + Number(imovel.valorCondominio || 0);
  el('ct-valor').textContent = `${formatarMoeda(total)}/mês`;
  el('ct-detalhe').textContent = `Aluguel ${formatarMoeda(imovel.valorAluguel)} + condomínio ${formatarMoeda(imovel.valorCondominio)}`;
  el('ct-dia').innerHTML = Array.from({ length: 28 }, (_, i) =>
    `<option value="${i + 1}"${i + 1 === 10 ? ' selected' : ''}>Dia ${i + 1}</option>`).join('');
  el('sec-contratar').hidden = false;

  el('ct-assinar').onclick = async () => {
    const botao = el('ct-assinar');
    botao.disabled = true;
    botao.textContent = 'Enviando...';
    try {
      const hoje = new Date();
      const fim = new Date(hoje.getFullYear() + 1, hoje.getMonth(), hoje.getDate());
      await api('/propostas', {
        metodo: 'POST',
        corpo: {
          imovelId: imovel.id, dataInicio: dataIso(hoje), dataFim: dataIso(fim), diaVencimento: Number(el('ct-dia').value),
          mensagem: el('ct-mensagem').value.trim() || null
        }
      });
      el('sec-contratar').hidden = true;
      mostrarAviso(el('confirmacao'), 'Proposta enviada! O proprietário foi avisado por e-mail e você recebe a resposta por e-mail. ' +
        'Acompanhe em Meus Aluguéis.', 'ok');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      erro(e.message);
      botao.disabled = false;
      botao.textContent = 'Enviar proposta ao proprietário';
    }
  };
  return true;
}

function parar() {
  if (temporizador) clearInterval(temporizador);
  temporizador = null;
}

function mostrarStatusModal(mensagem, tipo) {
  const status = el('pag-status');
  status.textContent = mensagem;
  status.className = tipo === 'ok' ? 'aviso ok' : (tipo === 'erro' ? 'aviso' : 'info');
  status.hidden = !mensagem;
}

function copiar(texto) {
  const botao = el('pag-copiar');
  const rotulo = botao ? botao.textContent : '';
  const feito = () => {
    mostrarStatusModal('Código copiado! Agora é só colar no app do seu banco.', 'ok');
    if (!botao) return;
    botao.textContent = 'Copiado ✓';
    setTimeout(() => { if (botao.isConnected) botao.textContent = rotulo; }, 2000);
  };
  const falhou = () => mostrarStatusModal('Não foi possível copiar. Selecione o código e copie manualmente.', 'erro');
  const alternativa = () => {
    try {
      el('pag-codigo').select();
      document.execCommand('copy') ? feito() : falhou();
    } catch (e) {
      falhou();
    }
  };
  if (navigator.clipboard) navigator.clipboard.writeText(texto).then(feito).catch(alternativa);
  else alternativa();
}

function dataHora(iso) {
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

// Usa a imagem do provedor quando vier; senao desenha o QR a partir do proprio codigo copia e cola.
function qrDoPix(p) {
  if (p.pixQrCodeBase64) return `<img class="qr" alt="QR Code Pix" src="data:image/png;base64,${p.pixQrCodeBase64}">`;
  if (!p.pixCopiaECola || !window.QRCode) return '';
  try {
    const qr = new QRCode(-1, QRErrorCorrectLevel.M);
    qr.addData(p.pixCopiaECola);
    qr.make();
    const n = qr.getModuleCount();
    let tracado = '';
    for (let linha = 0; linha < n; linha++) {
      for (let coluna = 0; coluna < n; coluna++) {
        if (qr.isDark(linha, coluna)) tracado += `M${coluna} ${linha}h1v1h-1z`;
      }
    }
    return `<svg class="qr" viewBox="-3 -3 ${n + 6} ${n + 6}" shape-rendering="crispEdges" role="img" aria-label="QR Code Pix">` +
      `<rect x="-3" y="-3" width="${n + 6}" height="${n + 6}" fill="#fff"/><path d="${tracado}" fill="#111"/></svg>`;
  } catch (e) {
    return '';
  }
}

function blocoPix(p) {
  const pago = p.status === 'PAGO';
  const qr = qrDoPix(p);
  let corpo = '';
  if (qr) {
    corpo += `<div class="qr-caixa${pago ? ' pago' : ''}">${qr}${pago ? '<span class="qr-selo">✓ Pago</span>' : ''}</div>` +
      `<p class="info pix-dica">${pago ? 'Este Pix já foi pago.' : 'Abra o app do seu banco, escolha pagar com Pix e escaneie o QR Code.'}</p>`;
  }
  if (p.pixCopiaECola) {
    corpo += '<label for="pag-codigo">Pix copia e cola</label>' +
      '<div class="copia">' +
      `<input id="pag-codigo" readonly value="${escaparHtml(p.pixCopiaECola)}">` +
      '<button type="button" class="btn btn-ouro" id="pag-copiar">Copiar</button></div>';
  }
  if (!pago && p.expiraEm) corpo += `<p class="info" style="margin-top:8px">Este código vale até ${dataHora(p.expiraEm)}.</p>`;
  return corpo + segundaVia(p, 'código Pix');
}

// A linha digitavel (47 digitos) e o codigo de barras (44) carregam os mesmos dados em ordem diferente.
function codigoDeBarras(linha) {
  const d = digitosDe(linha);
  if (d.length === 44) return d;
  if (d.length === 47) return d.slice(0, 4) + d.slice(32, 33) + d.slice(33, 47) + d.slice(4, 9) + d.slice(10, 20) + d.slice(21, 31);
  return null;
}

// Codigo de barras intercalado 2 de 5, o padrao dos boletos.
function svgBarras(digitos) {
  let x = 0;
  let barras = '';
  const desenhar = (largura, barra) => {
    if (barra) barras += `<rect x="${x}" y="0" width="${largura}" height="1"/>`;
    x += largura;
  };
  const largura = tipo => (tipo === 'w' ? 2 : 1);
  [1, 1, 1, 1].forEach((l, i) => desenhar(l, i % 2 === 0));
  for (let i = 0; i < digitos.length; i += 2) {
    const barrasDoPar = ITF[digitos[i]];
    const espacosDoPar = ITF[digitos[i + 1]];
    for (let k = 0; k < 5; k++) {
      desenhar(largura(barrasDoPar[k]), true);
      desenhar(largura(espacosDoPar[k]), false);
    }
  }
  desenhar(2, true);
  desenhar(1, false);
  desenhar(1, true);
  return `<svg class="barras" viewBox="0 0 ${x} 1" preserveAspectRatio="none" shape-rendering="crispEdges" role="img" aria-label="Código de barras do boleto"><g fill="#111">${barras}</g></svg>`;
}

function blocoBoleto(p) {
  const pago = p.status === 'PAGO';
  const barras = codigoDeBarras(p.linhaDigitavel);
  let corpo = '';
  if (barras) {
    corpo += `<div class="qr-caixa barras-caixa${pago ? ' pago' : ''}">${svgBarras(barras)}${pago ? '<span class="qr-selo">✓ Pago</span>' : ''}</div>`;
  }
  if (p.linhaDigitavel) {
    corpo += '<label for="pag-codigo">Linha digitável</label>' +
      '<div class="copia">' +
      `<input id="pag-codigo" readonly value="${escaparHtml(p.linhaDigitavel)}">` +
      '<button type="button" class="btn btn-ouro" id="pag-copiar">Copiar</button></div>';
  }
  corpo += '<div class="recibo">' +
    `<div><span>Valor</span><strong>${formatarMoeda(p.valor)}</strong></div>` + linhaDeAcrescimos(p) +
    (p.vencimento ? `<div><span>Vencimento</span><strong>${formatarData(p.vencimento)}</strong></div>` : '') +
    '</div>';
  if (!pago && p.linkPagamento) {
    corpo += `<div class="acoes-modal"><a class="btn" href="${escaparHtml(p.linkPagamento)}" target="_blank" rel="noopener">Abrir boleto</a></div>`;
  }
  return corpo + segundaVia(p, 'boleto');
}

function blocoRecibo(p) {
  const forma = p.metodo === 'CARTAO_DEBITO'
    ? 'Cartão de débito'
    : `Cartão de crédito${recemCriado && parcelasEscolhidas > 1 ? ` em ${parcelasEscolhidas}x` : ''}`;
  const linhas = [['Forma de pagamento', forma], ['Valor', formatarMoeda(p.valor)]];
  if (acrescimos(p) > 0) linhas.push(['Multa e juros', formatarMoeda(acrescimos(p))]);
  if (p.pagoEm) linhas.push(['Pago em', new Date(p.pagoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })]);
  linhas.push(['Comprovante', `PG-${String(p.id).padStart(6, '0')}`]);
  return '<div class="recibo">' + linhas.map(([rotulo, valor]) =>
    `<div><span>${rotulo}</span><strong>${escaparHtml(valor)}</strong></div>`).join('') + '</div>';
}

function acrescimos(p) {
  return Number(p.multa || 0) + Number(p.juros || 0);
}

function linhaDeAcrescimos(p) {
  return acrescimos(p) > 0 ? `<div><span>Multa e juros</span><strong>${formatarMoeda(acrescimos(p))}</strong></div>` : '';
}

// Boleto ou Pix vencido: gerar de novo recalcula o valor (multa e juros ate hoje).
function segundaVia(p, nome) {
  const vencido = p.status !== 'PAGO' && p.expiraEm && new Date(p.expiraEm) < new Date();
  if (!vencido) return '';
  return `<p class="aviso" style="margin-top:12px">Este ${nome} venceu. Gere uma segunda via com o valor atualizado.</p>` +
    `<div class="acoes-modal"><button type="button" class="btn btn-ouro" data-segunda-via="${p.id}">Gerar segunda via</button></div>`;
}

function botaoDoRecibo(p) {
  if (p.status !== 'PAGO') return '';
  const comprovante = `PG-${String(p.id).padStart(6, '0')}`;
  return `<div class="acoes-modal"><button type="button" class="btn" data-baixar="/pagamentos/${p.id}/recibo.pdf" data-arquivo="recibo-${comprovante}.pdf">Baixar recibo (PDF)</button></div>`;
}

function digitosDe(texto) {
  return String(texto || '').replace(/\D/g, '');
}

function bandeiraDoNumero(digitos) {
  return BANDEIRAS.find(b => b.regex.test(digitos)) || null;
}

function luhnValido(digitos) {
  let soma = 0;
  for (let i = 0; i < digitos.length; i++) {
    let n = Number(digitos[digitos.length - 1 - i]);
    if (i % 2 === 1) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    soma += n;
  }
  return soma % 10 === 0;
}

function formatarNumeroCartao(digitos, bandeira) {
  const grupos = bandeira && bandeira.grupos ? bandeira.grupos : [4, 4, 4, 4, 3];
  const partes = [];
  let inicio = 0;
  for (const tamanho of grupos) {
    if (inicio >= digitos.length) break;
    partes.push(digitos.slice(inicio, inicio + tamanho));
    inicio += tamanho;
  }
  return partes.join(' ');
}

function formularioCartao(metodo) {
  const valor = Number(contrato.valorMensal);
  const parcelas = metodo === 'credito'
    ? '<label for="cc-parcelas">Parcelas</label><select id="cc-parcelas">' +
      Array.from({ length: 12 }, (_, i) =>
        `<option value="${i + 1}">${i + 1}x de ${formatarMoeda(valor / (i + 1))}${i === 0 ? ' (à vista)' : ''}</option>`).join('') +
      '</select>'
    : '';
  return `<form id="form-cartao" novalidate autocomplete="on">
    <div class="cartao-previa">
      <div class="cp-topo"><span>${NOMES_METODO[metodo]}</span><span id="cp-bandeira">Cartão</span></div>
      <div class="cp-numero" id="cp-numero">•••• •••• •••• ••••</div>
      <div class="cp-base"><span id="cp-nome">Nome no cartão</span><span id="cp-validade">MM/AA</span></div>
    </div>
    <label for="cc-numero">Número do cartão</label>
    <input id="cc-numero" type="text" inputmode="numeric" autocomplete="cc-number" placeholder="0000 0000 0000 0000" maxlength="23">
    <label for="cc-nome">Nome impresso no cartão</label>
    <input id="cc-nome" type="text" autocomplete="cc-name" placeholder="COMO ESTÁ NO CARTÃO" maxlength="60">
    <div class="campos-2">
      <div><label for="cc-validade">Validade</label><input id="cc-validade" type="text" inputmode="numeric" autocomplete="cc-exp" placeholder="MM/AA" maxlength="5"></div>
      <div><label for="cc-cvv">CVV</label><input id="cc-cvv" type="text" inputmode="numeric" autocomplete="cc-csc" placeholder="123" maxlength="4"></div>
    </div>
    ${config.provider === 'fake' ? '' :
      '<label for="cc-cpf">CPF do titular do cartão</label>' +
      '<input id="cc-cpf" type="text" inputmode="numeric" autocomplete="off" placeholder="000.000.000-00" maxlength="14">'}
    ${parcelas}
    <p class="aviso" id="cc-erro" hidden style="margin-top:12px"></p>
    <p class="info nota">${config.provider === 'fake'
      ? 'Ambiente de teste: os dados do cartão ficam só neste navegador e não são enviados ao servidor.'
      : 'Os dados do cartão vão direto para o Mercado Pago, por conexão segura, e não passam pelo nosso servidor.'}</p>
    <button type="submit" hidden></button>
  </form>`;
}

function atualizarPrevia() {
  const digitos = digitosDe(el('cc-numero').value);
  const bandeira = bandeiraDoNumero(digitos);
  const mascara = bandeira && bandeira.id === 'amex' ? '•••• •••••• •••••' : '•••• •••• •••• ••••';
  const numero = formatarNumeroCartao(digitos, bandeira);
  el('cp-numero').textContent = numero + mascara.slice(numero.length);
  el('cp-bandeira').textContent = bandeira ? bandeira.nome : 'Cartão';
  el('cp-nome').textContent = el('cc-nome').value || 'Nome no cartão';
  el('cp-validade').textContent = el('cc-validade').value || 'MM/AA';
}

function lerCartao() {
  const invalido = (mensagem, campo) => {
    mostrarAviso(el('cc-erro'), mensagem);
    el(campo).focus();
    return null;
  };
  const digitos = digitosDe(el('cc-numero').value);
  const bandeira = bandeiraDoNumero(digitos);
  if (digitos.length < 13 || !luhnValido(digitos)) return invalido('Número do cartão inválido. Confira os dígitos.', 'cc-numero');
  // No pagamento real quem diz a bandeira e se o cartao aceita debito ou credito e o proprio Mercado Pago (depois,
  // em bandeiraPeloMercadoPago); a tabela local nao cobre todas as faixas de cartao e so vale para o modo de teste.
  const real = config.provider !== 'fake';
  if (!real && !bandeira) return invalido('Bandeira do cartão não reconhecida.', 'cc-numero');
  if (!real && cartaoMetodo === 'debito' && !bandeira.debito) {
    return invalido(`O cartão ${bandeira.nome} não aceita débito. Use Crédito, PIX ou boleto.`, 'cc-numero');
  }
  if (el('cc-nome').value.trim().length < 3) return invalido('Digite o nome como está impresso no cartão.', 'cc-nome');
  const validade = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(el('cc-validade').value);
  if (!validade) return invalido('Validade inválida. Use o formato MM/AA.', 'cc-validade');
  if (new Date(2000 + Number(validade[2]), Number(validade[1]), 0, 23, 59, 59) < new Date()) {
    return invalido('Cartão vencido. Use outro cartão.', 'cc-validade');
  }
  const tamanhoCvv = bandeira ? (bandeira.id === 'amex' ? 4 : 3) : null;
  const tamanhoDigitado = el('cc-cvv').value.length;
  if (tamanhoCvv ? tamanhoDigitado !== tamanhoCvv : (tamanhoDigitado < 3 || tamanhoDigitado > 4)) {
    return invalido(`O CVV tem ${tamanhoCvv || '3 ou 4'} dígitos.`, 'cc-cvv');
  }
  const cpf = el('cc-cpf') ? digitosDe(el('cc-cpf').value) : '';
  if (el('cc-cpf') && cpf.length !== 11) return invalido('Informe os 11 dígitos do CPF do titular do cartão.', 'cc-cpf');
  mostrarAviso(el('cc-erro'), '');
  return {
    bandeira: bandeira ? (cartaoMetodo === 'debito' ? bandeira.debito : bandeira.credito) : null,
    parcelas: cartaoMetodo === 'credito' ? Number(el('cc-parcelas').value) : 1,
    // Só existem na memória até o token ser gerado; nunca vão para o nosso servidor.
    cartao: {
      cardNumber: digitos,
      cardholderName: el('cc-nome').value.trim(),
      cardExpirationMonth: validade[1],
      cardExpirationYear: String(2000 + Number(validade[2])),
      securityCode: el('cc-cvv').value,
      identificationType: 'CPF',
      identificationNumber: cpf
    }
  };
}

let mercadoPagoPromessa = null;

// A biblioteca do Mercado Pago so e carregada quando existe pagamento real com cartao. O Access Token nunca vem
// para ca: a chave publica serve apenas para trocar os dados do cartao por um token de uso unico.
function carregarMercadoPago() {
  if (!mercadoPagoPromessa) {
    mercadoPagoPromessa = new Promise((resolve, reject) => {
      const iniciar = () => resolve(new window.MercadoPago(config.publicKey, { locale: 'pt-BR' }));
      if (window.MercadoPago) return iniciar();
      const script = document.createElement('script');
      script.src = 'https://sdk.mercadopago.com/js/v2';
      script.onload = iniciar;
      script.onerror = () => {
        mercadoPagoPromessa = null;
        reject(new Error('Não foi possível carregar o Mercado Pago. Verifique sua conexão e tente de novo.'));
      };
      document.head.appendChild(script);
    });
  }
  return mercadoPagoPromessa;
}

function mensagemDoErroDeCartao(erro) {
  const causas = Array.isArray(erro) ? erro : (erro && erro.cause) || [];
  const codigos = causas.map(c => String(c.code));
  if (codigos.some(c => ['205', 'E301'].includes(c))) return 'Número do cartão inválido.';
  if (codigos.some(c => ['208', '209', '325', '326'].includes(c))) return 'Validade do cartão inválida.';
  if (codigos.some(c => ['224', '225', 'E203', 'E302'].includes(c))) return 'Código de segurança (CVV) inválido.';
  if (codigos.some(c => ['212', '213', '214', '221', '316'].includes(c))) return 'Confira o nome e o CPF do titular do cartão.';
  const descricao = causas.map(c => c.description).filter(Boolean).join('; ');
  return descricao ? `Cartão recusado pelo Mercado Pago: ${descricao}` : 'Não foi possível validar o cartão. Confira os dados.';
}

// O Mercado Pago responde com os metodos possiveis para os 8 primeiros digitos; escolhe-se o de debito ou o de credito.
// Se o Mercado Pago nao conhece o cartao (resposta vazia), vale a bandeira da tabela local, quando ela existe.
async function bandeiraPeloMercadoPago(numero, daTabelaLocal) {
  const mp = await carregarMercadoPago();
  let resposta;
  try {
    resposta = await mp.getPaymentMethods({ bin: numero.slice(0, 8) });
  } catch (e) {
    throw new Error('Não foi possível identificar o cartão. Confira o número e tente de novo.');
  }
  const resultados = (resposta && resposta.results) || [];
  if (resultados.length === 0) {
    if (daTabelaLocal) return daTabelaLocal;
    throw new Error('Cartão não reconhecido. Confira o número ou use outro cartão, PIX ou boleto.');
  }
  const tipo = cartaoMetodo === 'debito' ? 'debit_card' : 'credit_card';
  const metodo = resultados.find(m => m.payment_type_id === tipo);
  if (!metodo) {
    throw new Error(cartaoMetodo === 'debito'
      ? 'Este cartão não aceita débito. Use Crédito, PIX ou boleto.'
      : 'Este cartão não aceita crédito. Use Débito, PIX ou boleto.');
  }
  return metodo.id;
}

async function gerarTokenDoCartao(cartao) {
  if (config.provider === 'fake') return 'token-de-teste';
  const mp = await carregarMercadoPago();
  try {
    const token = await mp.createCardToken(cartao);
    if (!token || !token.id) throw token;
    return token.id;
  } catch (e) {
    throw new Error(mensagemDoErroDeCartao(e));
  }
}

function renderizarResultado(p) {
  pagamentoAberto = p;
  const metodo = ROTULO_METODO_API[p.metodo];
  el('pag-titulo').textContent = p.status === 'PAGO'
    ? `Pagamento de ${formatarMoeda(p.valor)} via ${metodo}`
    : `Pagar ${formatarMoeda(p.valor)} com ${metodo}`;
  el('pag-resumo').textContent = `Aluguel de ${nomeDoMes(p.referenciaMes)} · ${p.tituloImovel}`;
  const acao = el('pag-acao');
  let corpo = '';

  if (p.status === 'PAGO') {
    parar();
    acao.hidden = true;
    el('pag-fechar').textContent = recemCriado ? 'Concluir' : 'Fechar';
    el('pag-corpo').innerHTML = (p.metodo === 'PIX' ? blocoPix(p) : (p.metodo === 'BOLETO' ? blocoBoleto(p) : blocoRecibo(p))) + botaoDoRecibo(p);
    mostrarStatusModal(`Pagamento aprovado! Comprovante PG-${String(p.id).padStart(6, '0')}.`, 'ok');
    return;
  }

  if (p.status === 'RECUSADO' || p.status === 'ESTORNADO') {
    parar();
    acao.hidden = true;
    el('pag-corpo').innerHTML = '';
    const motivo = p.statusDetalhe ? ` (${p.statusDetalhe})` : '';
    mostrarStatusModal(`Pagamento ${p.status === 'RECUSADO' ? 'recusado' : 'estornado'}${motivo}. Tente outro método.`, 'erro');
    return;
  }

  if (p.metodo === 'PIX') {
    corpo += blocoPix(p);
    mostrarStatusModal('Aguardando o pagamento...', 'info');
  } else if (p.metodo === 'BOLETO') {
    corpo += blocoBoleto(p);
    mostrarStatusModal('Aguardando a compensação do boleto (pode levar até 2 dias úteis).', 'info');
  } else {
    if (p.urlAutenticacao3ds) corpo += `<p><a class="btn btn-ouro" href="${escaparHtml(p.urlAutenticacao3ds)}" target="_blank" rel="noopener">Autenticar no banco</a></p>`;
    mostrarStatusModal('Aguardando a confirmação do pagamento...', 'info');
  }

  el('pag-corpo').innerHTML = corpo;
  acao.hidden = false;
  iniciarVerificacao(p.id);
}

async function verificar(id, manual) {
  try {
    const p = await api(`/pagamentos/${id}/sincronizar`, { metodo: 'POST' });
    if (!pagamentoAberto || pagamentoAberto.id !== p.id) return;
    const mudou = pagamentoAberto.status !== p.status;
    if (mudou) renderizarResultado(p);
    if (mudou && p.status !== 'PENDENTE' && p.status !== 'PROCESSANDO') await carregarHistorico();
    else if (manual && !mudou) mostrarStatusModal('Ainda não identificamos o pagamento. Tente de novo em instantes.', 'info');
  } catch (e) {
    if (manual) mostrarStatusModal(e.message, 'erro');
  }
}

function iniciarVerificacao(id) {
  parar();
  let tentativas = 0;
  temporizador = setInterval(() => {
    if (++tentativas > 120) return parar();
    verificar(id, false);
  }, 5000);
}

function mostrarModal() {
  el('modal-pagamento').hidden = false;
  document.body.style.overflow = 'hidden';
  el('pag-fechar').focus();
}

function abrirFormularioCartao(metodo) {
  cartaoMetodo = metodo;
  modoModal = 'formulario';
  recemCriado = true;
  pagamentoAberto = null;
  el('pag-titulo').textContent = `Pagar ${formatarMoeda(contrato.valorMensal)} com ${NOMES_METODO[metodo]}`;
  el('pag-resumo').textContent = `Aluguel de ${nomeDoMes(el('sel-mes').value)} · ${contrato.tituloImovel}`;
  el('pag-corpo').innerHTML = formularioCartao(metodo);
  mostrarStatusModal('', 'info');
  el('pag-acao').textContent = `Pagar ${formatarMoeda(contrato.valorMensal)}`;
  el('pag-acao').hidden = false;
  el('pag-fechar').textContent = 'Cancelar';
  mostrarModal();
  el('cc-numero').focus();
}

async function confirmarCartao() {
  const dados = lerCartao();
  if (!dados) return;

  let tokenCartao;
  let bandeiraCartao = dados.bandeira;
  const botao = el('pag-acao');
  botao.disabled = true;
  try {
    // A bandeira vem antes do token: o token e de uso unico e nao deve ser gasto num cartao que sera recusado.
    if (config.provider !== 'fake') bandeiraCartao = await bandeiraPeloMercadoPago(dados.cartao.cardNumber, dados.bandeira);
    tokenCartao = await gerarTokenDoCartao(dados.cartao);
  } catch (e) {
    mostrarAviso(el('cc-erro'), e.message);
    return;
  } finally {
    botao.disabled = false;
  }

  await enviarPagamento({
    contratoId: contrato.id,
    referenciaMes: el('sel-mes').value,
    metodo: METODO_API[cartaoMetodo],
    tokenCartao,
    bandeiraCartao,
    parcelas: dados.parcelas
  });
}

function abrirModal() {
  recemCriado = true;
  modoModal = 'resultado';
  el('pag-titulo').textContent = 'Pagamento';
  el('pag-resumo').textContent = '';
  el('pag-corpo').innerHTML = '';
  el('pag-acao').hidden = true;
  el('pag-fechar').textContent = 'Fechar';
  mostrarStatusModal('Gerando cobrança...', 'info');
  mostrarModal();
}

function abrirDoHistorico(id) {
  const p = historico.find(h => h.id === id);
  if (!p) return;
  parar();
  recemCriado = false;
  modoModal = 'resultado';
  el('pag-fechar').textContent = 'Fechar';
  renderizarResultado(p);
  mostrarModal();
}

function fecharModal() {
  el('modal-pagamento').hidden = true;
  document.body.style.overflow = '';
  parar();
  if (recemCriado && pagamentoAberto && pagamentoAberto.status === 'PAGO') {
    mostrarAviso(el('confirmacao'),
      `Pagamento de ${formatarMoeda(pagamentoAberto.valor)} via ${ROTULO_METODO_API[pagamentoAberto.metodo]} aprovado! ` +
      `Comprovante PG-${String(pagamentoAberto.id).padStart(6, '0')}.`, 'ok');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  pagamentoAberto = null;
  modoModal = 'resultado';
  cartaoMetodo = null;
}

async function enviarPagamento(corpo) {
  parcelasEscolhidas = corpo.parcelas || 1;
  abrirModal();
  try {
    const p = await api('/pagamentos', { metodo: 'POST', corpo });
    renderizarResultado(p);
    await carregarHistorico();
  } catch (e) {
    mostrarStatusModal(e.message, 'erro');
  }
}

async function pagar() {
  erro('');
  const metodo = metodoEscolhido();

  if (metodo === 'debito' || metodo === 'credito') {
    if (config.provider !== 'fake' && !config.publicKey) {
      erro('Pagamento com cartão ainda não está habilitado neste ambiente. Use PIX ou boleto.');
      return;
    }
    abrirFormularioCartao(metodo);
    return;
  }

  await enviarPagamento({ contratoId: contrato.id, referenciaMes: el('sel-mes').value, metodo: METODO_API[metodo] });
}

async function iniciar() {
  if (!Sessao.logado()) return;
  try {
    const [pagina, cfg] = await Promise.all([
      api('/contratos/meus?papel=locatario&size=100&sort=id,desc'),
      api('/pagamentos/config').catch(() => config)
    ]);
    config = cfg;
    contratos = pagina.content.filter(c => c.status === 'ATIVO');
    el('carregando').hidden = true;

    const imovelParam = params.get('imovel');
    const contratoParam = params.get('contrato');
    const contratoDoImovel = imovelParam && contratos.find(c => String(c.imovelId) === imovelParam);

    if (imovelParam && !contratoDoImovel) {
      if (await mostrarContratacao(imovelParam)) return;
    }

    if (contratos.length === 0) {
      el('sem-contrato').hidden = false;
      return;
    }

    el('sec-pagar').hidden = false;
    if (contratos.length > 1) {
      el('rot-contrato').hidden = false;
      el('sel-contrato').hidden = false;
      el('sel-contrato').innerHTML = contratos.map(c =>
        `<option value="${c.id}">${escaparHtml(c.tituloImovel)} (contrato nº ${c.id})</option>`).join('');
    }

    const inicial = contratoParam ? Number(contratoParam) : (contratoDoImovel ? contratoDoImovel.id : contratos[0].id);
    const escolhido = contratos.some(c => c.id === inicial) ? inicial : contratos[0].id;
    el('sel-contrato').value = escolhido;
    await selecionarContrato(escolhido);

    if (params.get('novo')) {
      mostrarAviso(el('confirmacao'), 'Contrato assinado! Agora é só pagar o primeiro aluguel.', 'ok');
    }
  } catch (e) {
    el('carregando').hidden = true;
    if (e.status === 401) return irParaLogin('expirou');
    erro(e.message);
  }
}

document.querySelectorAll('input[name="metodo"]').forEach(r => r.addEventListener('change', atualizarBotao));
el('sel-mes').addEventListener('change', () => { atualizarBotao(); atualizarValor(); });
el('sel-contrato').addEventListener('change', e => selecionarContrato(e.target.value));
el('btn-pagar').addEventListener('click', pagar);
el('pag-fechar').addEventListener('click', fecharModal);
el('pag-x').addEventListener('click', fecharModal);
el('historico').addEventListener('click', e => {
  const botao = e.target.closest('[data-pagamento]');
  if (botao) abrirDoHistorico(Number(botao.dataset.pagamento));
});
el('pag-acao').addEventListener('click', () => {
  if (modoModal === 'formulario') return el('form-cartao').requestSubmit();
  if (pagamentoAberto) verificar(pagamentoAberto.id, true);
});
el('modal-pagamento').addEventListener('click', e => {
  if (e.target.id === 'modal-pagamento' && modoModal !== 'formulario') fecharModal();
});
document.addEventListener('keydown', e => { if (e.key === 'Escape' && !el('modal-pagamento').hidden) fecharModal(); });
el('pag-corpo').addEventListener('click', e => {
  if (e.target.id === 'pag-copiar') copiar(el('pag-codigo').value);
  if (e.target.dataset.segundaVia && pagamentoAberto) {
    enviarPagamento({ contratoId: contrato.id, referenciaMes: pagamentoAberto.referenciaMes, metodo: pagamentoAberto.metodo });
  }
});
el('pag-corpo').addEventListener('submit', e => {
  e.preventDefault();
  if (modoModal === 'formulario') confirmarCartao();
});
el('pag-corpo').addEventListener('input', e => {
  const campo = e.target;
  if (campo.id === 'cc-numero') {
    const bruto = digitosDe(campo.value);
    const limite = (bandeiraDoNumero(bruto) || {}).id === 'amex' ? 15 : 19;
    const digitos = bruto.slice(0, limite);
    campo.value = formatarNumeroCartao(digitos, bandeiraDoNumero(digitos));
  } else if (campo.id === 'cc-nome') {
    campo.value = campo.value.toUpperCase();
  } else if (campo.id === 'cc-validade') {
    const digitos = digitosDe(campo.value).slice(0, 4);
    campo.value = digitos.length > 2 ? `${digitos.slice(0, 2)}/${digitos.slice(2)}` : digitos;
  } else if (campo.id === 'cc-cvv') {
    campo.value = digitosDe(campo.value).slice(0, 4);
  } else if (campo.id === 'cc-cpf') {
    const d = digitosDe(campo.value).slice(0, 11);
    campo.value = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1-$2');
    return;
  } else {
    return;
  }
  atualizarPrevia();
});

iniciar();
