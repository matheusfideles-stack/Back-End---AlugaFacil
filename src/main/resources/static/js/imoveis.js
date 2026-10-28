const el = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
let todos = [];

el('local').value = params.get('local') || '';
el('tipo').value = params.get('tipo') || '';
el('preco').value = params.get('preco') || '';
el('quartos').value = params.get('quartos') || '0';
el('ordem').value = params.get('ordem') || 'recentes';

function lerPreco(texto) {
  const limpo = String(texto).replace(/[^\d,.]/g, '');
  if (!limpo) return Infinity;
  const numero = parseFloat(limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo.replace(/\.(?=\d{3}\b)/g, ''));
  return Number.isFinite(numero) && numero > 0 ? numero : Infinity;
}

function filtrar() {
  const termo = normalizarTexto(el('local').value.trim());
  const tipo = el('tipo').value;
  const precoMaximo = lerPreco(el('preco').value);
  const quartosMinimo = Number(el('quartos').value);
  const ordem = el('ordem').value;

  const lista = todos.filter(i =>
    (!termo || normalizarTexto([i.cidade, i.bairro, i.titulo, i.logradouro, i.uf].join(' ')).includes(termo)) &&
    (!tipo || i.tipo === tipo) &&
    Number(i.valorAluguel) <= precoMaximo &&
    (i.quartos ?? 0) >= quartosMinimo);

  if (ordem === 'menor') lista.sort((a, b) => a.valorAluguel - b.valorAluguel);
  if (ordem === 'maior') lista.sort((a, b) => b.valorAluguel - a.valorAluguel);
  if (ordem === 'avaliacao') {
    lista.sort((a, b) => (b.mediaAvaliacoes ?? -1) - (a.mediaAvaliacoes ?? -1) || b.totalAvaliacoes - a.totalAvaliacoes);
  }
  return lista;
}

function atualizarEndereco() {
  const novo = new URLSearchParams();
  [['local', el('local').value.trim()], ['tipo', el('tipo').value], ['preco', el('preco').value.trim()],
    ['quartos', el('quartos').value === '0' ? '' : el('quartos').value], ['ordem', el('ordem').value === 'recentes' ? '' : el('ordem').value]]
    .forEach(([chave, valor]) => { if (valor) novo.set(chave, valor); });
  history.replaceState(null, '', novo.toString() ? `?${novo}` : location.pathname);
}

function renderizar() {
  const lista = filtrar();
  atualizarEndereco();

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

['local', 'preco'].forEach(id => el(id).addEventListener('input', renderizar));
['tipo', 'quartos', 'ordem'].forEach(id => el(id).addEventListener('change', renderizar));
el('filtros').addEventListener('submit', e => e.preventDefault());
el('limpar').addEventListener('click', () => {
  el('local').value = '';
  el('tipo').value = '';
  el('preco').value = '';
  el('quartos').value = '0';
  el('ordem').value = 'recentes';
  renderizar();
  el('local').focus();
});
el('vitrine').addEventListener('click', e => {
  const botao = e.target.closest('button[data-id]');
  if (botao) irParaImovel(botao.dataset.id, botao.dataset.ancora);
});

carregar();
