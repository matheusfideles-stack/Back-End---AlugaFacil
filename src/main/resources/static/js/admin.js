const el = id => document.getElementById(id);
const TAMANHO = 15;
const ABAS = ['proprietarios', 'usuarios', 'imoveis', 'avaliacoes', 'contratos', 'pagamentos'];
const ROTULO_PERFIL = { LOCATARIO: 'Locatário', LOCADOR: 'Proprietário', ADMIN: 'Administrador' };
const ROTULO_STATUS_CONTRATO = { ATIVO: 'Ativo', ENCERRADO: 'Encerrado' };
const ROTULO_STATUS_PAGAMENTO = { PAGO: 'Pago', PENDENTE: 'Aguardando', PROCESSANDO: 'Processando', RECUSADO: 'Recusado', ESTORNADO: 'Estornado' };
const ROTULO_METODO = { PIX: 'Pix', BOLETO: 'Boleto', CARTAO_CREDITO: 'Crédito', CARTAO_DEBITO: 'Débito' };

const form = el('form-locador');
const paginas = {};
const perfilFiltrado = { usuarios: '' };

el('cpf').addEventListener('input', e => { e.target.value = mascararCpf(e.target.value); });
el('telefone').addEventListener('input', e => { e.target.value = mascararTelefone(e.target.value); });

function iniciais(nome) {
  const partes = String(nome || '?').split(' ').filter(Boolean);
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (partes[0][0] + ultima).toUpperCase();
}

function mostrarAvisoAdmin(mensagem, tipo = 'ok') {
  const aviso = el('aviso-admin');
  mostrarAviso(aviso, mensagem, tipo);
}

// ---------- Proprietarios: cadastro e lista (aba original) ----------

async function carregarProprietarios() {
  try {
    const pagina = await api('/admin/locadores?size=200&sort=id,desc');
    el('lista-proprietarios').innerHTML = pagina.content.length
      ? pagina.content.map(u => `
        <div class="linha-admin proprietarios">
          <div class="avatar">${escaparHtml(iniciais(u.nome))}</div>
          <div><strong>${escaparHtml(u.nome)}</strong><p class="info">${escaparHtml(u.email)}</p></div>
          <div><p class="info">${escaparHtml(u.telefone || 'Sem telefone')}</p></div>
          <div><p class="info">CPF ${escaparHtml(mascararCpf(u.cpf))}</p>${u.emailConfirmado === false ? '<span class="selo alerta">Aguardando confirmação</span>' : ''}</div>
        </div>`).join('')
      : '<p class="info">Nenhum proprietário cadastrado ainda.</p>';
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    el('lista-proprietarios').innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
  }
}

form.addEventListener('submit', async e => {
  e.preventDefault();
  mostrarAviso(el('erro'), '');
  mostrarAviso(el('confirmacao'), '');
  el('codigo-teste').hidden = true;

  const campos = ['nome', 'email', 'cpf', 'senha'].map(el);
  const invalido = campos.find(c => !c.checkValidity());
  if (invalido) {
    const nomeCampo = document.querySelector(`label[for="${invalido.id}"]`).textContent.trim();
    mostrarAviso(el('erro'), invalido.id === 'senha' && invalido.value
      ? 'A senha precisa ter pelo menos 8 caracteres.'
      : `Preencha o campo "${nomeCampo}" corretamente.`);
    invalido.focus();
    return;
  }

  const botao = form.querySelector('button[type=submit]');
  botao.disabled = true;
  try {
    const criado = await api('/admin/locadores', {
      metodo: 'POST',
      corpo: {
        nome: el('nome').value.trim(),
        email: el('email').value.trim(),
        cpf: el('cpf').value,
        telefone: el('telefone').value,
        senha: el('senha').value
      }
    });
    mostrarAviso(el('confirmacao'), `Proprietário ${criado.nome} cadastrado. Enviamos um código de verificação para ${criado.email}; ele só consegue entrar depois de confirmar.`, 'ok');
    el('codigo-teste').hidden = !criado.codigoDeTeste;
    if (criado.codigoDeTeste) el('codigo-teste-valor').textContent = criado.codigoDeTeste;
    form.reset();
    await carregarProprietarios();
  } catch (erro) {
    mostrarAviso(el('erro'), erro.message);
  }
  botao.disabled = false;
});

// ---------- Abas de consulta ----------

