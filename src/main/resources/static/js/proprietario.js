const el = id => document.getElementById(id);
const idDaUrl = Number(new URLSearchParams(location.search).get('id'));

const ROTULO_STATUS_MANUTENCAO = { ABERTA: 'Aberta', EM_ANDAMENTO: 'Em andamento', CONCLUIDA: 'Concluída' };

function iniciais(nome) {
  const partes = String(nome || '?').split(' ').filter(Boolean);
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (partes[0][0] + ultima).toUpperCase();
}

function erroDaPagina(mensagem) {
  const aviso = el('erro');
  if (aviso) mostrarAviso(aviso, mensagem);
}

async function carregarBase() {
  const [imoveis, contratos] = await Promise.all([
    api('/imoveis/meus?size=200&sort=id,desc'),
    api('/contratos/meus?papel=locador&size=200&sort=id,desc')
  ]);
  return { imoveis: imoveis.content, contratos: contratos.content };
}

async function carregarPagamentos() {
  const pagina = await api('/pagamentos/recebidos?size=1000&sort=id,desc');
  return pagina.content;
}

function contratoAtivoDo(imovel, contratos) {
  return contratos.find(c => c.imovelId === imovel.id && c.status === 'ATIVO') || null;
}

function statusDoImovel(imovel, contratos) {
  if (contratoAtivoDo(imovel, contratos)) return 'Alugado';
  return imovel.disponivel ? 'Disponível' : 'Inativo';
}

function receitaPorMes(pagamentos, quantidade) {
  const fim = mesAtual();
  const meses = Array.from({ length: quantidade }, (_, i) => somarMeses(fim, i - (quantidade - 1)));
  return meses.map(mes => ({
    mes,
    rotulo: MESES_CURTOS[Number(mes.slice(5)) - 1],
    valor: pagamentos.filter(p => p.status === 'PAGO' && p.referenciaMes === mes)
      .reduce((total, p) => total + Number(p.valor), 0)
  }));
}

function recebidoNoMes(pagamentos, contratoId, mes) {
  return pagamentos.filter(p => p.contratoId === contratoId && p.status === 'PAGO' && p.referenciaMes === mes)
    .reduce((total, p) => total + Number(p.valor), 0);
}

function rotuloEixo(valor) {
  if (valor >= 1000) return `${Math.round(valor / 100) / 10}k`.replace('.0k', 'k');
  return String(Math.round(valor));
}

function desenharGrafico(svg, serie) {
  const esquerda = 60, direita = 620, topo = 10, base = 150;
  const maximo = Math.max(...serie.map(s => s.valor), 0);
  const teto = maximo === 0 ? 1000 : Math.ceil(maximo / 1000) * 1000;
  const x = i => serie.length === 1 ? (esquerda + direita) / 2 : esquerda + (i * (direita - esquerda)) / (serie.length - 1);
  const y = valor => base - (valor / teto) * (base - topo);

  const linhas = [0, 0.5, 1].map(f => {
    const yy = base - f * (base - topo);
    return `<line class="grade-h" x1="50" x2="640" y1="${yy}" y2="${yy}" /><text x="46" y="${yy + 4}" text-anchor="end">${rotuloEixo(teto * f)}</text>`;
  }).join('');
  const pontos = serie.map((s, i) => `${x(i)},${y(s.valor)}`).join(' ');
  const caminho = serie.map((s, i) => `${i ? 'L' : 'M'}${x(i)},${y(s.valor)}`).join(' ');
  const area = `${caminho} L${x(serie.length - 1)},${base} L${x(0)},${base} Z`;
  const marcas = serie.map((s, i) =>
    `<circle class="ponto" cx="${x(i)}" cy="${y(s.valor)}" r="4"><title>${s.rotulo}: ${formatarMoeda(s.valor)}</title></circle>` +
    `<text x="${x(i)}" y="172" text-anchor="middle">${s.rotulo}</text>`).join('');

  svg.innerHTML = `${linhas}<path class="area" d="${area}" /><path class="curva" d="${caminho}" />${marcas}`;
  svg.setAttribute('aria-label', `Receitas dos últimos ${serie.length} meses`);
  return pontos;
}

