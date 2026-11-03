const el = id => document.getElementById(id);
const ROTULO_STATUS_MANUTENCAO = { ABERTA: 'Aberta', EM_ANDAMENTO: 'Em andamento', CONCLUIDA: 'Concluída' };

let contratos = [];
let pagamentos = [];
let contratoDaManutencao = null;

function mostrarAba(nome) {
  document.querySelectorAll('.abas a[data-aba]').forEach(a => a.classList.toggle('ativo', a.dataset.aba === nome));
  el('ativos').hidden = nome !== 'ativos';
  el('historico').hidden = nome !== 'historico';
}

function cartaoAtivo(contrato) {
  const situacao = calcularSituacao(contrato, pagamentos);
  const proximo = situacao.proximo
    ? `${formatarData(dataIso(situacao.proximo.vencimento))}${situacao.proximo.atrasado ? ' (em atraso)' : ''}`
    : 'Tudo pago';
  return `
    <article class="cartao contrato">
      ${fotoImovel(contrato.imovelId)}
      <div class="corpo">
        <span class="selo ${situacao.pendentes ? 'alerta' : ''}">● ${situacao.pendentes ? 'Pagamento em atraso' : 'Contrato ativo'}</span>
        <h2 class="secao" style="margin:8px 0 0">${escaparHtml(contrato.tituloImovel)}</h2>
        <p class="info">Contrato nº ${contrato.id} · Válido até ${formatarData(contrato.dataFim)}</p>
        <div class="valores">
          <div><p class="rotulo">Valor mensal</p><strong>${formatarMoeda(contrato.valorMensal)}</strong></div>
          <div><p class="rotulo">Próximo vencimento</p><strong>${proximo}</strong></div>
        </div>
        <div class="acoes">
          <a class="btn btn-ouro" href="pagamentos.html?contrato=${contrato.id}">Pagar Aluguel</a>
          <a class="btn" href="imovel.html?id=${contrato.imovelId}#avaliacoes">Avaliar imóvel</a>
          <button type="button" class="btn" data-baixar="/contratos/${contrato.id}/contrato.pdf" data-arquivo="contrato-${contrato.id}.pdf">Contrato (PDF)</button>
          <button type="button" class="btn abrir-manutencao" data-contrato="${contrato.id}">Solicitar Manutenção</button>
          <button type="button" class="btn encerrar" data-contrato="${contrato.id}">Encerrar contrato</button>
        </div>
      </div>
    </article>`;
}

function cartaoEncerrado(contrato) {
  const pagos = pagamentos.filter(p => p.contratoId === contrato.id && p.status === 'PAGO');
  const ultimo = pagos.length ? formatarData(pagos[0].pagoEm || pagos[0].vencimento) : 'Nenhum';
  return `
    <article class="cartao contrato">
      ${fotoImovel(contrato.imovelId)}
      <div class="corpo">
        <span class="selo">● Encerrado</span>
        <h2 class="secao" style="margin:8px 0 0">${escaparHtml(contrato.tituloImovel)}</h2>
        <p class="info">Contrato nº ${contrato.id} · Iniciado em ${formatarData(contrato.dataInicio)}</p>
        <div class="valores">
          <div><p class="rotulo">Valor mensal</p><strong>${formatarMoeda(contrato.valorMensal)}</strong></div>
          <div><p class="rotulo">Último pagamento</p><strong>${ultimo}</strong></div>
        </div>
        <div class="acoes"><a class="btn" href="imovel.html?id=${contrato.imovelId}#avaliacoes">Avaliar imóvel</a></div>
      </div>
    </article>`;
}

function renderizar() {
  const ativos = contratos.filter(c => c.status === 'ATIVO');
  const encerrados = contratos.filter(c => c.status !== 'ATIVO');
  el('aba-ativos').textContent = `Contratos Ativos (${ativos.length})`;
  el('aba-historico').textContent = `Histórico (${encerrados.length})`;
  el('ativos').innerHTML = ativos.length
    ? ativos.map(cartaoAtivo).join('')
    : '<div class="cartao"><p>Você não tem contratos ativos.</p><div class="acoes"><a class="btn btn-ouro" href="imoveis.html">Ver imóveis disponíveis</a></div></div>';
  el('historico').innerHTML = encerrados.length
    ? encerrados.map(cartaoEncerrado).join('')
    : '<div class="cartao"><p class="info">Nenhum contrato encerrado.</p></div>';
}

