// Vitrine da página inicial: lista os imóveis fictícios
const vitrine = document.getElementById('vitrine');
const contagem = document.getElementById('contagem');
let todosImoveis = [];

function cardImovel(imovel) {
  return `
    <article class="cartao imovel">
      ${fotoImovel(imovel.id)}
      <div class="corpo">
        <strong>${escaparHtml(imovel.titulo)}</strong>
        <p class="preco">${formatarMoeda(imovel.valorAluguel)}/mês</p>
        <p class="info">${escaparHtml(rotuloTipo[imovel.tipo] || imovel.tipo)} · ${escaparHtml(imovel.bairro)}, ${escaparHtml(imovel.cidade)}/${escaparHtml(imovel.uf)}</p>
        <span class="selo">✓ Disponível</span>
        <div class="acoes"><button class="btn btn-ouro" type="button" data-id="${imovel.id}">Alugar</button></div>
      </div>
    </article>`;
}

function renderizar(lista) {
  const singular = lista.length === 1;
  contagem.textContent = `Mostrando ${lista.length} ${singular ? 'imóvel disponível' : 'imóveis disponíveis'}`;

  if (lista.length === 0) {
    vitrine.innerHTML = `<p class="vazio">${todosImoveis.length === 0
      ? 'Ainda não há imóveis cadastrados.'
      : 'Nenhum imóvel encontrado com esses filtros.'}</p>`;
    return;
  }
  vitrine.innerHTML = lista.map(cardImovel).join('');
}

// Filtra a lista já carregada pelos campos da busca
function aplicarFiltros() {
  const local = document.getElementById('local').value.trim().toLowerCase();
  const tipo = document.getElementById('tipo').value;
  const precoMaximo = Number(document.getElementById('preco').value.replace(/\D/g, '')) || Infinity;

  renderizar(todosImoveis.filter(imovel =>
    (!local || imovel.cidade.toLowerCase().includes(local) || imovel.bairro.toLowerCase().includes(local)) &&
    (!tipo || imovel.tipo === tipo) &&
    Number(imovel.valorAluguel) <= precoMaximo
  ));
}

async function carregarVitrine() {
  vitrine.innerHTML = '<p class="vazio">Carregando imóveis...</p>';
  try {
    const { lista } = buscarImoveis();
    todosImoveis = lista;
    renderizar(todosImoveis);
  } catch (erro) {
    contagem.textContent = '';
    vitrine.innerHTML = `<p class="aviso">${escaparHtml(erro.message)}</p>`;
  }
}

document.getElementById('filtros').addEventListener('submit', e => {
  e.preventDefault();
  aplicarFiltros();
});

vitrine.addEventListener('click', e => {
  const botao = e.target.closest('button[data-id]');
  if (botao) irParaImovel(botao.dataset.id);
});

atualizarBotaoSessao(document.getElementById('btn-sessao'));
carregarVitrine();