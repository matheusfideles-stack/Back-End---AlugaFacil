function visaoGeral(){
  return `
    <p class="eyebrow">Visão geral</p>
    <h1 class="pagetitle">Seus imóveis</h1>
    <div class="summary-row">
      <div class="summary-card"><p class="k">Repasse do mês</p><p class="v">R$ 6.215<small>,00</small></p></div>
      <div class="summary-card"><p class="k">Próximo repasse</p><p class="v">10 out</p></div>
      <div class="summary-card"><p class="k">Aguardando validação</p><p class="v">1 <small>comprovante</small></p></div>
    </div>
    <section class="block">
      <h3>Imóveis</h3>
      <div class="ledger">
        <div class="ledger-row">
          <div class="main-col">
            <p class="title">Apto 302 — Rua das Palmeiras, 88</p>
            <p class="sub">Inquilina: Mariana Duarte · vencimento dia 5</p>
          </div>
          <span class="tag validado"><span class="dot"></span>Repassado</span>
          <div class="amount">R$ 2.205<span class="label">de R$ 2.450</span></div>
        </div>
        <div class="ledger-row">
          <div class="main-col">
            <p class="title">Casa — Rua Girassol, 210</p>
            <p class="sub">Inquilino: Fábio Nogueira · vencimento dia 8</p>
          </div>
          <span class="tag pendente"><span class="dot"></span>Em validação</span>
          <div class="amount">R$ 3.060<span class="label">de R$ 3.400</span></div>
        </div>
        <div class="ledger-row">
          <div class="main-col">
            <p class="title">Kitnet — Av. Brasil, 1450</p>
            <p class="sub">Inquilina: Joana Prado · vencimento dia 12</p>
          </div>
          <span class="tag validado"><span class="dot"></span>Repassado</span>
          <div class="amount">R$ 950<span class="label">de R$ 1.050</span></div>
        </div>
      </div>
    </section>
  `;
}

function repasses(){
  const rows = [
    ['10 set 2026','Apto 302 — Rua das Palmeiras, 88','2.205,00','validado','Repassado'],
    ['10 set 2026','Kitnet — Av. Brasil, 1450','950,00','validado','Repassado'],
    ['10 ago 2026','Apto 302 — Rua das Palmeiras, 88','2.205,00','validado','Repassado'],
    ['10 ago 2026','Casa — Rua Girassol, 210','3.060,00','rejeitado','Comprovante rejeitado'],
    ['10 ago 2026','Kitnet — Av. Brasil, 1450','950,00','validado','Repassado'],
  ];
  return `
    <p class="eyebrow">Financeiro</p>
    <h1 class="pagetitle">Histórico de repasses</h1>
    <div class="ledger">
      ${rows.map(([d,t,v,cls,label])=>`
        <div class="ledger-row">
          <div class="main-col">
            <p class="title">${t}</p>
            <p class="sub">${d} · taxa de administração 10% já descontada</p>
          </div>
          <span class="tag ${cls}"><span class="dot"></span>${label}</span>
          <div class="amount">R$ ${v}</div>
        </div>
      `).join('')}
    </div>
  `;
}

window.AlugaFacil.proprietario = {
  render(page){
    return page === 0 ? visaoGeral() : repasses();
  },
  wire(){
    // Nenhuma interação nesta tela por enquanto.
  }
};
