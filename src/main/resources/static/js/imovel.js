const conteudo = document.getElementById('conteudo');
const id = new URLSearchParams(location.search).get('id');
const POR_PAGINA = 5;

let urlsDasFotos = [];
let indiceFoto = 0;
let notaEscolhida = null;
let minhaAvaliacao = null;
let paginaAvaliacoes = 0;

const el = nome => document.getElementById(nome);

function mostrarErro(mensagem) {
  conteudo.innerHTML = `
    <p class="aviso">${escaparHtml(mensagem)}</p>
    <a class="btn" href="imoveis.html">Voltar para os imóveis</a>`;
}

function htmlGaleria(imovel) {
  const varias = urlsDasFotos.length > 1;
  return `
    <div class="galeria">
      <div class="galeria-principal">
        <img id="galeria-img" src="${escaparHtml(urlsDasFotos[0])}" alt="Foto 1 de ${escaparHtml(imovel.titulo)}"
          onerror="this.onerror=null;this.src='${fotoFicticia(imovel.id)}'">
        ${varias ? `<button type="button" class="galeria-seta anterior" aria-label="Foto anterior">‹</button>
          <button type="button" class="galeria-seta proxima" aria-label="Próxima foto">›</button>
          <span class="galeria-contador" id="galeria-contador"></span>` : ''}
      </div>
      ${varias ? `<div class="galeria-mini">${urlsDasFotos.map((url, i) =>
        `<button type="button" data-foto="${i}" aria-label="Ver foto ${i + 1}"><img src="${escaparHtml(url)}" alt="" loading="lazy"></button>`).join('')}</div>` : ''}
    </div>`;
}

function irParaFoto(indice) {
  indiceFoto = (indice + urlsDasFotos.length) % urlsDasFotos.length;
  const imagem = el('galeria-img');
  imagem.src = urlsDasFotos[indiceFoto];
  imagem.alt = `Foto ${indiceFoto + 1} do imóvel`;
  const contador = el('galeria-contador');
  if (contador) contador.textContent = `${indiceFoto + 1} / ${urlsDasFotos.length}`;
  document.querySelectorAll('.galeria-mini button').forEach(botao => {
    const ativa = Number(botao.dataset.foto) === indiceFoto;
    botao.classList.toggle('ativa', ativa);
    if (ativa) botao.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  });
}

function iniciarGaleria() {
  if (urlsDasFotos.length < 2) return;
  irParaFoto(0);
  document.querySelector('.galeria').addEventListener('click', e => {
    if (e.target.closest('.anterior')) return irParaFoto(indiceFoto - 1);
    if (e.target.closest('.proxima')) return irParaFoto(indiceFoto + 1);
    const miniatura = e.target.closest('[data-foto]');
    if (miniatura) irParaFoto(Number(miniatura.dataset.foto));
  });
  document.addEventListener('keydown', e => {
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) return;
    if (e.key === 'ArrowLeft') irParaFoto(indiceFoto - 1);
    if (e.key === 'ArrowRight') irParaFoto(indiceFoto + 1);
  });
}

