// Página de detalhe de um imóvel. Só abre com sessão ativa.
const conteudo = document.getElementById('conteudo');
const id = new URLSearchParams(location.search).get('id');
const destinoAtual = `imovel.html?id=${id}`;

function irParaLogin() {
  location.href = `login.html?redirect=${encodeURIComponent(destinoAtual)}`;
}

function mostrarErro(mensagem) {
  conteudo.innerHTML = `
    <p class="aviso">${escaparHtml(mensagem)}</p>
    <a class="btn" href="index.html">Voltar para os imóveis</a>`;
}

function renderizarImovel(imovel, perfil) {
  const endereco = `${imovel.logradouro}, ${imovel.numero}${imovel.complemento ? ' - ' + imovel.complemento : ''}`;
  const podeAlugar = perfil !== 'LOCADOR';

  conteudo.innerHTML = `
    <section class="imovel-detalhe">
      ${fotoImovel(imovel.id, 'foto-grande')}
      <div>
        <p class="rotulo">${escaparHtml(rotuloTipo[imovel.tipo] || imovel.tipo)}</p>
        <h1 class="titulo">${escaparHtml(imovel.titulo)}</h1>
        <p class="info">${escaparHtml(imovel.bairro)}, ${escaparHtml(imovel.cidade)}/${escaparHtml(imovel.uf)} · CEP ${escaparHtml(imovel.cep)}</p>
        <p class="numero" style="margin-top:14px">${formatarMoeda(imovel.valorAluguel)}
          <span class="info" style="font-size:.9rem;font-weight:400">/mês</span></p>
        <p class="info">Condomínio: ${formatarMoeda(imovel.valorCondominio)}</p>
        <div class="caixa">
          <p class="rotulo">Endereço</p>
          <p>${escaparHtml(endereco)}</p>
          <p class="rotulo" style="margin-top:10px">Anunciado por</p>
          <p>${escaparHtml(imovel.nomeLocador)}</p>
        </div>
        ${imovel.descricao ? `<p class="descricao">${escaparHtml(imovel.descricao)}</p>` : ''}
        <div class="acoes" style="margin-top:20px">
          ${podeAlugar
            ? `<a class="btn btn-ouro" href="pagamentos.html?imovel=${imovel.id}">Alugar este imóvel</a>`
            : '<p class="info">Você está logado como proprietário.</p>'}
        </div>
      </div>
    </section>`;
}

async function iniciar() {
  atualizarBotaoSessao(document.getElementById('btn-sessao'));

  if (!/^\d+$/.test(id || '')) return mostrarErro('Imóvel não informado ou inválido.');
  if (!Sessao.logado()) return irParaLogin();

  try {
    const imovel = buscarImovel(id);
    renderizarImovel(imovel, Sessao.dados().perfil);
  } catch (erro) {
    if (erro.status === 401) {
      Sessao.limpar();
      return irParaLogin();
    }
    mostrarErro(erro.message);
  }
}

iniciar();