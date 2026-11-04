const CHAVE_TOKEN = 'alugafacil_token';

const Sessao = {
  salvar(token, lembrar = true) {
    this.limpar();
    (lembrar ? localStorage : sessionStorage).setItem(CHAVE_TOKEN, token);
  },
  limpar() {
    localStorage.removeItem(CHAVE_TOKEN);
    sessionStorage.removeItem(CHAVE_TOKEN);
  },
  token() {
    return sessionStorage.getItem(CHAVE_TOKEN) || localStorage.getItem(CHAVE_TOKEN);
  },
  dados() {
    const token = this.token();
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(atob(base64).split('').map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''));
      const dados = JSON.parse(json);
      if (dados.exp * 1000 <= Date.now()) {
        this.limpar();
        return null;
      }
      return dados;
    } catch (erro) {
      this.limpar();
      return null;
    }
  },
  logado() {
    return this.dados() !== null;
  }
};

class ErroApi extends Error {
  constructor(mensagem, status, codigo) {
    super(mensagem);
    this.status = status;
    this.codigo = codigo;
  }
}

const NOMES_CAMPOS = {
  nome: 'Nome', email: 'Email', cpf: 'CPF', telefone: 'Telefone', senha: 'Senha', novaSenha: 'Nova senha',
  perfil: 'Perfil', titulo: 'Título', tipo: 'Tipo', logradouro: 'Rua', numero: 'Número', bairro: 'Bairro',
  cidade: 'Cidade', uf: 'UF', cep: 'CEP', valorAluguel: 'Valor do aluguel', valorCondominio: 'Condomínio',
  quartos: 'Quartos', banheiros: 'Banheiros', vagas: 'Vagas', areaM2: 'Área', dataInicio: 'Início',
  dataFim: 'Fim', diaVencimento: 'Dia do vencimento', referenciaMes: 'Mês de referência', metodo: 'Método'
};

function traduzirErroCampo(erro) {
  const campo = NOMES_CAMPOS[erro.campo] || erro.campo;
  const mensagem = String(erro.mensagem || '');
  if (/blank|null|empty/i.test(mensagem)) return `${campo}: preenchimento obrigatório`;
  if (/e-?mail/i.test(mensagem)) return `${campo}: informe um email válido`;
  if (/size|between|length/i.test(mensagem)) return `${campo}: tamanho inválido (${mensagem})`;
  return `${campo}: ${mensagem}`;
}

function mensagemDeErro(status, corpo) {
  if (corpo && !Array.isArray(corpo) && typeof corpo === 'object' && corpo.mensagem) return corpo.mensagem;
  if (status === 429) return 'Muitas tentativas em pouco tempo. Aguarde alguns segundos e tente de novo.';
  if (status === 401) return 'Email ou senha inválidos, ou sessão expirada.';
  if (status === 403) return 'Você não tem permissão para fazer isso.';
  if (status === 404) return 'Não encontrado.';
  if (status >= 500 && status !== 502) return 'Erro no servidor. Tente novamente em instantes.';
  if (Array.isArray(corpo)) return corpo.map(traduzirErroCampo).join(' · ');
  if (typeof corpo === 'string' && corpo.trim()) return corpo;
  if (corpo && corpo.erro) return corpo.erro;
  return 'Não foi possível concluir a operação.';
}

async function api(caminho, { metodo = 'GET', corpo, autenticado = true } = {}) {
  const cabecalhos = {};
  const arquivos = corpo instanceof FormData;
  if (corpo !== undefined && !arquivos) cabecalhos['Content-Type'] = 'application/json';
  const token = Sessao.token();
  if (autenticado && token) cabecalhos.Authorization = `Bearer ${token}`;

  let resposta;
  try {
    resposta = await fetch(caminho, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : (arquivos ? corpo : JSON.stringify(corpo))
    });
  } catch (erro) {
    throw new ErroApi('Não foi possível conectar ao servidor. Confira se ele está rodando.', 0);
  }

  const texto = await resposta.text();
  let dados = texto;
  try {
    dados = texto ? JSON.parse(texto) : null;
  } catch (erro) {
    dados = texto;
  }

  if (!resposta.ok) {
    if (resposta.status === 401 && autenticado && token) {
      Sessao.limpar();
      throw new ErroApi('Sua sessão expirou. Entre novamente.', 401);
    }
    throw new ErroApi(mensagemDeErro(resposta.status, dados), resposta.status, dados && dados.codigo);
  }
  return dados;
}