function renderizarImovel(imovel, perfil) {
  const podeAlugar = perfil === 'LOCATARIO' && imovel.disponivel;
  const detalhes = detalhesDoImovel(imovel);
  const vagas = imovel.vagas != null ? ` · ${imovel.vagas} ${imovel.vagas === 1 ? 'vaga' : 'vagas'}` : '';

  let acao;
  if (!imovel.disponivel) acao = '<p class="info">Este imóvel não está mais disponível para locação.</p>';
  else if (podeAlugar) acao = `<a class="btn btn-ouro" href="pagamentos.html?imovel=${imovel.id}">Alugar este imóvel</a>`;
  else acao = '<p class="info">Somente contas de locatário podem alugar imóveis.</p>';

  conteudo.innerHTML = `
    <section class="imovel-detalhe">
      ${htmlGaleria(imovel)}
      <div>
        <p class="rotulo">${escaparHtml(rotuloTipo[imovel.tipo] || imovel.tipo)}</p>
        <h1 class="titulo">${escaparHtml(imovel.titulo)}</h1>
        <p class="info">${escaparHtml(imovel.bairro)}, ${escaparHtml(imovel.cidade)}/${escaparHtml(imovel.uf)} · CEP ${escaparHtml(imovel.cep)}</p>
        ${resumoDeAvaliacao(imovel)}
        <p style="margin:0 0 6px"><a href="#avaliacoes" style="color:var(--ouro-texto);font-weight:600;font-size:.85rem">${perfil === 'LOCATARIO' ? 'Avaliar este imóvel ↓' : 'Ver avaliações ↓'}</a></p>
        ${detalhes || vagas ? `<p class="info">${escaparHtml(detalhes + vagas)}</p>` : ''}
        <p class="numero" style="margin-top:14px">${formatarMoeda(imovel.valorAluguel)}
          <span class="info" style="font-size:.9rem;font-weight:400">/mês</span></p>
        <p class="info">Condomínio: ${formatarMoeda(imovel.valorCondominio)}</p>
        <div class="caixa">
          <p class="rotulo">Endereço</p>
          <p>${escaparHtml(enderecoCompleto(imovel))}</p>
          <p class="rotulo" style="margin-top:10px">Anunciado por</p>
          <p>${escaparHtml(imovel.nomeLocador)}</p>
        </div>
        ${imovel.comodidades && imovel.comodidades.length ? `<p class="rotulo" style="margin-top:18px">O que este lugar oferece</p>
          <div class="comodidades-lista">${htmlComodidades(imovel.comodidades)}</div>` : ''}
        ${imovel.descricao ? `<p class="descricao">${escaparHtml(imovel.descricao)}</p>` : ''}
        <div class="acoes" style="margin-top:20px">${acao}</div>
      </div>
    </section>
    <section class="cartao" id="localizacao">
      <h2 class="secao" style="margin-top:0">Localização</h2>
      <div id="mapa-imovel" class="mapa-imovel"></div>
      <p class="info" id="mapa-nota" style="margin-top:8px"></p>
    </section>
    <section class="cartao avaliacoes" id="avaliacoes">
      <h2 class="secao" style="margin-top:0">Avaliações</h2>
      <div id="av-resumo"></div>
      <div id="av-form"></div>
      <div id="av-lista"></div>
      <div class="acoes" style="justify-content:center"><button type="button" class="btn" id="av-mais" hidden>Ver mais avaliações</button></div>
    </section>`;
  iniciarMapaDoImovel(imovel);
}

// Sem posicao (cidade fora da lista) ou sem a biblioteca do mapa, a secao some em vez de mostrar um quadro vazio.
function iniciarMapaDoImovel(imovel) {
  const secao = document.getElementById('localizacao');
  const posicao = posicaoDoImovel(imovel);
  if (!posicao || !mapaDisponivel()) {
    secao.hidden = true;
    return;
  }
  const mapaDoImovel = criarMapa(document.getElementById('mapa-imovel'), [posicao.lat, posicao.lng], posicao.aproximada ? 13 : 16,
    { rolarParaAmpliar: false });
  L.marker([posicao.lat, posicao.lng], { icon: iconePreco(imovel, true) }).addTo(mapaDoImovel);
  document.getElementById('mapa-nota').textContent = posicao.aproximada
    ? 'Posição aproximada: ainda não localizamos o endereço exato no mapa.'
    : `${enderecoCompleto(imovel)} - ${imovel.bairro}, ${imovel.cidade}/${imovel.uf}`;
}

function htmlResumo(resumo) {
  if (!resumo.total) return '<p class="info">Este imóvel ainda não recebeu avaliações.</p>';
  const maior = Math.max(...resumo.distribuicao, 1);
  const barras = [5, 4, 3, 2, 1, 0].map(nota =>
    `<div><span>${nota}★</span><div class="barra"><span style="width:${(resumo.distribuicao[nota] / maior) * 100}%"></span></div><span>${resumo.distribuicao[nota]}</span></div>`).join('');
  return `<div class="avaliacoes-resumo">
    <div class="avaliacoes-nota">
      <span class="numero-grande">${formatarNota(resumo.media)}</span>
      ${estrelas(resumo.media, 'grande')}
      <p class="info">${resumo.total} ${resumo.total === 1 ? 'avaliação' : 'avaliações'}</p>
    </div>
    <div class="distribuicao">${barras}</div>
  </div>`;
}

