const el = id => document.getElementById(id);
const form = el('form-locador');

el('cpf').addEventListener('input', e => { e.target.value = mascararCpf(e.target.value); });
el('telefone').addEventListener('input', e => { e.target.value = mascararTelefone(e.target.value); });

function iniciais(nome) {
  const partes = String(nome || '?').split(' ').filter(Boolean);
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (partes[0][0] + ultima).toUpperCase();
}

async function carregarLista() {
  try {
    const pagina = await api('/admin/locadores?size=200&sort=id,desc');
    el('lista').innerHTML = pagina.content.length
      ? pagina.content.map(u => `
        <div class="tab-linha inq" style="grid-template-columns:40px 1.3fr 1.2fr 1fr">
          <div class="avatar">${escaparHtml(iniciais(u.nome))}</div>
          <div><strong>${escaparHtml(u.nome)}</strong><p class="info">${escaparHtml(u.email)}</p></div>
          <div><p class="info">${escaparHtml(u.telefone || 'Sem telefone')}</p></div>
          <div><p class="info">CPF ${escaparHtml(mascararCpf(u.cpf))}</p>${u.emailConfirmado === false ? '<span class="selo alerta">Aguardando confirmação</span>' : ''}</div>
        </div>`).join('')
      : '<p class="info">Nenhum proprietário cadastrado ainda.</p>';
  } catch (e) {
    if (e.status === 401) return irParaLogin('expirou');
    el('lista').innerHTML = `<p class="info">${escaparHtml(e.message)}</p>`;
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
    await carregarLista();
  } catch (erro) {
    mostrarAviso(el('erro'), erro.message);
  }
  botao.disabled = false;
});

if (Sessao.logado()) carregarLista();