const LINHAS = {
  usuarios: {
    caminho: '/admin/usuarios',
    vazio: 'Nenhum usuário encontrado.',
    linha: u => {
      const desativado = u.ativo === false;
      const acao = desativado
        ? `<button class="btn" data-acao="ativar-usuario" data-id="${u.id}" data-ativo="true">Ativar</button>`
        : `<button class="btn" data-acao="ativar-usuario" data-id="${u.id}" data-ativo="false" data-nome="${escaparHtml(u.nome)}">Desativar</button>`;
      return `
        <div class="linha-admin usuarios">
          <div class="avatar">${escaparHtml(iniciais(u.nome))}</div>
          <div><strong>${escaparHtml(u.nome)}</strong><p class="info">${escaparHtml(u.email)}</p></div>
          <div><p class="info">${escaparHtml(ROTULO_PERFIL[u.perfil] || u.perfil)}</p></div>
          <div>${desativado ? '<span class="selo erro">Desativado</span>' : '<span class="selo">Ativo</span>'}${u.emailConfirmado === false ? '<p class="info">E-mail não confirmado</p>' : ''}</div>
          <div>${acao}</div>
        </div>`;
    }
  },
  imoveis: {
    caminho: '/admin/imoveis',
    vazio: 'Nenhum imóvel cadastrado.',
    linha: i => {
      const disponivel = i.disponivel !== false;
      const acao = disponivel
        ? `<button class="btn" data-acao="disponivel-imovel" data-id="${i.id}" data-disponivel="false" data-titulo="${escaparHtml(i.titulo)}">Desativar</button>`
        : `<button class="btn" data-acao="disponivel-imovel" data-id="${i.id}" data-disponivel="true">Reativar</button>`;
      return `
        <div class="linha-admin imoveis">
          <div><strong>${escaparHtml(i.titulo)}</strong><p class="info">${escaparHtml(rotuloTipo[i.tipo] || i.tipo)} · ${escaparHtml(i.cidade)}/${escaparHtml(i.uf)}</p></div>
          <div><p class="info">${formatarMoeda(i.valorAluguel)}/mês</p></div>
          <div>${disponivel ? '<span class="selo">Disponível</span>' : '<span class="selo erro">Fora da busca</span>'}</div>
          <div>${acao}</div>
        </div>`;
    }
  },
  avaliacoes: {
    caminho: '/admin/avaliacoes',
    vazio: 'Nenhuma avaliação ainda.',
    linha: a => `
      <div class="linha-admin avaliacoes">
        <div><strong>${escaparHtml(a.nomeAutor)}</strong><p class="info">${escaparHtml(a.emailAutor)}</p></div>
        <div><p class="info">${escaparHtml(a.tituloImovel)}</p><p class="info">Nota ${escaparHtml(a.nota)} · ${formatarData(a.criadoEm)}</p></div>
        <div><p class="info">${escaparHtml(a.comentario || 'Sem comentário')}</p></div>
        <div><button class="btn" data-acao="excluir-avaliacao" data-id="${a.id}">Excluir</button></div>
      </div>`
  },
  contratos: {
    caminho: '/admin/contratos',
    vazio: 'Nenhum contrato ainda.',
    linha: c => `
      <div class="linha-admin contratos">
        <div><strong>${escaparHtml(c.tituloImovel)}</strong><p class="info">${formatarData(c.dataInicio)} a ${formatarData(c.dataFim)}</p></div>
        <div><p class="info">${escaparHtml(c.nomeLocatario)}</p><p class="info">${escaparHtml(c.emailLocatario)}</p></div>
        <div><p class="info">${formatarMoeda(c.valorMensal)}/mês · vence dia ${escaparHtml(c.diaVencimento)}</p></div>
        <div><span class="selo ${c.status === 'ATIVO' ? '' : 'alerta'}">${escaparHtml(ROTULO_STATUS_CONTRATO[c.status] || c.status)}</span></div>
      </div>`
  },
  pagamentos: {
    caminho: '/admin/pagamentos',
    vazio: 'Nenhum pagamento ainda.',
    linha: p => `
      <div class="linha-admin pagamentos">
        <div><strong>${escaparHtml(p.tituloImovel)}</strong><p class="info">${escaparHtml(p.nomeLocatario)} · ${escaparHtml(p.referenciaMes)}</p></div>
        <div><p class="info">${formatarMoeda(p.valor)} · ${escaparHtml(ROTULO_METODO[p.metodo] || p.metodo)}</p></div>
        <div><p class="info">${p.pagoEm ? 'Pago em ' + formatarData(p.pagoEm) : 'Vence em ' + formatarData(p.vencimento)}</p></div>
        <div><span class="selo ${p.status === 'PAGO' ? '' : (p.status === 'RECUSADO' ? 'erro' : 'alerta')}">${escaparHtml(ROTULO_STATUS_PAGAMENTO[p.status] || p.status)}</span>${p.status === 'PENDENTE' ? `<button class="btn" data-acao="lembrete-pagamento" data-id="${p.id}" data-nome="${escaparHtml(p.nomeLocatario)}">Enviar lembrete</button>` : ''}</div>
      </div>`
  }
};

