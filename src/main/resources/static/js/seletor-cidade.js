// Seletor de cidade (e, quando a pagina informa, de bairro): lista agrupada por estado, com busca e bolinhas de escolha.
// Usado na busca da pagina inicial (so cidades) e nos filtros da vitrine (cidades e bairros).
const mesmoTexto = (a, b) => normalizarTexto(a) === normalizarTexto(b);

// raiz: elemento vazio com id. extras(): cidades que aparecem nos anuncios mas nao estao em CIDADES, como [{ nome, uf }].
// bairros(cidade): bairros daquela cidade, como [[nome, quantidade]]. aoMudar(cidade, bairro): chamado a cada escolha.
function criarSeletorDeCidade({ raiz, rotuloId, extras = () => [], bairros = () => [], aoMudar = () => {} }) {
  let cidade = '';
  let bairro = '';

  raiz.innerHTML = `
    <button type="button" class="seletor-botao" aria-haspopup="listbox" aria-expanded="false"${rotuloId ? ` aria-labelledby="${rotuloId} ${raiz.id}-texto"` : ''}>
      <span id="${raiz.id}-texto">Selecione</span><span aria-hidden="true">⌄</span>
    </button>
    <div class="seletor-painel" hidden>
      <input type="search" placeholder="Buscar cidade..." autocomplete="off" aria-label="Buscar cidade">
      <div role="listbox" aria-label="Cidades"></div>
    </div>`;
  const botao = raiz.querySelector('.seletor-botao');
  const texto = raiz.querySelector(`#${raiz.id}-texto`);
  const painel = raiz.querySelector('.seletor-painel');
  const busca = painel.querySelector('input');
  const lista = painel.querySelector('[role="listbox"]');

  // As cidades da lista fixa vem primeiro, na ordem dela; as que so aparecem nos anuncios entram depois.
  function cidadesPorEstado() {
    const todas = new Map(CIDADES.map(c => [normalizarTexto(c.nome), { nome: c.nome, uf: c.uf }]));
    extras().forEach(c => {
      const chave = normalizarTexto(c.nome);
      if (!todas.has(chave)) todas.set(chave, c);
    });
    const grupos = new Map();
    todas.forEach(c => {
      if (!grupos.has(c.uf)) grupos.set(c.uf, []);
      grupos.get(c.uf).push(c);
    });
    return [...grupos.entries()];
  }

  function opcao(rotulo, atributos, marcada) {
    return `<button type="button" class="seletor-opcao" role="option" aria-selected="${marcada}" ${atributos}>` +
      `<span class="radio"></span><span>${escaparHtml(rotulo)}</span></button>`;
  }

  function desenhar() {
    const termo = normalizarTexto(busca.value.trim());
    let html = termo ? '' : opcao('Todas as cidades', 'data-cidade=""', !cidade);

    for (const [uf, cidades] of cidadesPorEstado()) {
      const visiveis = cidades.filter(c => !termo || normalizarTexto(c.nome).includes(termo));
      if (!visiveis.length) continue;
      html += `<p class="seletor-grupo">${escaparHtml((NOMES_ESTADOS[uf] || uf).toUpperCase())}</p>` +
        visiveis.map(c => opcao(c.nome, `data-cidade="${escaparHtml(c.nome)}"`, mesmoTexto(c.nome, cidade))).join('');
    }

    if (cidade) {
      const doBairro = bairros(cidade).filter(([nome]) => !termo || normalizarTexto(nome).includes(termo));
      if (doBairro.length) {
        html += `<p class="seletor-grupo">BAIRROS EM ${escaparHtml(cidade.toUpperCase())}</p>` +
          (termo ? '' : opcao('Todos os bairros', 'data-bairro=""', !bairro)) +
          doBairro.map(([nome, total]) => opcao(`${nome} (${total})`, `data-bairro="${escaparHtml(nome)}"`, mesmoTexto(nome, bairro))).join('');
      }
    }
    lista.innerHTML = html || '<p class="seletor-vazio">Nenhuma cidade encontrada.</p>';
  }

  function atualizarTexto() {
    texto.textContent = cidade ? (bairro ? `${bairro}, ${cidade}` : cidade) : 'Selecione';
    botao.classList.toggle('escolhido', !!cidade);
  }

  function abrir(abrir) {
    painel.hidden = !abrir;
    botao.setAttribute('aria-expanded', String(abrir));
    if (abrir) {
      busca.value = '';
      desenhar();
      busca.focus();
    }
  }

  botao.addEventListener('click', () => abrir(painel.hidden));
  busca.addEventListener('input', desenhar);
  lista.addEventListener('click', e => {
    const escolhida = e.target.closest('.seletor-opcao');
    if (!escolhida) return;
    if (escolhida.dataset.bairro !== undefined) {
      bairro = escolhida.dataset.bairro;
      abrir(false);
    } else {
      cidade = escolhida.dataset.cidade;
      bairro = '';
      // Escolher uma cidade mantem o painel aberto para escolher o bairro, se a pagina tiver bairros; "todas" fecha.
      if (!cidade || bairros(cidade).length === 0) abrir(false);
      else desenhar();
    }
    atualizarTexto();
    aoMudar(cidade, bairro);
  });
  // composedPath e calculado no instante do clique: a opcao clicada pode ja ter sido trocada na tela quando isto roda.
  document.addEventListener('click', e => {
    if (!painel.hidden && !e.composedPath().includes(raiz)) abrir(false);
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && !painel.hidden) {
      abrir(false);
      botao.focus();
    }
  });

  atualizarTexto();
  return {
    cidade: () => cidade,
    bairro: () => bairro,
    definir(novaCidade, novoBairro = '') {
      cidade = novaCidade;
      bairro = novoBairro;
      atualizarTexto();
    }
  };
}