function linhaImovel(imovel, contratos, pagamentos) {
  const contrato = contratoAtivoDo(imovel, contratos);
  const status = statusDoImovel(imovel, contratos);
  const recebido = contrato ? recebidoNoMes(pagamentos, contrato.id, mesAtual()) : 0;
  return `<div class="tab-linha"><strong>${escaparHtml(imovel.titulo)}</strong>` +
    `<span class="destaque">${formatarMoeda(imovel.valorAluguel)}/mês</span>` +
    `<span>Inquilino: ${contrato ? escaparHtml(contrato.nomeLocatario) : 'Sem inquilino'}</span>` +
    `<span class="info">${formatarMoeda(recebido)} recebido no mês</span>` +
    `<span class="selo ${status === 'Alugado' ? '' : 'alerta'}">${status}</span></div>`;
}

function linhaInquilino(contrato, imoveis, pagamentos) {
  const situacao = calcularSituacao(contrato, pagamentos);
  const pendente = situacao.pendentes > 0;
  return `<div class="tab-linha inq"><div class="avatar">${escaparHtml(iniciais(contrato.nomeLocatario))}</div>` +
    `<div><strong>${escaparHtml(contrato.nomeLocatario)}</strong><p class="info">${escaparHtml(contrato.emailLocatario)}${contrato.telefoneLocatario ? ' · ' + escaparHtml(contrato.telefoneLocatario) : ''}</p></div>` +
    `<div><strong>${escaparHtml(contrato.tituloImovel)}</strong><p class="info">Desde ${formatarData(contrato.dataInicio)}</p></div>` +
    `<div><p class="info">Aluguel: ${formatarMoeda(contrato.valorMensal)}</p><p class="info">Vence todo dia ${contrato.diaVencimento}</p></div>` +
    `<span class="selo${pendente ? ' alerta' : ''}">${pendente ? '⚠ Pendente' : '✓ Em Dia'}</span>` +
    `<div class="acoes" style="margin:0"><a class="btn" href="inquilino.html?id=${contrato.id}">Ver</a></div></div>`;
}

function listaManutencao(itens, vazio) {
  if (!itens.length) return `<p class="info">${vazio}</p>`;
  return itens.map(m => `
    <div class="tab-linha manut">
      <div><strong>${escaparHtml(m.protocolo)} · ${escaparHtml(m.tipo)}</strong>
        <p class="info">${escaparHtml(m.tituloImovel)} · ${escaparHtml(m.nomeLocatario)} · Urgência: ${m.urgencia === 'URGENTE' ? 'Urgente' : 'Normal'}</p>
        <p class="info">${escaparHtml(m.descricao)}</p></div>
      <select data-manutencao="${m.id}" aria-label="Alterar status da solicitação ${escaparHtml(m.protocolo)}">
        ${Object.entries(ROTULO_STATUS_MANUTENCAO).map(([valor, rotulo]) =>
          `<option value="${valor}"${valor === m.status ? ' selected' : ''}>${rotulo}</option>`).join('')}
      </select>
    </div>`).join('');
}

async function carregarManutencoes(destino) {
  try {
    const pagina = await api('/manutencoes/recebidas?size=30&sort=id,desc');
    destino.innerHTML = listaManutencao(pagina.content, 'Nenhuma solicitação de manutenção.');
  } catch (e) {
    destino.innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
  }
}

