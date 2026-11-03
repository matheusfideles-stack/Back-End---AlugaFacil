const el = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
let todos = [];
let cidadeEscolhida = params.get('cidade') || '';
let bairroEscolhido = params.get('bairro') || '';
const comodidadesEscolhidas = new Set((params.get('comodidades') || '').split(',').filter(id => comodidadePorId(id)));
let limiteDoMapa = null;
let mapaDeImoveis = null;
let camadaDePinos = null;
let ajustandoMapa = false;
const pinos = new Map();

el('local').value = params.get('local') || '';
el('tipo').value = params.get('tipo') || '';
el('preco').value = params.get('preco') || '';
el('quartos').value = params.get('quartos') || '0';
el('ordem').value = params.get('ordem') || 'recentes';

function dentroDoMapa(imovel) {
  const posicao = posicaoDoImovel(imovel);
  return !!posicao && limiteDoMapa.contains([posicao.lat, posicao.lng]);
}

// comRegiao false devolve tudo que passa nos filtros, sem cortar pela area do mapa: e o que os pinos mostram.
function filtrar(comRegiao = true) {
  const termo = normalizarTexto(el('local').value.trim());
  const tipo = el('tipo').value;
  const precoMaximo = lerPreco(el('preco').value);
  const quartosMinimo = Number(el('quartos').value);
  const ordem = el('ordem').value;

  const lista = todos.filter(i =>
    (!cidadeEscolhida || mesmoTexto(i.cidade, cidadeEscolhida)) &&
    (!bairroEscolhido || mesmoTexto(i.bairro, bairroEscolhido)) &&
    (!termo || normalizarTexto([i.cidade, i.bairro, i.titulo, i.logradouro, i.uf].join(' ')).includes(termo)) &&
    (!tipo || i.tipo === tipo) &&
    Number(i.valorAluguel) <= precoMaximo &&
    (i.quartos ?? 0) >= quartosMinimo &&
    [...comodidadesEscolhidas].every(c => (i.comodidades || []).includes(c)) &&
    (!comRegiao || !limiteDoMapa || dentroDoMapa(i)));

  if (ordem === 'menor') lista.sort((a, b) => a.valorAluguel - b.valorAluguel);
  if (ordem === 'maior') lista.sort((a, b) => b.valorAluguel - a.valorAluguel);
  if (ordem === 'avaliacao') {
    lista.sort((a, b) => (b.mediaAvaliacoes ?? -1) - (a.mediaAvaliacoes ?? -1) || b.totalAvaliacoes - a.totalAvaliacoes);
  }
  return lista;
}

function atualizarEndereco() {
  const novo = new URLSearchParams();
  [['local', el('local').value.trim()], ['cidade', cidadeEscolhida], ['bairro', bairroEscolhido],
    ['comodidades', [...comodidadesEscolhidas].join(',')], ['tipo', el('tipo').value], ['preco', el('preco').value.trim()],
    ['quartos', el('quartos').value === '0' ? '' : el('quartos').value], ['ordem', el('ordem').value === 'recentes' ? '' : el('ordem').value]]
    .forEach(([chave, valor]) => { if (valor) novo.set(chave, valor); });
  history.replaceState(null, '', novo.toString() ? `?${novo}` : location.pathname);
}

// ---------- Seletor de cidade e bairros ----------

function bairrosDaCidade(cidade) {
  const contagem = new Map();
  todos.filter(i => mesmoTexto(i.cidade, cidade)).forEach(i => {
    contagem.set(i.bairro, (contagem.get(i.bairro) || 0) + 1);
  });
  return [...contagem.entries()].sort((a, b) => a[0].localeCompare(b[0], 'pt-BR'));
}

const seletorDeCidade = criarSeletorDeCidade({
  raiz: el('seletor-cidade'),
  rotuloId: 'rotulo-cidade',
  extras: () => todos.map(i => ({ nome: i.cidade, uf: i.uf })),
  bairros: bairrosDaCidade,
  aoMudar: (cidade, bairro) => {
    cidadeEscolhida = cidade;
    bairroEscolhido = bairro;
    renderizar();
  }
});
seletorDeCidade.definir(cidadeEscolhida, bairroEscolhido);

// ---------- Comodidades ----------

function desenharComodidades() {
  el('comodidades-grade').innerHTML = htmlCartoesDeComodidades(comodidadesEscolhidas);
}

el('comodidades-grade').addEventListener('click', e => {
  const botao = e.target.closest('[data-comodidade]');
  if (!botao) return;
  const id = botao.dataset.comodidade;
  if (comodidadesEscolhidas.has(id)) comodidadesEscolhidas.delete(id);
  else comodidadesEscolhidas.add(id);
  desenharComodidades();
  renderizar();
});

// ---------- Mapa ----------