function htmlFormulario() {
  const editando = minhaAvaliacao !== null;
  return `<form id="av-formulario" class="caixa" novalidate>
    <p class="rotulo">${editando ? 'Sua avaliação' : 'Avalie este imóvel'}</p>
    <div class="seletor-estrelas" id="av-estrelas" role="group" aria-label="Nota de 0 a 5 estrelas">
      <button type="button" class="zero" data-nota="0" aria-label="Nota 0">0</button>
      ${[1, 2, 3, 4, 5].map(n => `<button type="button" data-nota="${n}" aria-label="${n} ${n === 1 ? 'estrela' : 'estrelas'}">★</button>`).join('')}
      <span class="rotulo-nota" id="av-rotulo"></span>
    </div>
    <label for="av-comentario">Comentário sobre o imóvel e a região <span class="info">(opcional)</span></label>
    <textarea id="av-comentario" rows="3" maxlength="1000" placeholder="Conte como é morar aqui: o imóvel, a vizinhança, o proprietário..."></textarea>
    <p class="info" id="av-contador" style="text-align:right">0/1000</p>
    <p class="aviso" id="av-aviso" hidden></p>
    <div class="acoes" style="align-items:center">
      <button class="btn btn-ouro" type="submit">${editando ? 'Atualizar avaliação' : 'Enviar avaliação'}</button>
      ${editando ? '<button type="button" class="link-perigo" id="av-excluir">Excluir minha avaliação</button>' : ''}
    </div>
  </form>`;
}

function escolherNota(nota) {
  notaEscolhida = nota;
  document.querySelectorAll('#av-estrelas button').forEach(botao => {
    const valor = Number(botao.dataset.nota);
    if (valor === 0) botao.classList.toggle('ativa', nota === 0);
    else botao.classList.toggle('cheia', nota !== null && valor <= nota);
  });
  el('av-rotulo').textContent = nota === null ? '' : `${nota} · ${ROTULO_NOTA[nota]}`;
}

function iniciarFormulario() {
  escolherNota(minhaAvaliacao ? minhaAvaliacao.nota : null);
  const comentario = el('av-comentario');
  comentario.value = minhaAvaliacao && minhaAvaliacao.comentario ? minhaAvaliacao.comentario : '';
  const contar = () => { el('av-contador').textContent = `${comentario.value.length}/1000`; };
  contar();
  comentario.addEventListener('input', contar);
  el('av-estrelas').addEventListener('click', e => {
    const botao = e.target.closest('[data-nota]');
    if (botao) escolherNota(Number(botao.dataset.nota));
  });

  el('av-formulario').addEventListener('submit', async e => {
    e.preventDefault();
    if (notaEscolhida === null) {
      mostrarAviso(el('av-aviso'), 'Escolha uma nota de 0 a 5 estrelas.');
      return;
    }
    const botao = e.submitter || el('av-formulario').querySelector('button[type=submit]');
    botao.disabled = true;
    try {
      await api(`/imoveis/${id}/avaliacoes/minha`, {
        metodo: 'PUT',
        corpo: { nota: notaEscolhida, comentario: comentario.value.trim() || null }
      });
      await carregarAvaliacoes();
      mostrarAviso(el('av-aviso'), 'Avaliação salva. Obrigado!', 'ok');
    } catch (erro) {
      mostrarAviso(el('av-aviso'), erro.message);
      botao.disabled = false;
    }
  });

  const excluir = el('av-excluir');
  if (excluir) {
    excluir.addEventListener('click', async () => {
      if (!confirm('Excluir a sua avaliação deste imóvel?')) return;
      try {
        await api(`/imoveis/${id}/avaliacoes/minha`, { metodo: 'DELETE' });
        await carregarAvaliacoes();
      } catch (erro) {
        mostrarAviso(el('av-aviso'), erro.message);
      }
    });
  }
}