const telas = {
  async 'visao-geral'() {
    const [base, pagamentos] = await Promise.all([carregarBase(), carregarPagamentos()]);
    desenharGrafico(el('grafico'), receitaPorMes(pagamentos, 6));

    el('lista-imoveis').innerHTML = base.imoveis.length
      ? base.imoveis.map(i => linhaImovel(i, base.contratos, pagamentos)).join('')
      : '<p class="info" style="padding:8px 0">Você ainda não cadastrou imóveis. <a href="novo-imovel.html" style="color:var(--ouro-texto)">Cadastrar agora</a></p>';

    const ativos = base.contratos.filter(c => c.status === 'ATIVO');
    el('lista-inquilinos').innerHTML = ativos.length
      ? ativos.map(c => linhaInquilino(c, base.imoveis, pagamentos)).join('')
      : '<p class="info" style="padding:8px 0">Nenhum inquilino ainda.</p>';

    const destino = el('lista-manutencao');
    await carregarManutencoes(destino);
    destino.addEventListener('change', async e => {
      const seletor = e.target.closest('select[data-manutencao]');
      if (!seletor) return;
      try {
        await api(`/manutencoes/${seletor.dataset.manutencao}/status`, { metodo: 'PUT', corpo: { status: seletor.value } });
        await carregarManutencoes(destino);
      } catch (erro) {
        erroDaPagina(erro.message);
      }
    });
  },

  async imoveis() {
    const base = await carregarBase();
    const params = new URLSearchParams(location.search);
    if (params.get('salvo')) mostrarAviso(el('confirmacao'), 'Imóvel salvo com sucesso!', 'ok');

    const cidades = [...new Set(base.imoveis.map(i => i.cidade))].sort();
    el('ci').innerHTML = '<option>Todas</option>' + cidades.map(c => `<option>${escaparHtml(c)}</option>`).join('');

    function montarLista() {
      const status = el('st').value;
      const cidade = el('ci').value;
      const busca = el('bu').value.toLowerCase();
      const filtrados = base.imoveis.filter(i => {
        const texto = `${i.titulo} ${i.logradouro} ${i.bairro}`.toLowerCase();
        return (status === 'Todos' || statusDoImovel(i, base.contratos) === status)
          && (cidade === 'Todas' || i.cidade === cidade) && texto.includes(busca);
      });
      el('lista').innerHTML = filtrados.length ? filtrados.map(i => {
        const st = statusDoImovel(i, base.contratos);
        const contrato = contratoAtivoDo(i, base.contratos);
        const detalhes = detalhesDoImovel(i);
        return `<article class="cartao imovel">${fotoImovel(i.id)}<div class="corpo"><div class="stat"><strong>${escaparHtml(i.titulo)}</strong>` +
          `<span class="selo${st === 'Alugado' ? '' : ' alerta'}" style="margin:0">${st}</span></div>` +
          `<p class="info">${escaparHtml(detalhes || rotuloTipo[i.tipo])}</p>` +
          `<p class="preco">${formatarMoeda(i.valorAluguel)}/mês</p>` +
          `<p class="info">${contrato ? 'Inquilino: ' + escaparHtml(contrato.nomeLocatario) : 'Sem inquilino no momento'}</p>` +
          `<div class="acoes"><button type="button" class="btn" data-ver="${i.id}">Ver</button>` +
          `<a class="btn btn-ouro" href="editar-imovel.html?id=${i.id}">Editar</a></div></div></article>`;
      }).join('') : '<p class="info">Nenhum imóvel encontrado.</p>';
    }

    montarLista();
    ['st', 'ci', 'bu'].forEach(id => el(id).addEventListener('input', montarLista));
    document.querySelector('form').addEventListener('submit', e => e.preventDefault());
    configurarModalDetalhes(base, montarLista);
  },

  async 'imovel-form'() {
    const form = document.querySelector('form');
    const editando = idDaUrl > 0;
    el('erro').hidden = true;

    if (editando) {
      document.title = 'Editar Imóvel | AlugaFácil';
      el('titulo-pagina').textContent = 'Editar Imóvel';
      try {
        const imovel = await api(`/imoveis/${idDaUrl}`);
        Object.entries(imovel).forEach(([chave, valor]) => {
          const campo = form.elements[chave];
          if (campo && valor !== null) campo.value = valor;
        });
      } catch (e) {
        el('conteudo-form').innerHTML = `<p class="aviso">${escaparHtml(e.status === 404 ? 'Imóvel não encontrado.' : e.message)}</p><a class="btn" href="meus-imoveis.html">Voltar</a>`;
        return;
      }
    }

    const LIMITE_FOTOS = 10;
    const TAMANHO_MAXIMO_FOTO = 5 * 1024 * 1024;
    const TIPOS_DE_FOTO = ['image/jpeg', 'image/png', 'image/webp'];
    let fotosSalvas = [];
    const fotosNovas = [];

    function desenharFotos() {
      const semSalvas = fotosSalvas.length === 0;
      el('fotos-grade').innerHTML =
        fotosSalvas.map(f => `<div class="foto-item"><img src="${escaparHtml(f.url)}" alt="">` +
          `${f.capa ? '<span class="selo-foto">Capa</span>' : ''}<div class="foto-acoes">` +
          `${f.capa ? '<span></span>' : `<button type="button" data-capa="${f.id}">Tornar capa</button>`}` +
          `<button type="button" class="remover" data-remover="${f.id}">Remover</button></div></div>`).join('') +
        fotosNovas.map((n, i) => `<div class="foto-item"><img src="${n.previa}" alt="">` +
          `<span class="selo-foto${semSalvas && i === 0 ? '' : ' nova'}">${semSalvas && i === 0 ? 'Capa' : 'Nova'}</span>` +
          `<div class="foto-acoes"><span></span><button type="button" class="remover" data-descartar="${i}">Remover</button></div></div>`).join('');
      el('fotos-adicionar').disabled = fotosSalvas.length + fotosNovas.length >= LIMITE_FOTOS;
    }

    async function carregarFotos() {
      fotosSalvas = editando ? await api(`/imoveis/${idDaUrl}/fotos`) : [];
      desenharFotos();
    }

    el('fotos-adicionar').addEventListener('click', () => el('fotos-arquivo').click());
    el('fotos-arquivo').addEventListener('change', e => {
      const problemas = [];
      for (const arquivo of e.target.files) {
        if (fotosSalvas.length + fotosNovas.length >= LIMITE_FOTOS) {
          problemas.push(`Cada imóvel pode ter no máximo ${LIMITE_FOTOS} fotos.`);
          break;
        }
        if (!TIPOS_DE_FOTO.includes(arquivo.type)) problemas.push(`"${arquivo.name}" não é JPG, PNG ou WebP.`);
        else if (arquivo.size > TAMANHO_MAXIMO_FOTO) problemas.push(`"${arquivo.name}" passa de 5 MB.`);
        else fotosNovas.push({ arquivo, previa: URL.createObjectURL(arquivo) });
      }
      e.target.value = '';
      mostrarAviso(el('fotos-erro'), problemas.join(' '));
      desenharFotos();
    });

    el('fotos-grade').addEventListener('click', async e => {
      const botao = e.target.closest('button');
      if (!botao) return;
      mostrarAviso(el('fotos-erro'), '');
      try {
        if (botao.dataset.descartar !== undefined) {
          URL.revokeObjectURL(fotosNovas.splice(Number(botao.dataset.descartar), 1)[0].previa);
          desenharFotos();
        } else if (botao.dataset.capa) {
          await api(`/imoveis/${idDaUrl}/fotos/${botao.dataset.capa}/capa`, { metodo: 'PUT' });
          await carregarFotos();
        } else if (botao.dataset.remover && confirm('Remover esta foto do anúncio?')) {
          await api(`/imoveis/${idDaUrl}/fotos/${botao.dataset.remover}`, { metodo: 'DELETE' });
          await carregarFotos();
        }
      } catch (erro) {
        mostrarAviso(el('fotos-erro'), erro.message);
      }
    });

    await carregarFotos();
    const erroDoEnvio = sessionStorage.getItem('erro-fotos');
    if (erroDoEnvio) {
      sessionStorage.removeItem('erro-fotos');
      mostrarAviso(el('erro'), `O imóvel foi salvo, mas as fotos não: ${erroDoEnvio} Adicione-as novamente abaixo.`);
    }

    form.addEventListener('submit', async e => {
      e.preventDefault();
      const numero = nome => (form.elements[nome].value === '' ? null : Number(form.elements[nome].value));
      const corpo = {
        titulo: form.elements.titulo.value.trim(),
        descricao: form.elements.descricao.value.trim() || null,
        tipo: form.elements.tipo.value,
        logradouro: form.elements.logradouro.value.trim(),
        numero: form.elements.numero.value.trim(),
        complemento: form.elements.complemento.value.trim() || null,
        bairro: form.elements.bairro.value.trim(),
        cidade: form.elements.cidade.value.trim(),
        uf: form.elements.uf.value.trim().toUpperCase(),
        cep: form.elements.cep.value.trim(),
        valorAluguel: numero('valorAluguel'),
        valorCondominio: numero('valorCondominio') ?? 0,
        quartos: numero('quartos'),
        banheiros: numero('banheiros'),
        vagas: numero('vagas'),
        areaM2: numero('areaM2')
      };
      const botao = form.querySelector('button[type=submit]');
      botao.disabled = true;
      const textoBotao = botao.textContent;
      try {
        const salvo = await api(editando ? `/imoveis/${idDaUrl}` : '/imoveis', { metodo: editando ? 'PUT' : 'POST', corpo });
        if (fotosNovas.length) {
          botao.textContent = 'Enviando fotos...';
          const dados = new FormData();
          fotosNovas.forEach(n => dados.append('fotos', n.arquivo));
          try {
            await api(`/imoveis/${salvo.id}/fotos`, { metodo: 'POST', corpo: dados });
          } catch (erroFotos) {
            sessionStorage.setItem('erro-fotos', erroFotos.message);
            location.href = `editar-imovel.html?id=${salvo.id}`;
            return;
          }
        }
        location.href = 'meus-imoveis.html?salvo=1';
      } catch (erro) {
        botao.textContent = textoBotao;
        mostrarAviso(el('erro'), erro.message);
        window.scrollTo({ top: 0, behavior: 'smooth' });
        botao.disabled = false;
      }
    });
  },

  async inquilinos() {
    const [base, pagamentos] = await Promise.all([carregarBase(), carregarPagamentos()]);
    const ativos = base.contratos.filter(c => c.status === 'ATIVO');
    el('lista').innerHTML = ativos.length
      ? ativos.map(c => linhaInquilino(c, base.imoveis, pagamentos)).join('')
      : '<p class="info" style="padding:8px 0">Você ainda não tem inquilinos. Quando alguém alugar um dos seus imóveis, aparecerá aqui.</p>';
  },

  async inquilino() {
    const [base, pagamentos] = await Promise.all([carregarBase(), carregarPagamentos()]);
    const contrato = base.contratos.find(c => c.id === idDaUrl);
    if (!contrato) {
      document.querySelector('main').innerHTML = '<h1 class="titulo">Registro não encontrado</h1><a class="btn" href="inquilinos.html">Voltar</a>';
      return;
    }
    const imovel = base.imoveis.find(i => i.id === contrato.imovelId);
    const situacao = calcularSituacao(contrato, pagamentos);
    const campos = {
      iniciais: iniciais(contrato.nomeLocatario),
      nome: contrato.nomeLocatario,
      email: contrato.emailLocatario,
      telefone: contrato.telefoneLocatario || 'Não informado',
      imovelNome: contrato.tituloImovel,
      imovelEndereco: imovel ? `${enderecoCompleto(imovel)} - ${imovel.bairro}, ${imovel.cidade}` : 'Não informado',
      valor: formatarMoeda(contrato.valorMensal),
      desde: formatarData(contrato.dataInicio),
      fim: formatarData(contrato.dataFim),
      vencimento: `Todo dia ${contrato.diaVencimento}`,
      statusContrato: contrato.status === 'ATIVO' ? 'Ativo' : 'Encerrado'
    };
    document.querySelectorAll('[data-campo]').forEach(e => { e.textContent = campos[e.dataset.campo] ?? ''; });
    const selo = document.querySelector('[data-selo]');
    const pendente = situacao.pendentes > 0;
    selo.textContent = contrato.status !== 'ATIVO' ? 'Encerrado' : (pendente ? '⚠ Pendente' : '✓ Em Dia');
    selo.classList.toggle('alerta', contrato.status !== 'ATIVO' || pendente);
    el('ver-imovel').href = `editar-imovel.html?id=${contrato.imovelId}`;

    const meus = pagamentos.filter(p => p.contratoId === contrato.id);
    el('historico').innerHTML = meus.length ? meus.map(p => `
      <div class="linha"><div><strong>${escaparHtml(nomeDoMes(p.referenciaMes))}</strong>
        <p class="info">Vencimento ${formatarData(p.vencimento)} · ${escaparHtml(p.metodo)}</p></div>
        <div><p class="valor">${formatarMoeda(p.valor)}</p><span class="selo ${p.status === 'PAGO' ? '' : 'alerta'}">${escaparHtml(p.status)}</span></div></div>`).join('')
      : '<p class="info">Nenhum pagamento registrado.</p>';

    const encerrar = el('encerrar');
    encerrar.hidden = contrato.status !== 'ATIVO';
    encerrar.addEventListener('click', async () => {
      if (!confirm('Encerrar este contrato? O imóvel voltará a ficar disponível.')) return;
      try {
        await api(`/contratos/${contrato.id}/encerrar`, { metodo: 'PUT' });
        location.reload();
      } catch (e) {
        erroDaPagina(e.message);
      }
    });
  },

  async financeiro() {
    const [base, pagamentos] = await Promise.all([carregarBase(), carregarPagamentos()]);
    const ativos = base.contratos.filter(c => c.status === 'ATIVO');
    const alugados = base.imoveis.filter(i => contratoAtivoDo(i, base.contratos)).length;
    const disponiveis = base.imoveis.filter(i => i.disponivel && !contratoAtivoDo(i, base.contratos)).length;
    const serie = receitaPorMes(pagamentos, 6);
    const atual = serie[serie.length - 1].valor;
    const anterior = serie[serie.length - 2].valor;
    const pendencias = ativos.reduce((total, c) => total + calcularSituacao(c, pagamentos).pendentes, 0);

    el('n-imoveis').textContent = base.imoveis.length;
    el('t-imoveis').textContent = `${alugados} alugados · ${disponiveis} disponíveis`;
    el('n-receita').textContent = formatarMoeda(atual);
    const variacao = anterior > 0 ? Math.round(((atual - anterior) / anterior) * 100) : null;
    el('t-receita').textContent = variacao === null ? 'Receita deste mês' : `${variacao >= 0 ? '+' : ''}${variacao}% vs. mês anterior`;
    el('n-inquilinos').textContent = ativos.length;
    el('t-inquilinos').textContent = ativos.length ? 'Com contrato vigente' : 'Nenhum contrato ativo';
    el('n-pendencias').textContent = pendencias;
    el('t-pendencias').textContent = pendencias ? '⚠ Há aluguel em atraso' : '✓ Tudo em dia';
    el('t-pendencias').classList.toggle('alerta', pendencias > 0);

    desenharGrafico(el('grafico'), serie);

    const recentes = pagamentos.filter(p => p.status === 'PAGO').slice(0, 10);
    el('recebimentos').innerHTML = recentes.length ? recentes.map(p => `
      <div class="linha"><div><strong>${escaparHtml(p.tituloImovel)}</strong>
        <p class="info">${escaparHtml(p.nomeLocatario)} · ${escaparHtml(nomeDoMes(p.referenciaMes))} · ${escaparHtml(p.metodo)}</p></div>
        <div><p class="valor">${formatarMoeda(p.valor)}</p><span class="selo">Pago</span></div></div>`).join('')
      : '<p class="info">Nenhum recebimento ainda.</p>';
  }
};