function paginacao(pagina, nome) {
  if (pagina.totalPages <= 1) return '';
  return `
    <button class="btn" data-acao="pagina" data-aba="${nome}" data-pagina="${pagina.number - 1}" ${pagina.number === 0 ? 'disabled' : ''}>Anterior</button>
    <span class="info">Página ${pagina.number + 1} de ${pagina.totalPages}</span>
    <button class="btn" data-acao="pagina" data-aba="${nome}" data-pagina="${pagina.number + 1}" ${pagina.number + 1 >= pagina.totalPages ? 'disabled' : ''}>Próxima</button>`;
}

async function carregarAba(nome) {
  if (nome === 'proprietarios') return carregarProprietarios();
  const config = LINHAS[nome];
  const parametros = new URLSearchParams({ page: paginas[nome] || 0, size: TAMANHO, sort: 'id,desc' });
  if (nome === 'usuarios' && perfilFiltrado.usuarios) parametros.set('perfil', perfilFiltrado.usuarios);
  const lista = el('lista-' + nome);
  try {
    const pagina = await api(`${config.caminho}?${parametros}`);
    lista.innerHTML = pagina.content.length
      ? pagina.content.map(config.linha).join('')
      : `<p class="info">${config.vazio}</p>`;
    el('pag-' + nome).innerHTML = paginacao(pagina, nome);
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    lista.innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
  }
}

function abaDoHash() {
  const nome = location.hash.slice(1);
  return ABAS.includes(nome) ? nome : 'proprietarios';
}

function mostrarAba(nome, recarregar = true) {
  const escolhida = ABAS.includes(nome) ? nome : 'proprietarios';
  ABAS.forEach(a => {
    el('sec-' + a).hidden = a !== escolhida;
  });
  document.querySelectorAll('#menu-principal a[data-aba]').forEach(link => {
    link.classList.toggle('ativo', link.dataset.aba === escolhida);
  });
  mostrarAviso(el('erro-admin'), '');
  if (recarregar) carregarAba(escolhida);
}

// As abas ficam no menu do topo e cada uma tem um endereco (#usuarios, #imoveis...): a pagina so reage a isso.
window.addEventListener('hashchange', () => {
  paginas[abaDoHash()] = 0;
  mostrarAba(abaDoHash());
});

el('filtro-perfil').addEventListener('change', e => {
  perfilFiltrado.usuarios = e.target.value;
  paginas.usuarios = 0;
  carregarAba('usuarios');
});

// Acoes de ativar, desativar e excluir passam por esta unica escuta.
document.querySelector('main').addEventListener('click', async e => {
  const botao = e.target.closest('[data-acao]');
  if (!botao || botao.disabled) return;
  const acao = botao.dataset.acao;

  if (acao === 'pagina') {
    paginas[botao.dataset.aba] = Number(botao.dataset.pagina);
    return carregarAba(botao.dataset.aba);
  }

  try {
    if (acao === 'lembrete-pagamento') {
      if (!confirm(`Enviar lembrete para ${botao.dataset.nome}? O e-mail pede que ele pague em até 5 dias.`)) return;
      await api(`/admin/pagamentos/${botao.dataset.id}/lembrete`, { metodo: 'POST' });
      return mostrarAvisoAdmin(`Lembrete enviado para ${botao.dataset.nome}.`);
    }
    if (acao === 'ativar-usuario') {
      const ativar = botao.dataset.ativo === 'true';
      if (!ativar && !confirm(`Desativar a conta de ${botao.dataset.nome}? Ela não consegue mais entrar até ser reativada.`)) return;
      await api(`/admin/usuarios/${botao.dataset.id}/ativo`, { metodo: 'PUT', corpo: { ativo: ativar } });
      mostrarAvisoAdmin(ativar ? 'Conta reativada.' : 'Conta desativada.');
      return carregarAba('usuarios');
    }
    if (acao === 'disponivel-imovel') {
      const disponivel = botao.dataset.disponivel === 'true';
      if (!disponivel && !confirm(`Tirar "${botao.dataset.titulo}" da busca? O proprietário continua vendo o imóvel no painel dele.`)) return;
      await api(`/admin/imoveis/${botao.dataset.id}/disponivel`, { metodo: 'PUT', corpo: { ativo: disponivel } });
      mostrarAvisoAdmin(disponivel ? 'Imóvel voltou para a busca.' : 'Imóvel saiu da busca.');
      return carregarAba('imoveis');
    }
    if (acao === 'excluir-avaliacao') {
      if (!confirm('Excluir esta avaliação? Essa ação não pode ser desfeita.')) return;
      await api(`/avaliacoes/${botao.dataset.id}`, { metodo: 'DELETE' });
      mostrarAvisoAdmin('Avaliação excluída.');
      return carregarAba('avaliacoes');
    }
  } catch (erro) {
    if (erro.status === 401) return irParaLogin('expirou');
    mostrarAviso(el('erro-admin'), erro.message);
  }
});

if (Sessao.logado()) mostrarAba(abaDoHash());