function paginaInicial(perfil) {
  if (perfil === 'LOCATARIO') return 'cliente.html';
  return perfil === 'ADMIN' ? 'admin.html' : 'proprietario.html';
}

function destinoSeguro(valor) {
  return valor && /^[a-z0-9-]+\.html(\?[\w=&.%-]*)?(#[\w-]+)?$/i.test(valor) ? valor : null;
}

function paginaAtual() {
  return location.pathname.split('/').pop() + location.search;
}

function irParaLogin(motivo) {
  const alvo = encodeURIComponent(paginaAtual());
  location.replace(`login.html?redirect=${alvo}${motivo ? `&${motivo}=1` : ''}`);
}

function exigirSessao(perfisPermitidos) {
  const dados = Sessao.dados();
  if (!dados) {
    irParaLogin('expirou');
    return false;
  }
  if (perfisPermitidos && perfisPermitidos.length && !perfisPermitidos.includes(dados.perfil)) {
    location.replace(paginaInicial(dados.perfil));
    return false;
  }
  return true;
}

function sair() {
  Sessao.limpar();
  location.href = 'sair.html';
}

const formatarMoeda = valor => {
  const numero = Number(valor || 0);
  return numero.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: numero % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2
  });
};

const escaparHtml = texto => String(texto ?? '').replace(/[&<>"']/g, c => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[c]);

const rotuloTipo = {
  CASA: 'Casa',
  APARTAMENTO: 'Apartamento',
  KITNET: 'Kitnet',
  SALA_COMERCIAL: 'Sala comercial',
  GALPAO: 'Galpão'
};

// ---- Cidades atendidas e comodidades: a mesma lista nos filtros, no cadastro do imovel e no mapa.
// lat e lng sao o centro de cada cidade; servem de posicao de reserva quando o imovel ainda nao foi localizado.
const CIDADES = [
  { nome: 'Patos de Minas', uf: 'MG', lat: -18.6041, lng: -46.5097 },
  { nome: 'Lagoa Formosa', uf: 'MG', lat: -18.7742, lng: -46.4083 },
  { nome: 'Nova Ponte', uf: 'MG', lat: -19.1692, lng: -47.6718 },
  { nome: 'Tiros', uf: 'MG', lat: -19.0039, lng: -45.9636 }
];
const NOMES_ESTADOS = { MG: 'Minas Gerais' };
// Icones de traco, no mesmo estilo dos icones do site: 24x24, cor herdada do texto.
const ICONE = conteudo => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${conteudo}</svg>`;

const COMODIDADES = [
  { id: 'PISCINA', rotulo: 'Piscina', icone: ICONE('<path d="M2 16c1.7 0 1.7-1.2 3.3-1.2S7 16 8.7 16s1.7-1.2 3.3-1.2S13.7 16 15.3 16s1.7-1.2 3.3-1.2S20.3 16 22 16"/><path d="M2 20c1.7 0 1.7-1.2 3.3-1.2S7 20 8.7 20s1.7-1.2 3.3-1.2S13.7 20 15.3 20s1.7-1.2 3.3-1.2S20.3 20 22 20"/><path d="M8 12V4m0 4h3"/>') },
  { id: 'ESTACIONAMENTO_GRATUITO', rotulo: 'Estacionamento gratuito', icone: ICONE('<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M9 17V7h3.5a3 3 0 0 1 0 6H9"/>') },
  { id: 'MAQUINA_DE_LAVAR', rotulo: 'Máquina de lavar', icone: ICONE('<rect x="4" y="2.5" width="16" height="19" rx="2"/><path d="M4 6.5h16"/><circle cx="12" cy="13.5" r="4.5"/><path d="M7.5 5h.01"/>') },
  { id: 'AR_CONDICIONADO', rotulo: 'Ar-condicionado', icone: ICONE('<path d="M12 2.5v19"/><path d="M4.2 7.2l15.6 9.6"/><path d="M4.2 16.8l15.6-9.6"/>') },
  { id: 'LAREIRA', rotulo: 'Lareira', icone: ICONE('<path d="M12 3c.5 3 4.5 5.2 4.5 9.5a4.5 4.5 0 0 1-9 0c0-2.2 1-3.6 2-4.6.2 1.8 1 2.8 2 3.1C11.4 8.2 11.6 5.6 12 3z"/><path d="M5 21h14"/>') },
  { id: 'TV', rotulo: 'TV', icone: ICONE('<rect x="2.5" y="6" width="19" height="12.5" rx="2"/><path d="M8 21.5h8"/><path d="M9 3l3 3 3-3"/>') },
  { id: 'AQUECEDOR', rotulo: 'Aquecedor', icone: ICONE('<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7.5 8v8M12 8v8M16.5 8v8"/>') },
  { id: 'CHURRASQUEIRA', rotulo: 'Churrasqueira', icone: ICONE('<path d="M3.5 11h17a8.5 8.5 0 0 1-17 0z"/><path d="M8 19.5l-1 2.5M16 19.5l1 2.5M12 11V4"/>') }
];

function comodidadePorId(id) {
  return COMODIDADES.find(c => c.id === id) || null;
}

function htmlComodidades(ids) {
  return (ids || []).map(comodidadePorId).filter(Boolean)
    .map(c => `<span class="comodidade-chip"><span aria-hidden="true">${c.icone}</span> ${escaparHtml(c.rotulo)}</span>`).join('');
}

function centroDaCidade(nome) {
  const alvo = normalizarTexto(nome);
  return CIDADES.find(c => normalizarTexto(c.nome) === alvo) || null;
}

function aleatorioFixo(semente) {
  const x = Math.sin(semente * 12.9898) * 43758.5453;
  return x - Math.floor(x);
}

// Posicao exata quando o imovel foi localizado; senao, o centro da cidade com um pequeno desvio fixo por imovel, para os
// pinos nao ficarem empilhados. Cidade desconhecida: sem posicao (nao aparece no mapa).
function posicaoDoImovel(imovel) {
  if (imovel.latitude != null && imovel.longitude != null) {
    return { lat: imovel.latitude, lng: imovel.longitude, aproximada: false };
  }
  const centro = centroDaCidade(imovel.cidade);
  if (!centro) return null;
  return {
    lat: centro.lat + (aleatorioFixo(Number(imovel.id) + 101) - 0.5) * 0.024,
    lng: centro.lng + (aleatorioFixo(Number(imovel.id)) - 0.5) * 0.024,
    aproximada: true
  };
}

// Baixa um arquivo que exige login (PDF do contrato ou do recibo): o link simples nao enviaria o token.
async function baixarArquivo(caminho, nome, botao) {
  const textoOriginal = botao ? botao.textContent : '';
  if (botao) {
    botao.disabled = true;
    botao.textContent = 'Baixando...';
  }
  try {
    const resposta = await fetch(caminho, { headers: { Authorization: `Bearer ${Sessao.token()}` } });
    if (!resposta.ok) {
      let mensagem = 'Não foi possível baixar o arquivo.';
      try {
        mensagem = mensagemDeErro(resposta.status, await resposta.json());
      } catch (erro) {
        // corpo sem JSON: fica a mensagem padrao
      }
      throw new Error(mensagem);
    }
    const url = URL.createObjectURL(await resposta.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  } catch (erro) {
    alert(erro.message);
  } finally {
    if (botao) {
      botao.disabled = false;
      botao.textContent = textoOriginal;
    }
  }
}

document.addEventListener('click', e => {
  const botao = e.target.closest('[data-baixar]');
  if (botao) baixarArquivo(botao.dataset.baixar, botao.dataset.arquivo, botao);
});

// Preco digitado pelo usuario ("3000", "3.000,50", "R$ 3000"); vazio ou invalido nao limita nada.
function lerPreco(texto) {
  const limpo = String(texto).replace(/[^\d,.]/g, '');
  if (!limpo) return Infinity;
  const numero = parseFloat(limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo.replace(/\.(?=\d{3}\b)/g, ''));
  return Number.isFinite(numero) && numero > 0 ? numero : Infinity;
}

// Cartoes de comodidade para os filtros (a pagina escuta o clique em [data-comodidade]).
function htmlCartoesDeComodidades(escolhidas) {
  return COMODIDADES.map(c =>
    `<button type="button" class="comodidade-card${escolhidas.has(c.id) ? ' ativo' : ''}" data-comodidade="${c.id}" ` +
    `aria-pressed="${escolhidas.has(c.id)}"><span class="icone" aria-hidden="true">${c.icone}</span>` +
    `<span>${escaparHtml(c.rotulo)}</span></button>`).join('');
}

const NOMES_MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
const MESES_CURTOS = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

function nomeDoMes(referencia) {
  const [ano, mes] = referencia.split('-').map(Number);
  return `${NOMES_MESES[mes - 1]} de ${ano}`;
}

function mesAtual() {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`;
}

function somarMeses(referencia, quantidade) {
  const [ano, mes] = referencia.split('-').map(Number);
  const data = new Date(ano, mes - 1 + quantidade, 1);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

function formatarData(iso) {
  if (!iso) return '';
  const [ano, mes, dia] = iso.slice(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

function dataIso(data) {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function enderecoCompleto(imovel) {
  const complemento = imovel.complemento ? ` - ${imovel.complemento}` : '';
  return `${imovel.logradouro}, ${imovel.numero}${complemento}`;
}

function detalhesDoImovel(imovel) {
  const partes = [];
  if (imovel.quartos != null) partes.push(`${imovel.quartos} ${imovel.quartos === 1 ? 'quarto' : 'quartos'}`);
  if (imovel.banheiros != null) partes.push(`${imovel.banheiros} ${imovel.banheiros === 1 ? 'banheiro' : 'banheiros'}`);
  if (imovel.areaM2 != null) partes.push(`${imovel.areaM2} m²`);
  return partes.join(' · ');
}

// Foto real do imovel (a capa escolhida pelo proprietario). Sem fotos enviadas, cai numa foto ficticia fixa pelo id.
const FOTOS_FICTICIAS = ['img/casa-1.png', 'img/casa-2.webp', 'img/casa-3.png', 'img/casa-4.png'];

function fotoFicticia(id) {
  return FOTOS_FICTICIAS[Math.abs(Number(id) || 0) % FOTOS_FICTICIAS.length];
}

function fotoImovel(id, classeExtra = '') {
  return `<div class="foto ${classeExtra}"><img src="/imoveis/${Number(id)}/capa" alt="" loading="lazy" onerror="this.onerror=null;this.src='${fotoFicticia(id)}'"></div>`;
}

const ROTULO_NOTA = ['Péssimo', 'Ruim', 'Regular', 'Bom', 'Muito bom', 'Excelente'];

function formatarNota(nota) {
  return Number(nota).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

// Estrelas preenchidas por CSS: a nota (inclusive fracionada, como a media) vira a largura do preenchimento.
function estrelas(nota, classe = '') {
  const valor = Math.max(0, Math.min(5, Number(nota) || 0));
  return `<span class="estrelas ${classe}" style="--nota:${valor}" role="img" aria-label="${formatarNota(valor)} de 5 estrelas">★★★★★</span>`;
}

// Nos cards a linha de estrelas e um botao que abre o imovel ja na area de avaliacoes.
function resumoDeAvaliacao(imovel, comLink = false) {
  const conteudo = imovel.totalAvaliacoes > 0
    ? `${estrelas(imovel.mediaAvaliacoes)} <strong>${formatarNota(imovel.mediaAvaliacoes)}</strong> <span class="info">(${imovel.totalAvaliacoes})</span>`
    : '<span class="info">Sem avaliações ainda</span>';
  return comLink
    ? `<button type="button" class="avaliacao link-avaliacao" data-id="${imovel.id}" data-ancora="avaliacoes" title="Ver e fazer avaliações">${conteudo}</button>`
    : `<p class="avaliacao">${conteudo}</p>`;
}

function normalizarTexto(texto) {
  return String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function cardImovel(imovel) {
  const detalhes = detalhesDoImovel(imovel);
  return `
    <article class="cartao imovel" data-imovel="${imovel.id}">
      ${fotoImovel(imovel.id)}
      <div class="corpo">
        <strong>${escaparHtml(imovel.titulo)}</strong>
        ${resumoDeAvaliacao(imovel, true)}
        <p class="preco">${formatarMoeda(imovel.valorAluguel)}/mês</p>
        <p class="info">${escaparHtml(rotuloTipo[imovel.tipo] || imovel.tipo)} · ${escaparHtml(imovel.bairro)}, ${escaparHtml(imovel.cidade)}/${escaparHtml(imovel.uf)}</p>
        ${detalhes ? `<p class="info">${escaparHtml(detalhes)}</p>` : ''}
        <span class="selo">✓ Disponível</span>
        <div class="acoes"><button class="btn btn-ouro" type="button" data-id="${imovel.id}">Alugar</button>
          <button class="btn" type="button" data-id="${imovel.id}" data-ancora="avaliacoes">Avaliações</button></div>
      </div>
    </article>`;
}

function mostrarAviso(elemento, mensagem, tipo = 'erro') {
  elemento.textContent = mensagem;
  elemento.className = 'aviso' + (tipo === 'ok' ? ' ok' : '');
  elemento.hidden = !mensagem;
}

function irParaImovel(id, ancora = '') {
  const destino = `imovel.html?id=${id}${ancora ? '#' + ancora : ''}`;
  // A pagina do imovel e publica: o login so e pedido na hora de alugar ou avaliar.
  location.href = destino;
}

function montarAcoesDoTopo(elemento) {
  if (!elemento) return;
  const dados = Sessao.dados();
  if (!dados) {
    elemento.innerHTML = '<a class="btn" href="login.html" style="margin: 2px;">Entrar</a>' +
      '<a class="btn btn-ouro" href="login.html?aba=cadastro">Criar Conta</a>';
    return;
  }
  const primeiroNome = escaparHtml(String(dados.nome || '').split(' ')[0]);
  elemento.innerHTML = `<a class="btn" href="${paginaInicial(dados.perfil)}" style="margin: 2px;">Olá, ${primeiroNome}</a>` +
    '<a class="btn btn-ouro" href="sair.html">Sair</a>';
}

function atualizarBotaoSessao(botao) {
  if (botao && Sessao.logado()) {
    botao.textContent = 'Sair';
    botao.href = 'sair.html';
  }
}

function preencherSaudacao() {
  const dados = Sessao.dados();
  if (!dados) return;
  const primeiroNome = String(dados.nome || '').split(' ')[0];
  document.querySelectorAll('[data-saudacao]').forEach(el => {
    el.textContent = el.dataset.saudacao === 'curta' ? `Olá, ${primeiroNome}` : `Bem-vindo, ${primeiroNome}!`;
  });
}

function mascararCpf(valor) {
  const d = valor.replace(/\D/g, '').slice(0, 11);
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1-$2');
}

function mascararTelefone(valor) {
  const d = valor.replace(/\D/g, '').slice(0, 11);
  if (d.length <= 2) return d ? `(${d}` : '';
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

function montarMenu(nav) {
  if (!nav) return;
  const dados = Sessao.dados();
  const atual = location.pathname.split('/').pop() || 'index.html';
  let itens;
  if (dados && dados.perfil === 'LOCATARIO') {
    itens = [['Início', 'cliente.html'], ['Imóveis', 'imoveis.html'], ['Meus Aluguéis', 'alugueis.html'],
      ['Pagamentos', 'pagamentos.html'], ['Ajuda', 'ajuda.html']];
  } else if (dados && dados.perfil === 'LOCADOR') {
    itens = [['Visão Geral', 'proprietario.html'], ['Meus Imóveis', 'meus-imoveis.html'], ['Financeiro', 'financeiro.html'],
      ['Inquilinos', 'inquilinos.html'], ['Ajuda', 'proprietario-ajuda.html']];
  } else if (dados && dados.perfil === 'ADMIN') {
    itens = [['Proprietários', 'admin.html#proprietarios', 'proprietarios'], ['Usuários', 'admin.html#usuarios', 'usuarios'],
      ['Imóveis', 'admin.html#imoveis', 'imoveis'], ['Avaliações', 'admin.html#avaliacoes', 'avaliacoes'],
      ['Contratos', 'admin.html#contratos', 'contratos'], ['Pagamentos', 'admin.html#pagamentos', 'pagamentos'],
      ['Vitrine', 'imoveis.html']];
  } else if (dados) {
    itens = [['Meu painel', paginaInicial(dados.perfil)], ['Imóveis', 'imoveis.html'], ['Ajuda', 'ajuda.html']];
  } else {
    itens = [['Início', 'index.html'], ['Imóveis', 'imoveis.html'], ['Ajuda', 'ajuda.html']];
  }
  nav.innerHTML = itens.map(([rotulo, destino, aba]) =>
    `<a href="${destino}"${aba ? ` data-aba="${aba}"` : ''}${destino === atual ? ' class="ativo"' : ''}>${rotulo}</a>`).join('');
}

function preencherRodape() {
  document.querySelectorAll('[data-ano]').forEach(el => { el.textContent = new Date().getFullYear(); });
}

(function protegerPagina() {
  const exige = document.body && document.body.dataset.exige;
  if (exige) {
    if (exigirSessao(exige.split(','))) preencherSaudacao();
  }
  montarMenu(document.getElementById('menu-principal'));
  montarAcoesDoTopo(document.getElementById('topo-acoes'));
  preencherRodape();
})();

function vencimentoDoMes(contrato, mes) {
  const [ano, numeroMes] = mes.split('-').map(Number);
  const ultimoDia = new Date(ano, numeroMes, 0).getDate();
  return new Date(ano, numeroMes - 1, Math.min(contrato.diaVencimento, ultimoDia));
}

function calcularSituacao(contrato, pagamentos, hoje = new Date()) {
  const pagos = new Set(pagamentos
    .filter(p => p.contratoId === contrato.id && p.status === 'PAGO')
    .map(p => p.referenciaMes));
  const inicio = new Date(`${contrato.dataInicio}T00:00:00`);
  const fim = new Date(`${contrato.dataFim}T00:00:00`);
  const hojeSemHora = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());

  let mes = contrato.dataInicio.slice(0, 7);
  let pendentes = 0;
  let proximo = null;
  for (let i = 0; i < 120; i++) {
    const vencimento = vencimentoDoMes(contrato, mes);
    if (vencimento > fim) break;
    if (vencimento >= inicio && !pagos.has(mes)) {
      const atrasado = vencimento < hojeSemHora;
      if (atrasado) pendentes++;
      if (!proximo) proximo = { mes, vencimento, atrasado };
      if (!atrasado) break;
    }
    mes = somarMeses(mes, 1);
  }

  const dias = proximo ? Math.round((proximo.vencimento - hojeSemHora) / 86400000) : null;
  return { pendentes, proximo, dias };
}

function textoDoVencimento(situacao) {
  if (!situacao.proximo) return 'Tudo pago até o fim do contrato';
  const dias = situacao.dias;
  if (dias < 0) return `Venceu há ${-dias} ${-dias === 1 ? 'dia' : 'dias'}`;
  if (dias === 0) return 'Vence hoje';
  return `Vencimento em ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
}
