const el = id => document.getElementById(id);

async function iniciar() {
  if (!Sessao.logado()) return;
  try {
    const [contratos, pagamentos] = await Promise.all([
      api('/contratos/meus?papel=locatario&size=100&sort=id,desc'),
      api('/pagamentos/meus?size=500&sort=id,desc')
    ]);
    const ativos = contratos.content.filter(c => c.status === 'ATIVO');
    const imoveis = await Promise.all(ativos.map(c => api(`/imoveis/${c.imovelId}`).catch(() => null)));
    const situacoes = ativos.map(c => calcularSituacao(c, pagamentos.content));

    el('n-imoveis').textContent = ativos.length;

    const proximos = ativos.map((contrato, i) => ({ contrato, s: situacoes[i] }))
      .filter(x => x.s.proximo)
      .sort((a, b) => a.s.proximo.vencimento - b.s.proximo.vencimento);
    if (proximos.length) {
      el('n-proximo').textContent = formatarMoeda(proximos[0].contrato.valorMensal);
      el('t-proximo').textContent = `${textoDoVencimento(proximos[0].s)} · ${proximos[0].contrato.tituloImovel}`;
    } else {
      el('n-proximo').textContent = ativos.length ? 'Em dia' : '–';
      el('t-proximo').textContent = ativos.length ? 'Nenhum pagamento em aberto' : 'Você ainda não alugou um imóvel';
    }

    const pendencias = situacoes.reduce((total, s) => total + s.pendentes, 0);
    el('n-pendencias').textContent = pendencias;
    el('t-pendencias').textContent = pendencias ? 'Há aluguel em atraso' : 'Tudo em dia por aqui';

    if (ativos.length === 0) {
      el('imoveis').innerHTML = '<div class="cartao" style="grid-column:1/-1"><p>Você ainda não tem imóveis alugados.</p>' +
        '<div class="acoes"><a class="btn btn-ouro" href="imoveis.html">Ver imóveis disponíveis</a></div></div>';
      return;
    }

    el('imoveis').innerHTML = ativos.map((c, i) => {
      const imovel = imoveis[i];
      const detalhes = imovel ? detalhesDoImovel(imovel) : '';
      const emAtraso = situacoes[i].pendentes > 0;
      return `
        <article class="cartao imovel">
          ${fotoImovel(c.imovelId)}
          <div class="corpo"><strong>${escaparHtml(c.tituloImovel)}</strong>
            <p class="preco">${formatarMoeda(c.valorMensal)}/mês</p>
            ${detalhes ? `<p class="info">${escaparHtml(detalhes)}</p>` : ''}
            <span class="selo ${emAtraso ? 'alerta' : ''}">${emAtraso ? '⚠ Pagamento em atraso' : '✓ Alugado'}</span>
            <div class="acoes"><a class="btn btn-ouro" href="pagamentos.html?contrato=${c.id}">Pagar aluguel</a>
              <a class="btn" href="imovel.html?id=${c.imovelId}#avaliacoes">Avaliar</a></div>
          </div>
        </article>`;
    }).join('');
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    mostrarAviso(el('erro'), e.message);
    el('imoveis').innerHTML = '';
  }
}

iniciar();