function htmlAvaliacao(avaliacao, ehAdmin) {
  const editada = new Date(avaliacao.atualizadoEm) - new Date(avaliacao.criadoEm) > 1000;
  const minha = minhaAvaliacao && minhaAvaliacao.id === avaliacao.id;
  return `<article class="avaliacao-item">
    <div class="avatar">${escaparHtml(avaliacao.nomeUsuario.charAt(0).toUpperCase())}</div>
    <div>
      <div class="cabeca-avaliacao">
        <strong>${escaparHtml(avaliacao.nomeUsuario)}${minha ? ' (você)' : ''}</strong>
        ${estrelas(avaliacao.nota)}
        <span class="info">${formatarData(avaliacao.atualizadoEm)}${editada ? ' · editada' : ''}</span>
        ${ehAdmin ? `<button type="button" class="link-perigo" data-excluir="${avaliacao.id}">Excluir</button>` : ''}
      </div>
      ${avaliacao.comentario ? `<p>${escaparHtml(avaliacao.comentario)}</p>` : ''}
    </div>
  </article>`;
}

async function carregarPaginaDeAvaliacoes(zerar) {
  if (zerar) {
    paginaAvaliacoes = 0;
    el('av-lista').innerHTML = '';
  }
  const pagina = await api(`/imoveis/${id}/avaliacoes?size=${POR_PAGINA}&page=${paginaAvaliacoes}`);
  const ehAdmin = Sessao.dados().perfil === 'ADMIN';
  el('av-lista').insertAdjacentHTML('beforeend', pagina.content.map(a => htmlAvaliacao(a, ehAdmin)).join(''));
  el('av-mais').hidden = pagina.last;
}

async function carregarAvaliacoes() {
  const perfil = Sessao.dados().perfil;
  const [resumo, minha] = await Promise.all([
    api(`/imoveis/${id}/avaliacoes/resumo`),
    perfil === 'LOCATARIO'
      ? api(`/imoveis/${id}/avaliacoes/minha`).catch(erro => { if (erro.status === 404) return null; throw erro; })
      : Promise.resolve(null)
  ]);
  minhaAvaliacao = minha;
  el('av-resumo').innerHTML = htmlResumo(resumo);

  if (perfil === 'LOCATARIO') {
    el('av-form').innerHTML = htmlFormulario();
    iniciarFormulario();
  } else {
    el('av-form').innerHTML = '<p class="info" style="margin-top:12px">Somente os clientes (locatários) avaliam os imóveis.' +
      (perfil === 'ADMIN' ? ' Como administrador, você só pode excluir avaliações indevidas.' : '') + '</p>';
  }
  await carregarPaginaDeAvaliacoes(true);
}

function iniciarAvaliacoes() {
  el('av-mais').addEventListener('click', async () => {
    paginaAvaliacoes++;
    try {
      await carregarPaginaDeAvaliacoes(false);
    } catch (erro) {
      paginaAvaliacoes--;
      alert(erro.message);
    }
  });
  el('av-lista').addEventListener('click', async e => {
    const botao = e.target.closest('[data-excluir]');
    if (!botao || !confirm('Excluir esta avaliação? Isso não pode ser desfeito.')) return;
    try {
      await api(`/avaliacoes/${botao.dataset.excluir}`, { metodo: 'DELETE' });
      await carregarAvaliacoes();
    } catch (erro) {
      alert(erro.message);
    }
  });
  return carregarAvaliacoes();
}

async function iniciar() {
  if (!/^\d+$/.test(id || '')) return mostrarErro('Imóvel não informado ou inválido.');
  if (!Sessao.logado()) return irParaLogin();

  try {
    const [imovel, fotos] = await Promise.all([api(`/imoveis/${id}`), api(`/imoveis/${id}/fotos`)]);
    urlsDasFotos = fotos.length ? fotos.map(f => f.url) : [fotoFicticia(imovel.id)];
    renderizarImovel(imovel, Sessao.dados().perfil);
    iniciarGaleria();
    await iniciarAvaliacoes();
    if (location.hash === '#avaliacoes') el('avaliacoes').scrollIntoView();
  } catch (erro) {
    if (erro.status === 401) return irParaLogin('expirou');
    mostrarErro(erro.status === 404 ? 'Imóvel não encontrado.' : erro.message);
  }
}

iniciar();
