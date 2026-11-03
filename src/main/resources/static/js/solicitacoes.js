// Propostas de aluguel e pedidos de visita: a mesma lista serve aos dois lados.
// papel 'locatario': mostra as que a pessoa enviou, com "Cancelar". papel 'locador': as recebidas, com "Aceitar/Recusar"
// (proposta) e "Confirmar/Recusar" (visita).
const ROTULO_PROPOSTA = { PENDENTE: 'Aguardando resposta', ACEITA: 'Aceita', RECUSADA: 'Recusada', CANCELADA: 'Cancelada' };
const ROTULO_VISITA = { SOLICITADA: 'Aguardando resposta', CONFIRMADA: 'Confirmada', RECUSADA: 'Não confirmada', CANCELADA: 'Cancelada' };
const CLASSE_SELO = { PENDENTE: 'alerta', SOLICITADA: 'alerta', ACEITA: '', CONFIRMADA: '', RECUSADA: 'erro', CANCELADA: 'erro' };

const configuracaoDeSolicitacoes = { papel: null, destinoPropostas: null, destinoVisitas: null };

function dataEHora(iso) {
  return `${formatarData(iso)} às ${String(iso).slice(11, 16)}`;
}

function seloDeSolicitacao(status, rotulos) {
  return `<span class="selo ${CLASSE_SELO[status] ?? ''}">${escaparHtml(rotulos[status] || status)}</span>`;
}

function botaoDeSolicitacao(acao, id, texto, classe = '') {
  return `<button type="button" class="btn ${classe}" data-solicitacao="${acao}" data-id="${id}">${texto}</button>`;
}

function acoesDaProposta(p, papel) {
  if (papel === 'locador' && p.status === 'PENDENTE') {
    return botaoDeSolicitacao('aceitar-proposta', p.id, 'Aceitar', 'btn-ouro') + botaoDeSolicitacao('recusar-proposta', p.id, 'Recusar');
  }
  if (papel === 'locatario' && p.status === 'PENDENTE') return botaoDeSolicitacao('cancelar-proposta', p.id, 'Cancelar');
  if (papel === 'locatario' && p.status === 'ACEITA' && p.contratoId) {
    return `<a class="btn btn-ouro" href="pagamentos.html?contrato=${p.contratoId}&novo=1">Pagar aluguel</a>`;
  }
  return '';
}

function linhaDeProposta(p, papel) {
  const quem = papel === 'locador'
    ? `<p class="info">${escaparHtml(p.nomeLocatario)} · ${escaparHtml(p.emailLocatario)}${p.telefoneLocatario ? ' · ' + escaparHtml(p.telefoneLocatario) : ''}</p>` : '';
  return `<div class="linha-admin solicitacao">
    <div><strong>${escaparHtml(p.tituloImovel)}</strong>${quem}
      <p class="info">${formatarData(p.dataInicio)} a ${formatarData(p.dataFim)} · vence dia ${escaparHtml(p.diaVencimento)}</p></div>
    <div><p class="info">${formatarMoeda(p.valorMensal)}/mês</p>
      ${p.mensagem ? `<p class="info">“${escaparHtml(p.mensagem)}”</p>` : ''}
      ${p.motivoRecusa ? `<p class="info">Motivo: ${escaparHtml(p.motivoRecusa)}</p>` : ''}</div>
    <div>${seloDeSolicitacao(p.status, ROTULO_PROPOSTA)}</div>
    <div class="acoes acoes-quebra">${acoesDaProposta(p, papel)}</div>
  </div>`;
}

function acoesDaVisita(v, papel) {
  if (papel === 'locador' && v.status === 'SOLICITADA') {
    return botaoDeSolicitacao('confirmar-visita', v.id, 'Confirmar', 'btn-ouro') + botaoDeSolicitacao('recusar-visita', v.id, 'Recusar');
  }
  if (papel === 'locatario' && (v.status === 'SOLICITADA' || v.status === 'CONFIRMADA')) {
    return botaoDeSolicitacao('cancelar-visita', v.id, 'Cancelar');
  }
  return '';
}