function configurarModalDetalhes(base, aoMudar) {
  const modal = el('modal-imovel');
  const linha = (rotulo, valor) =>
    `<div class="linha-det"><span class="info">${escaparHtml(rotulo)}</span><strong>${escaparHtml(valor)}</strong></div>`;
  let atual = null;

  function abrir(id) {
    atual = base.imoveis.find(i => i.id === id);
    if (!atual) return;
    const contrato = contratoAtivoDo(atual, base.contratos);
    const status = statusDoImovel(atual, base.contratos);

    el('det-titulo').textContent = atual.titulo;
    el('det-status').textContent = status;
    el('det-status').className = 'selo' + (status === 'Alugado' ? '' : ' alerta');
    el('det-status').style.margin = '0';
    el('det-corpo').innerHTML = [
      linha('Tipo', rotuloTipo[atual.tipo] || atual.tipo),
      linha('Endereço', `${enderecoCompleto(atual)} - ${atual.bairro}, ${atual.cidade}/${atual.uf}`),
      linha('CEP', atual.cep),
      linha('Quartos', atual.quartos ?? 'Não informado'),
      linha('Banheiros', atual.banheiros ?? 'Não informado'),
      linha('Área', atual.areaM2 != null ? `${atual.areaM2} m²` : 'Não informado'),
      linha('Vagas', atual.vagas ?? 'Não informado'),
      linha('Aluguel', formatarMoeda(atual.valorAluguel)),
      linha('Condomínio', formatarMoeda(atual.valorCondominio)),
      linha('Inquilino', contrato ? contrato.nomeLocatario : 'Sem inquilino no momento'),
      linha('Descrição', atual.descricao || 'Não informado')
    ].join('');
    el('det-editar').href = `editar-imovel.html?id=${atual.id}`;

    const alternar = el('det-alternar');
    alternar.hidden = status === 'Alugado';
    alternar.textContent = atual.disponivel ? 'Desativar anúncio' : 'Reativar anúncio';
    modal.hidden = false;
    el('det-fechar').focus();
  }

  el('lista').addEventListener('click', e => {
    const botao = e.target.closest('[data-ver]');
    if (botao) abrir(Number(botao.dataset.ver));
  });
  el('det-fechar').addEventListener('click', () => { modal.hidden = true; });
  modal.addEventListener('click', e => { if (e.target === modal) modal.hidden = true; });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && !modal.hidden) modal.hidden = true; });

  el('det-alternar').addEventListener('click', async () => {
    try {
      if (atual.disponivel) await api(`/imoveis/${atual.id}`, { metodo: 'DELETE' });
      else await api(`/imoveis/${atual.id}/reativar`, { metodo: 'PUT' });
      location.href = 'meus-imoveis.html?salvo=1';
    } catch (erro) {
      modal.hidden = true;
      erroDaPagina(erro.message);
    }
  });
}

(async function iniciar() {
  const tela = telas[document.body.dataset.pagina];
  if (!tela || !Sessao.logado()) return;
  try {
    await tela();
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    erroDaPagina(e.message);
  }
})();
