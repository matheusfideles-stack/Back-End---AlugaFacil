// Mapa com Leaflet e OpenStreetMap (sem chave de API). Sem a biblioteca, as paginas seguem funcionando e mostram um
// aviso no lugar do mapa.
const CENTRO_PADRAO = [-18.8, -46.6];

function mapaDisponivel() {
  return typeof L !== 'undefined';
}

// rolarParaAmpliar false: a roda do mouse rola a pagina e so amplia o mapa depois de um clique nele.
function criarMapa(elemento, centro = CENTRO_PADRAO, zoom = 9, { rolarParaAmpliar = true } = {}) {
  const mapa = L.map(elemento, { zoomControl: true, scrollWheelZoom: rolarParaAmpliar }).setView(centro, zoom);
  if (!rolarParaAmpliar) mapa.once('click', () => mapa.scrollWheelZoom.enable());
  // Com o mapa afastado as etiquetas de preco se sobrepoem: viram pontos e voltam a ser precos ao aproximar.
  const marcarDistancia = () => mapa.getContainer().classList.toggle('mapa-longe', mapa.getZoom() < 11);
  mapa.on('zoomend', marcarDistancia);
  marcarDistancia();
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
  }).addTo(mapa);
  return mapa;
}

// Pino em forma de etiqueta com o preco, como nos sites de aluguel.
function iconePreco(imovel, ativo = false) {
  return L.divIcon({
    className: 'pino-preco' + (ativo ? ' ativo' : ''),
    html: `<span>${escaparHtml(formatarMoeda(imovel.valorAluguel))}</span>`,
    iconSize: [0, 0],
    iconAnchor: [0, 0]
  });
}

function popupDoImovel(imovel) {
  return `<div class="mapa-popup">
    ${fotoImovel(imovel.id)}
    <strong>${escaparHtml(imovel.titulo)}</strong>
    <span class="preco">${formatarMoeda(imovel.valorAluguel)}/mês</span>
    <span class="info">${escaparHtml(imovel.bairro)}, ${escaparHtml(imovel.cidade)}</span>
    <button type="button" class="btn btn-ouro" data-ir-imovel="${imovel.id}">Ver imóvel</button>
  </div>`;
}

// O pop-up nasce dentro do mapa, entao o clique dele e tratado aqui, uma vez so.
document.addEventListener('click', e => {
  const botao = e.target.closest('[data-ir-imovel]');
  if (botao) irParaImovel(botao.dataset.irImovel);
});
