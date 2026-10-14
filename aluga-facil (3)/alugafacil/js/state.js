window.AlugaFacil = window.AlugaFacil || {};

const state = { role: 'proprietario', page: 0 };

const roleConfig = {
  proprietario: {
    who: { name: 'Renata Bittencourt', sub: '3 imóveis alugados' },
    pages: ['Visão geral', 'Repasses'],
  },
  inquilino: {
    who: { name: 'Mariana Duarte', sub: 'Apto 302 · Rua das Palmeiras, 88' },
    pages: ['Meu aluguel', 'Enviar comprovante'],
  },
  imobiliaria: {
    who: { name: 'Imobiliária Bairro Novo', sub: '7 validações pendentes' },
    pages: ['Validações pendentes', 'Detalhe da validação'],
  },
};

window.AlugaFacil.state = state;
window.AlugaFacil.roleConfig = roleConfig;
