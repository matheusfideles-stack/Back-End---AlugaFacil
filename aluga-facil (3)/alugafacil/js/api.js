// Funções compartilhadas por todas as telas. Por enquanto tudo é fictício (sem API nem banco).

// Sessão guardada no navegador (login fictício)
const Sessao = {
  salvar(token) {
    localStorage.setItem('alugafacil_token', token);
  },
  limpar() {
    localStorage.removeItem('alugafacil_token');
  },
  token() {
    return localStorage.getItem('alugafacil_token');
  },
  // Lê os dados guardados no token. Retorna null se a sessão não existir ou tiver expirado.
  dados() {
    const token = this.token();
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const dados = JSON.parse(atob(base64));
      if (dados.exp * 1000 <= Date.now()) {
        this.limpar();
        return null;
      }
      return dados;
    } catch (erro) {
      this.limpar();
      return null;
    }
  },
  logado() {
    return this.dados() !== null;
  }
};

// Cria uma sessão fictícia de 2 horas
function iniciarSessaoLocal() {
  const base64 = obj => btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + 2 * 60 * 60;
  Sessao.salvar(`${base64({ alg: 'HS256', typ: 'JWT' })}.${base64({ perfil: 'LOCATARIO', exp })}.ficticio`);
}

const formatarMoeda = valor => {
  const numero = Number(valor || 0);
  return numero.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: numero % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2
  });
};

const escaparHtml = texto => String(texto ?? '').replace(/[&<>"']/g, c => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[c]);

const rotuloTipo = {
  CASA: 'Casa',
  APARTAMENTO: 'Apartamento',
  KITNET: 'Kitnet',
  SALA_COMERCIAL: 'Sala comercial',
  GALPAO: 'Galpão'
};

// Foto do imóvel: usa img/imovel-{id}.jpg. Se o arquivo não existir, a área fica com o degradê padrão.
function fotoImovel(id, classeExtra = '') {
  return `<div class="foto ${classeExtra}"><img src="img/imovel-${id}.jpg" alt="" onerror="this.remove()"></div>`;
}

// Imóveis fictícios (mesmo formato que um imóvel teria no sistema)
const IMOVEIS = [
  { id: 1, titulo: 'Apartamento Vila Nova - 3 quartos', descricao: 'Sol da manhã, armários planejados e garagem coberta.\nPerto de mercado e escola.', tipo: 'APARTAMENTO', logradouro: 'Rua das Palmeiras', numero: '88', complemento: 'Apto 302', bairro: 'Vila Nova', cidade: 'Patos de Minas', uf: 'MG', cep: '38700-000', valorAluguel: 2800, valorCondominio: 350, nomeLocador: 'Renata Bittencourt' },
  { id: 2, titulo: 'Casa com quintal - Bairro Novo', descricao: 'Três quartos, duas suítes, quintal amplo.', tipo: 'CASA', logradouro: 'Rua das Flores', numero: '120', complemento: null, bairro: 'Bairro Novo', cidade: 'Patos de Minas', uf: 'MG', cep: '38700-100', valorAluguel: 3200, valorCondominio: 0, nomeLocador: 'Renata Bittencourt' },
  { id: 3, titulo: 'Kitnet mobiliada - Centro', descricao: null, tipo: 'KITNET', logradouro: 'Av. Major Gote', numero: '410', complemento: null, bairro: 'Centro', cidade: 'Patos de Minas', uf: 'MG', cep: '38700-200', valorAluguel: 1800, valorCondominio: 0, nomeLocador: 'Imobiliária Bairro Novo' },
  { id: 4, titulo: 'Sala comercial - Centro', descricao: 'Ótima para consultórios.', tipo: 'SALA_COMERCIAL', logradouro: 'Rua Olegário Maciel', numero: '55', complemento: 'Sala 4', bairro: 'Centro', cidade: 'Patos de Minas', uf: 'MG', cep: '38700-300', valorAluguel: 2450.5, valorCondominio: 200, nomeLocador: 'Imobiliária Bairro Novo' }
];

// Lista de imóveis da vitrine
function buscarImoveis() {
  return { lista: IMOVEIS, exemplo: false };
}

// Um imóvel pelo id
function buscarImovel(id) {
  const imovel = IMOVEIS.find(i => String(i.id) === String(id));
  if (!imovel) throw new Error('Imóvel não encontrado.');
  return imovel;
}

// Clique em "Alugar": sem sessão vai para o login, que depois devolve para o imóvel
function irParaImovel(id) {
  const destino = `imovel.html?id=${id}`;
  location.href = Sessao.logado() ? destino : `login.html?redirect=${encodeURIComponent(destino)}`;
}

// Troca o botão "Entrar" por "Sair" quando já existe sessão
function atualizarBotaoSessao(botao) {
  if (botao && Sessao.logado()) {
    botao.textContent = 'Sair';
    botao.href = 'sair.html';
  }
}