function iniciarMapa() {
  if (!mapaDisponivel()) {
    el('mapa').innerHTML = '<p class="vazio" style="padding:24px">Não foi possível carregar o mapa agora.</p>';
    return;
  }
  mapaDeImoveis = criarMapa(el('mapa'));
  camadaDePinos = L.layerGroup().addTo(mapaDeImoveis);
  // Mexer no mapa a mao oferece buscar so naquela area; os ajustes feitos pelo proprio codigo nao contam.
  mapaDeImoveis.on('moveend', () => {
    if (!ajustandoMapa && todos.length) el('buscar-regiao').hidden = false;
  });
  el('buscar-regiao').addEventListener('click', () => {
    limiteDoMapa = mapaDeImoveis.getBounds();
    el('buscar-regiao').hidden = true;
    renderizar(false);
  });
  el('limpar-regiao').addEventListener('click', () => {
    limiteDoMapa = null;
    renderizar();
  });
}

function ajustarMapa(lista) {
  const pontos = lista.map(posicaoDoImovel).filter(Boolean).map(p => [p.lat, p.lng]);
  ajustandoMapa = true;
  if (pontos.length) mapaDeImoveis.fitBounds(pontos, { padding: [50, 50], maxZoom: 15, animate: false });
  else mapaDeImoveis.setView(CENTRO_PADRAO, 9, { animate: false });
  ajustandoMapa = false;
  el('buscar-regiao').hidden = true;
}

function desenharPinos(lista, reajustar) {
  if (!mapaDeImoveis) return;
  camadaDePinos.clearLayers();
  pinos.clear();
  lista.forEach(imovel => {
    const posicao = posicaoDoImovel(imovel);
    if (!posicao) return;
    const pino = L.marker([posicao.lat, posicao.lng], { icon: iconePreco(imovel), riseOnHover: true });
    pino.bindPopup(popupDoImovel(imovel), { minWidth: 190 });
    pino.on('click', () => destacarCard(imovel.id));
    pino.addTo(camadaDePinos);
    pinos.set(imovel.id, pino);
  });
  if (reajustar) ajustarMapa(lista);
}

function destacarCard(id) {
  const card = document.querySelector(`#vitrine [data-imovel="${id}"]`);
  if (!card) return;
  card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  card.classList.add('destacado');
  setTimeout(() => card.classList.remove('destacado'), 1800);
}

function marcarPino(id, ativo) {
  const pino = pinos.get(id);
  const imovel = todos.find(i => i.id === id);
  if (pino && imovel) {
    pino.setIcon(iconePreco(imovel, ativo));
    pino.setZIndexOffset(ativo ? 1000 : 0);
  }
}

el('vitrine').addEventListener('mouseover', e => {
  const card = e.target.closest('[data-imovel]');
  if (card && !card.contains(e.relatedTarget)) marcarPino(Number(card.dataset.imovel), true);
});
el('vitrine').addEventListener('mouseout', e => {
  const card = e.target.closest('[data-imovel]');
  if (card && !card.contains(e.relatedTarget)) marcarPino(Number(card.dataset.imovel), false);
});

// ---------- Lista ----------

function renderizar(reajustarMapa = true) {
  const lista = filtrar(true);
  atualizarEndereco();
  el('chip-regiao').hidden = !limiteDoMapa;

  if (todos.length === 0) {
    el('contagem').textContent = '';
    el('vitrine').innerHTML = '<p class="vazio">Ainda não há imóveis cadastrados.</p>';
    return;
  }
  el('contagem').textContent = lista.length === todos.length
    ? `${todos.length} ${todos.length === 1 ? 'imóvel disponível' : 'imóveis disponíveis'}`
    : `Mostrando ${lista.length} de ${todos.length} imóveis`;

  el('vitrine').innerHTML = lista.length
    ? lista.map(cardImovel).join('')
    : '<p class="vazio" style="grid-column:1/-1">Nenhum imóvel encontrado com esses filtros. Tente ampliar a busca ou clique em "Limpar".</p>';

  desenharPinos(filtrar(false), reajustarMapa);
}

async function carregar() {
  el('vitrine').innerHTML = '<p class="vazio">Carregando imóveis...</p>';
  try {
    let pagina = 0;
    let ultima = false;
    todos = [];
    while (!ultima) {
      const resposta = await api(`/imoveis?size=200&page=${pagina}&sort=id,desc`, { autenticado: false });
      todos = todos.concat(resposta.content);
      ultima = resposta.last;
      pagina++;
    }
    renderizar();
  } catch (erro) {
    el('vitrine').innerHTML = '';
    mostrarAviso(el('erro'), erro.message);
  }
}

['local', 'preco'].forEach(id => el(id).addEventListener('input', () => renderizar()));
['tipo', 'quartos', 'ordem'].forEach(id => el(id).addEventListener('change', () => renderizar()));
el('filtros').addEventListener('submit', e => e.preventDefault());
el('limpar').addEventListener('click', () => {
  el('local').value = '';
  el('tipo').value = '';
  el('preco').value = '';
  el('quartos').value = '0';
  el('ordem').value = 'recentes';
  cidadeEscolhida = '';
  bairroEscolhido = '';
  comodidadesEscolhidas.clear();
  limiteDoMapa = null;
  seletorDeCidade.definir('', '');
  desenharComodidades();
  renderizar();
  el('local').focus();
});
el('vitrine').addEventListener('click', e => {
  const botao = e.target.closest('button[data-id]');
  if (botao) irParaImovel(botao.dataset.id, botao.dataset.ancora);
});

desenharComodidades();
iniciarMapa();
carregar();