async function carregarManutencoes() {
  try {
    const pagina = await api('/manutencoes/meus?size=50&sort=id,desc');
    el('manutencoes').innerHTML = pagina.content.length
      ? pagina.content.map(m => `
        <div class="linha">
          <div><strong>${escaparHtml(m.protocolo)} · ${escaparHtml(m.tipo)}</strong>
            <p class="info">${escaparHtml(m.tituloImovel)} · Urgência: ${m.urgencia === 'URGENTE' ? 'Urgente' : 'Normal'} · ${formatarData(m.criadoEm)}</p>
            <p class="info">${escaparHtml(m.descricao)}</p></div>
          <div><span class="selo ${m.status === 'CONCLUIDA' ? '' : 'alerta'}">${ROTULO_STATUS_MANUTENCAO[m.status] || m.status}</span></div>
        </div>`).join('')
      : '<p class="info">Nenhuma solicitação enviada.</p>';
  } catch (e) {
    el('manutencoes').innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
  }
}

async function carregar() {
  const [cts, pgs] = await Promise.all([
    api('/contratos/meus?papel=locatario&size=100&sort=id,desc'),
    api('/pagamentos/meus?size=500&sort=id,desc')
  ]);
  contratos = cts.content;
  pagamentos = pgs.content;
  renderizar();
}

function abrirManutencao(idContrato) {
  contratoDaManutencao = contratos.find(c => c.id === Number(idContrato));
  el('modal-imovel').textContent = `Imóvel: ${contratoDaManutencao.tituloImovel}`;
  el('form-manutencao').reset();
  el('m-aviso').hidden = true;
  el('modal-manutencao').hidden = false;
  el('m-tipo').focus();
}

function fecharManutencao() {
  el('modal-manutencao').hidden = true;
}

function erroManutencao(mensagem) {
  mostrarAviso(el('m-aviso'), mensagem);
}

el('ativos').addEventListener('click', async e => {
  const manutencao = e.target.closest('.abrir-manutencao');
  if (manutencao) return abrirManutencao(manutencao.dataset.contrato);

  const encerrar = e.target.closest('.encerrar');
  if (!encerrar) return;
  if (!confirm('Encerrar este contrato? O imóvel volta a ficar disponível para outros locatários.')) return;
  try {
    await api(`/contratos/${encerrar.dataset.contrato}/encerrar`, { metodo: 'PUT' });
    mostrarAviso(el('confirmacao'), 'Contrato encerrado.', 'ok');
    await carregar();
  } catch (erro) {
    mostrarAviso(el('erro'), erro.message);
  }
});

document.querySelectorAll('.abas a[data-aba]').forEach(a => a.addEventListener('click', e => {
  e.preventDefault();
  mostrarAba(a.dataset.aba);
}));

el('m-cancelar').addEventListener('click', fecharManutencao);
el('modal-manutencao').addEventListener('click', e => { if (e.target.id === 'modal-manutencao') fecharManutencao(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape' && !el('modal-manutencao').hidden) fecharManutencao(); });

el('form-manutencao').addEventListener('submit', async e => {
  e.preventDefault();
  if (!el('m-tipo').value) { erroManutencao('Escolha o tipo do problema.'); return el('m-tipo').focus(); }
  if (el('m-descricao').value.trim().length < 10) {
    erroManutencao('Descreva o problema com pelo menos 10 caracteres.');
    return el('m-descricao').focus();
  }
  try {
    const m = await api('/manutencoes', {
      metodo: 'POST',
      corpo: {
        contratoId: contratoDaManutencao.id,
        tipo: el('m-tipo').value,
        urgencia: el('m-urgencia').value === 'Urgente' ? 'URGENTE' : 'NORMAL',
        descricao: el('m-descricao').value.trim()
      }
    });
    fecharManutencao();
    mostrarAviso(el('confirmacao'),
      `Solicitação enviada! Protocolo ${m.protocolo} · ${m.tituloImovel} · ${m.tipo}. O proprietário já pode ver o pedido.`, 'ok');
    window.scrollTo({ top: 0, behavior: 'smooth' });
    await carregarManutencoes();
  } catch (erro) {
    erroManutencao(erro.message);
  }
});

(async function iniciar() {
  if (!Sessao.logado()) return;
  mostrarAba(location.hash === '#historico' ? 'historico' : 'ativos');
  try {
    await carregar();
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    mostrarAviso(el('erro'), e.message);
    el('ativos').innerHTML = '';
  }
  carregarManutencoes();
  iniciarSolicitacoes('locatario', document.getElementById('propostas'), document.getElementById('visitas'));
})();