function linhaDeVisita(v, papel) {
  const quem = papel === 'locador'
    ? `<p class="info">${escaparHtml(v.nomeLocatario)}${v.telefoneLocatario ? ' · ' + escaparHtml(v.telefoneLocatario) : ''}</p>` : '';
  return `<div class="linha-admin solicitacao">
    <div><strong>${escaparHtml(v.tituloImovel)}</strong>${quem}</div>
    <div><p class="info">Visita em ${dataEHora(v.dataHora)}</p>${v.mensagem ? `<p class="info">“${escaparHtml(v.mensagem)}”</p>` : ''}</div>
    <div>${seloDeSolicitacao(v.status, ROTULO_VISITA)}</div>
    <div class="acoes acoes-quebra">${acoesDaVisita(v, papel)}</div>
  </div>`;
}

async function listarSolicitacoes(destino, caminho, linha, papel, vazio) {
  try {
    const pagina = await api(`${caminho}?size=50&sort=id,desc`);
    destino.innerHTML = pagina.content.length ? pagina.content.map(item => linha(item, papel)).join('') : `<p class="info">${vazio}</p>`;
  } catch (e) {
    destino.innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
  }
}

async function carregarSolicitacoes() {
  const { papel, destinoPropostas, destinoVisitas } = configuracaoDeSolicitacoes;
  const sufixo = papel === 'locador' ? 'recebidas' : 'minhas';
  await Promise.all([
    listarSolicitacoes(destinoPropostas, `/propostas/${sufixo}`, linhaDeProposta, papel,
      papel === 'locador' ? 'Nenhuma proposta recebida ainda.' : 'Você ainda não enviou propostas. Escolha um imóvel e clique em "Fazer proposta de aluguel".'),
    listarSolicitacoes(destinoVisitas, `/visitas/${sufixo}`, linhaDeVisita, papel,
      papel === 'locador' ? 'Nenhum pedido de visita.' : 'Você ainda não pediu visitas.')
  ]);
}

function iniciarSolicitacoes(papel, destinoPropostas, destinoVisitas) {
  Object.assign(configuracaoDeSolicitacoes, { papel, destinoPropostas, destinoVisitas });
  return carregarSolicitacoes();
}

function avisarSolicitacao(mensagem, tipo = 'erro') {
  const aviso = document.getElementById('erro');
  if (aviso) mostrarAviso(aviso, mensagem, tipo);
  else alert(mensagem);
}

document.addEventListener('click', async e => {
  const botao = e.target.closest('[data-solicitacao]');
  if (!botao) return;
  const { solicitacao: acao, id } = botao.dataset;
  const chamadas = {
    'aceitar-proposta': () => confirm('Aceitar esta proposta? O contrato começa agora e o imóvel sai da busca.')
      && api(`/propostas/${id}/aceitar`, { metodo: 'PUT' }),
    'recusar-proposta': () => {
      const motivo = prompt('Motivo da recusa (opcional). O interessado recebe este texto por e-mail:');
      return motivo !== null && api(`/propostas/${id}/recusar`, { metodo: 'PUT', corpo: { motivo: motivo.trim() || null } });
    },
    'cancelar-proposta': () => confirm('Cancelar esta proposta?') && api(`/propostas/${id}/cancelar`, { metodo: 'PUT' }),
    'confirmar-visita': () => api(`/visitas/${id}/confirmar`, { metodo: 'PUT' }),
    'recusar-visita': () => confirm('Recusar este pedido de visita?') && api(`/visitas/${id}/recusar`, { metodo: 'PUT' }),
    'cancelar-visita': () => confirm('Cancelar esta visita?') && api(`/visitas/${id}/cancelar`, { metodo: 'PUT' })
  };
  if (!chamadas[acao]) return;
  botao.disabled = true;
  try {
    const feito = await chamadas[acao]();
    if (feito) {
      avisarSolicitacao('Pronto! A outra parte foi avisada por e-mail.', 'ok');
      await carregarSolicitacoes();
      return;
    }
  } catch (erro) {
    avisarSolicitacao(erro.message);
  }
  botao.disabled = false;
});
