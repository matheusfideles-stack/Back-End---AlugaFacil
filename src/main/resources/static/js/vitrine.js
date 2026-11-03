const vitrine = document.getElementById('vitrine');
const contagem = document.getElementById('contagem');
let todos = [];
let cidadeEscolhida = '';
const comodidadesEscolhidas = new Set();
let mapaDaHome = null;
let camadaDePinos = null;

async function carregarImoveis() {
  let pagina = 0;
  let ultima = false;
  todos = [];
  while (!ultima) {
    const resposta = await api(`/imoveis?size=200&page=${pagina}&sort=id,desc`, { autenticado: false });
    todos = todos.concat(resposta.content);
    ultima = resposta.last;
    pagina++;
  }
}

async function carregarVitrine() {
  vitrine.innerHTML = '<p class="vazio">Carregando imóveis...</p>';
  try {
    await carregarImoveis();
    if (todos.length === 0) {
      contagem.textContent = '';
      vitrine.innerHTML = '<p class="vazio">Ainda não há imóveis cadastrados.</p>';
      desenharMapa();
      return;
    }
    const total = todos.length;
    contagem.textContent = `${total} ${total === 1 ? 'imóvel disponível' : 'imóveis disponíveis'} · mostrando os mais recentes`;
    vitrine.innerHTML = todos.slice(0, 4).map(cardImovel).join('');
    desenharMapa();
  } catch (erro) {
    contagem.textContent = '';
    vitrine.innerHTML = `<p class="aviso">${escaparHtml(erro.message)}</p>`;
  }
}

// ---------- Mapa e comodidades ----------

// O mapa acompanha a cidade, o tipo, o preco e as comodidades escolhidos; "Buscar" leva tudo isso para a lista.
function imoveisDoMapa() {
  const tipo = document.getElementById('tipo').value;
  const precoMaximo = lerPreco(document.getElementById('preco').value);
  return todos.filter(i =>
    (!cidadeEscolhida || mesmoTexto(i.cidade, cidadeEscolhida)) &&
    (!tipo || i.tipo === tipo) &&
    Number(i.valorAluguel) <= precoMaximo &&
    [...comodidadesEscolhidas].every(c => (i.comodidades || []).includes(c)));
}

function enderecoDaLista() {
  const consulta = new URLSearchParams();
  [['cidade', cidadeEscolhida], ['tipo', document.getElementById('tipo').value],
    ['preco', document.getElementById('preco').value.trim()], ['comodidades', [...comodidadesEscolhidas].join(',')]]
    .forEach(([chave, valor]) => { if (valor) consulta.set(chave, valor); });
  return consulta.toString() ? `imoveis.html?${consulta}` : 'imoveis.html';
}

function iniciarMapa() {
  if (!mapaDisponivel()) {
    document.getElementById('mapa').innerHTML = '<p class="vazio" style="padding:24px">Não foi possível carregar o mapa agora.</p>';
    return;
  }
  mapaDaHome = criarMapa(document.getElementById('mapa'), CENTRO_PADRAO, 9, { rolarParaAmpliar: false });
  camadaDePinos = L.layerGroup().addTo(mapaDaHome);
}

function desenharMapa() {
  document.getElementById('ver-mapa-completo').href = enderecoDaLista();
  if (!mapaDaHome) return;

  const lista = imoveisDoMapa();
  camadaDePinos.clearLayers();
  const pontos = [];
  lista.forEach(imovel => {
    const posicao = posicaoDoImovel(imovel);
    if (!posicao) return;
    pontos.push([posicao.lat, posicao.lng]);
    L.marker([posicao.lat, posicao.lng], { icon: iconePreco(imovel) })
      .bindPopup(popupDoImovel(imovel), { minWidth: 190 })
      .addTo(camadaDePinos);
  });

  if (pontos.length) {
    mapaDaHome.fitBounds(pontos, { padding: [50, 50], maxZoom: 15, animate: false });
  } else {
    const centro = cidadeEscolhida ? centroDaCidade(cidadeEscolhida) : null;
    mapaDaHome.setView(centro ? [centro.lat, centro.lng] : CENTRO_PADRAO, centro ? 12 : 9, { animate: false });
  }

  document.getElementById('contagem-mapa').textContent = todos.length === 0 ? '' : (pontos.length
    ? `${pontos.length} ${pontos.length === 1 ? 'imóvel' : 'imóveis'} no mapa`
    : 'Nenhum imóvel com esses filtros. Tente tirar alguma comodidade.');
}

function desenharComodidades() {
  document.getElementById('comodidades-grade').innerHTML = htmlCartoesDeComodidades(comodidadesEscolhidas);
  document.getElementById('comodidades-campo').value = [...comodidadesEscolhidas].join(',');
}

document.getElementById('comodidades-grade').addEventListener('click', e => {
  const botao = e.target.closest('[data-comodidade]');
  if (!botao) return;
  const id = botao.dataset.comodidade;
  if (comodidadesEscolhidas.has(id)) comodidadesEscolhidas.delete(id);
  else comodidadesEscolhidas.add(id);
  desenharComodidades();
  desenharMapa();
});
document.getElementById('tipo').addEventListener('change', desenharMapa);
document.getElementById('preco').addEventListener('input', desenharMapa);

vitrine.addEventListener('click', e => {
  const botao = e.target.closest('button[data-id]');
  if (botao) irParaImovel(botao.dataset.id, botao.dataset.ancora);
});

const sessao = Sessao.dados();
if (sessao) {
  document.getElementById('hero-acoes').innerHTML =
    `<a class="btn btn-ouro" href="${paginaInicial(sessao.perfil)}">Ir para meu painel</a>` +
    '<a class="btn" href="imoveis.html">Ver imóveis</a>';
  document.getElementById('faixa-cta').hidden = true;
}

// A busca leva a cidade escolhida para a lista de imoveis, que filtra por ela.
const campoDeCidade = document.getElementById('cidade');
criarSeletorDeCidade({
  raiz: document.getElementById('seletor-cidade'),
  rotuloId: 'rotulo-cidade',
  aoMudar: cidade => {
    campoDeCidade.value = cidade;
    cidadeEscolhida = cidade;
    desenharMapa();
  }
});

desenharComodidades();
iniciarMapa();
carregarVitrine();
