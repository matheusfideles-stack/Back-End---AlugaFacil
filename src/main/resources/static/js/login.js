const formEntrar = document.getElementById('form-entrar');
const formCadastro = document.getElementById('form-cadastro');
const aviso = document.getElementById('aviso');
const params = new URLSearchParams(location.search);
const destinoSolicitado = destinoSeguro(params.get('redirect'));

function avisar(mensagem, tipo = 'erro') {
  mostrarAviso(aviso, mensagem, tipo);
}

function mostrarAba(nome) {
  const cadastro = nome === 'cadastro';
  formEntrar.hidden = cadastro;
  formCadastro.hidden = !cadastro;
  document.querySelectorAll('.abas a[data-aba]').forEach(a => a.classList.toggle('ativo', a.dataset.aba === nome));
  document.getElementById('titulo-lado').textContent = cadastro ? 'Crie sua conta!' : 'Bem-vindo de volta!';
  document.getElementById('confirmacao-box').hidden = true;
  document.getElementById('sub-lado').textContent = cadastro
    ? 'Cadastre-se para ver os imóveis disponíveis e alugar com praticidade.'
    : 'Acesse sua conta para continuar gerenciando seus imóveis.';
  avisar('');
}

document.addEventListener('click', e => {
  const link = e.target.closest('[data-aba], [data-trocar-aba]');
  if (!link) return;
  e.preventDefault();
  mostrarAba(link.dataset.aba || link.dataset.trocarAba);
});

function validarFormulario(form) {
  const campoInvalido = [...form.querySelectorAll('input:not([type=radio]):not([type=checkbox])')]
    .find(campo => !campo.checkValidity());
  if (!campoInvalido) return true;
  const nomeCampo = document.querySelector(`label[for="${campoInvalido.id}"]`).textContent.replace('(opcional)', '').trim();
  if (campoInvalido.type === 'password' && campoInvalido.value && campoInvalido.value.length < 8) {
    avisar('A senha precisa ter pelo menos 8 caracteres.');
  } else if (campoInvalido.type === 'email' && campoInvalido.value) {
    avisar('Digite um email válido, por exemplo: nome@email.com.');
  } else {
    avisar(`Preencha o campo "${nomeCampo}".`);
  }
  campoInvalido.focus();
  return false;
}

document.getElementById('cpf').addEventListener('input', e => { e.target.value = mascararCpf(e.target.value); });
document.getElementById('telefone').addEventListener('input', e => { e.target.value = mascararTelefone(e.target.value); });

function ocupado(form, ocupadoAgora, textoOcupado) {
  const botao = form.querySelector('button[type=submit]');
  if (ocupadoAgora) {
    botao.dataset.texto = botao.textContent;
    botao.textContent = textoOcupado;
  } else if (botao.dataset.texto) {
    botao.textContent = botao.dataset.texto;
  }
  botao.disabled = ocupadoAgora;
}

let emailPendente = '';

function mostrarConfirmacao(email, texto, codigoDeTeste) {
  avisar('');
  emailPendente = email;
  document.getElementById('confirmacao-texto').textContent = texto;
  const teste = document.getElementById('confirmacao-teste');
  teste.hidden = !codigoDeTeste;
  if (codigoDeTeste) document.getElementById('confirmacao-teste-codigo').textContent = codigoDeTeste;
  document.getElementById('confirmacao-codigo').value = '';
  document.getElementById('confirmacao-box').hidden = false;
  document.getElementById('confirmacao-codigo').focus();
}

document.getElementById('confirmacao-codigo').addEventListener('input', e => {
  e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6);
});

document.getElementById('confirmacao-confirmar').addEventListener('click', async e => {
  const campo = document.getElementById('confirmacao-codigo');
  if (!/^\d{6}$/.test(campo.value)) {
    avisar('Digite o código de 6 dígitos que chegou no seu e-mail.');
    campo.focus();
    return;
  }
  const botao = e.currentTarget;
  botao.disabled = true;
  try {
    await api('/auth/confirmar-email', { metodo: 'POST', corpo: { email: emailPendente, codigo: campo.value }, autenticado: false });
    const email = emailPendente;
    mostrarAba('entrar');
    document.getElementById('email').value = email;
    document.getElementById('senha').focus();
    avisar('E-mail confirmado! Agora é só entrar com sua senha.', 'ok');
  } catch (erro) {
    avisar(erro.message);
  }
  botao.disabled = false;
});

