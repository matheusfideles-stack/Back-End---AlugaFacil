// Tela de login e criação de conta (fictícia, sem API)
const formEntrar = document.getElementById('form-entrar');
const formCadastro = document.getElementById('form-cadastro');
const aviso = document.getElementById('aviso');
const params = new URLSearchParams(location.search);

// Só aceita voltar para a página de um imóvel (evita redirecionar para outros sites)
function destinoSeguro(valor) {
  return valor && /^imovel\.html\?id=\d+$/.test(valor) ? valor : null;
}
const destinoSolicitado = destinoSeguro(params.get('redirect'));

function mostrarAviso(mensagem, tipo = 'erro') {
  aviso.textContent = mensagem;
  aviso.className = 'aviso' + (tipo === 'ok' ? ' ok' : '');
  aviso.hidden = !mensagem;
}

function mostrarAba(nome) {
  const cadastro = nome === 'cadastro';
  formEntrar.hidden = cadastro;
  formCadastro.hidden = !cadastro;
  document.querySelectorAll('.abas a[data-aba]').forEach(a => a.classList.toggle('ativo', a.dataset.aba === nome));
  document.getElementById('titulo-lado').textContent = cadastro ? 'Crie sua conta!' : 'Bem-vindo de volta!';
  document.getElementById('sub-lado').textContent = cadastro
    ? 'Cadastre-se para ver os imóveis disponíveis e alugar com praticidade.'
    : 'Acesse sua conta para continuar gerenciando seus imóveis.';
  mostrarAviso('');
}

document.addEventListener('click', e => {
  const link = e.target.closest('[data-aba], [data-trocar-aba]');
  if (!link) return;
  e.preventDefault();
  mostrarAba(link.dataset.aba || link.dataset.trocarAba);
});

// Mostra na tela qual campo está errado (o balão nativo do navegador some rápido demais)
function validarFormulario(form) {
  const campoInvalido = [...form.querySelectorAll('input')].find(campo => !campo.checkValidity());
  if (!campoInvalido) return true;
  const nomeCampo = document.querySelector(`label[for="${campoInvalido.id}"]`).textContent.trim();
  if (campoInvalido.type === 'password' && campoInvalido.value && campoInvalido.value.length < 8) {
    mostrarAviso('A senha precisa ter pelo menos 8 caracteres.');
  } else if (campoInvalido.type === 'email' && campoInvalido.value) {
    mostrarAviso('Digite um email válido, por exemplo: nome@email.com.');
  } else {
    mostrarAviso(`Preencha o campo "${nomeCampo}".`);
  }
  campoInvalido.focus();
  return false;
}

// Depois do login: volta para o imóvel de onde a pessoa veio, ou vai para a tela do cliente
function destinoAposLogin() {
  return destinoSolicitado || 'cliente.html';
}

formEntrar.addEventListener('submit', e => {
  e.preventDefault();
  if (!validarFormulario(formEntrar)) return;
  iniciarSessaoLocal();
  location.href = destinoAposLogin();
});

// Cadastro fictício: mostra sucesso e segue para o destino
formCadastro.addEventListener('submit', e => {
  e.preventDefault();
  if (!validarFormulario(formCadastro)) return;
  mostrarAviso('Conta criada com sucesso! Entrando...', 'ok');
  iniciarSessaoLocal();
  setTimeout(() => { location.href = destinoAposLogin(); }, 800);
});

mostrarAba(params.get('aba') === 'cadastro' ? 'cadastro' : 'entrar');