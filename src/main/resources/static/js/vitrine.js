const vitrine = document.getElementById('vitrine');
const contagem = document.getElementById('contagem');

async function carregarVitrine() {
  vitrine.innerHTML = '<p class="vazio">Carregando imóveis...</p>';
  try {
    const pagina = await api('/imoveis?size=4&sort=id,desc', { autenticado: false });
    if (pagina.content.length === 0) {
      contagem.textContent = '';
      vitrine.innerHTML = '<p class="vazio">Ainda não há imóveis cadastrados.</p>';
      return;
    }
    const total = pagina.totalElements;
    contagem.textContent = `${total} ${total === 1 ? 'imóvel disponível' : 'imóveis disponíveis'} · mostrando os mais recentes`;
    vitrine.innerHTML = pagina.content.map(cardImovel).join('');
  } catch (erro) {
    contagem.textContent = '';
    vitrine.innerHTML = `<p class="aviso">${escaparHtml(erro.message)}</p>`;
  }
}

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

carregarVitrine();