document.getElementById('confirmacao-reenviar').addEventListener('click', async e => {
  const botao = e.currentTarget;
  botao.disabled = true;
  try {
    const resposta = await api('/auth/reenviar-confirmacao', { metodo: 'POST', corpo: { email: emailPendente }, autenticado: false });
    mostrarConfirmacao(emailPendente, resposta.mensagem, resposta.codigoDeTeste);
  } catch (erro) {
    avisar(erro.message);
  }
  botao.disabled = false;
});

document.getElementById('confirmacao-entrar').addEventListener('click', () => {
  mostrarAba('entrar');
  document.getElementById('email').value = emailPendente;
  document.getElementById('senha').focus();
});

async function entrar(email, senha, lembrar) {
  const resposta = await api('/auth/login', { metodo: 'POST', corpo: { email, senha }, autenticado: false });
  Sessao.salvar(resposta.token, lembrar);
  location.href = destinoSolicitado || paginaInicial(resposta.perfil);
}

formEntrar.addEventListener('submit', async e => {
  e.preventDefault();
  if (!validarFormulario(formEntrar)) return;
  ocupado(formEntrar, true, 'Entrando...');
  try {
    await entrar(
      document.getElementById('email').value.trim(),
      document.getElementById('senha').value,
      document.getElementById('lembrar').checked);
  } catch (erro) {
    if (erro.codigo === 'EMAIL_NAO_CONFIRMADO') {
      avisar('');
      mostrarConfirmacao(document.getElementById('email').value.trim(), erro.message, null);
    } else {
      avisar(erro.status === 401 ? 'Email ou senha incorretos.' : erro.message);
    }
    ocupado(formEntrar, false);
  }
});

formCadastro.addEventListener('submit', async e => {
  e.preventDefault();
  if (!validarFormulario(formCadastro)) return;

  const senha = document.getElementById('cadastro-senha').value;
  if (senha !== document.getElementById('cadastro-senha2').value) {
    avisar('As senhas não são iguais.');
    document.getElementById('cadastro-senha2').focus();
    return;
  }

  const email = document.getElementById('cadastro-email').value.trim();
  ocupado(formCadastro, true, 'Criando conta...');
  try {
    const criada = await api('/auth/registro', {
      metodo: 'POST',
      autenticado: false,
      corpo: {
        nome: document.getElementById('nome').value.trim(),
        email,
        cpf: document.getElementById('cpf').value,
        telefone: document.getElementById('telefone').value,
        senha
      }
    });
    avisar('');
    formCadastro.hidden = true;
    mostrarConfirmacao(email, `Conta criada! Enviamos um código de verificação de 6 dígitos para ${email}. Digite o código abaixo para ativar sua conta (vale 60 minutos). Só depois da confirmação é possível entrar.`, criada.codigoDeTeste);
  } catch (erro) {
    avisar(erro.message);
    ocupado(formCadastro, false);
  }
});

mostrarAba(params.get('aba') === 'cadastro' ? 'cadastro' : 'entrar');
if (params.get('msg') === 'senha-alterada') avisar('Senha alterada com sucesso. Entre com a nova senha.', 'ok');
if (params.get('expirou')) avisar('Entre na sua conta para continuar.');
const sessaoAtual = Sessao.dados();
if (sessaoAtual && !params.get('msg')) {
  const papel = { LOCATARIO: 'locatário', LOCADOR: 'proprietário', ADMIN: 'administrador' }[sessaoAtual.perfil];
  document.getElementById('ja-logado-texto').textContent =
    `Você já está logado como ${sessaoAtual.nome} (${papel}). Para criar outra conta ou entrar com outro perfil, saia primeiro.`;
  document.getElementById('ja-logado-painel').href = destinoSolicitado || paginaInicial(sessaoAtual.perfil);
  document.getElementById('ja-logado').hidden = false;
  document.getElementById('ja-logado-sair').addEventListener('click', () => {
    Sessao.limpar();
    document.getElementById('ja-logado').hidden = true;
  });
}
