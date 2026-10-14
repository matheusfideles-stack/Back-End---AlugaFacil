function pendentes(){
  const rows = [
    ['Casa — Rua Girassol, 210','Fábio Nogueira','3.060,00'],
    ['Sobrado — Rua das Acácias, 55','Camila Reis','1.890,00'],
    ['Loja — Av. Central, 300','Diego Martins','2.700,00'],
  ];
  return `
    <p class="eyebrow">Validações</p>
    <h1 class="pagetitle">Pendentes de análise</h1>
    <div class="ledger">
      ${rows.map(([t,n,v],i)=>`
        <div class="ledger-row" ${i===0?'data-goto-detalhe':''} style="${i===0?'cursor:pointer;':''}">
          <div class="main-col">
            <p class="title">${t}</p>
            <p class="sub">Inquilino: ${n} · comprovante enviado hoje</p>
          </div>
          <span class="tag pendente"><span class="dot"></span>Aguardando validação</span>
          <div class="amount">R$ ${v}</div>
        </div>
      `).join('')}
    </div>
  `;
}

function detalheValidacao(){
  return `
    <p class="eyebrow">Validação</p>
    <h1 class="pagetitle">Casa — Rua Girassol, 210</h1>
    <div class="status-banner" id="validateBanner">Validado — repasse ao proprietário agendado para 10 out.</div>
    <div class="review-card">
      <div class="receipt-mock">
        <div class="rline w40"></div>
        <div class="rline w80"></div>
        <div class="rline w60"></div>
        <p class="rvalue">R$ 3.060,00</p>
        <div class="rline w60"></div>
        <div class="rline w40"></div>
        <div class="stamp" id="stamp">VALIDADO<br>07/09/2026</div>
      </div>
      <div class="review-details">
        <div class="field"><p class="fk">Inquilino</p><p class="fv">Fábio Nogueira</p></div>
        <div class="field"><p class="fk">Valor esperado</p><p class="fv">R$ 3.400,00</p></div>
        <div class="field"><p class="fk">Valor no comprovante</p><p class="fv">R$ 3.060,00 (aluguel com desconto de reparo combinado)</p></div>
        <div class="field"><p class="fk">Enviado em</p><p class="fv">07 set 2026, 09:14</p></div>
        <div class="actions-row">
          <button class="btn btn-primary" id="validarBtn">Validar comprovante</button>
          <button class="btn btn-danger">Rejeitar</button>
        </div>
      </div>
    </div>
  `;
}

window.AlugaFacil.imobiliaria = {
  render(page){
    return page === 0 ? pendentes() : detalheValidacao();
  },
  wire(page, goToPage){
    if(page === 0){
      document.querySelectorAll('[data-goto-detalhe]').forEach(el=>{
        el.addEventListener('click', ()=> goToPage(1));
      });
      return;
    }
    const btn = document.getElementById('validarBtn');
    const stamp = document.getElementById('stamp');
    const banner = document.getElementById('validateBanner');
    btn.addEventListener('click', ()=>{
      stamp.classList.add('show');
      setTimeout(()=> banner.classList.add('show'), 300);
      btn.disabled = true;
      btn.textContent = 'Validado';
    });
  }
};
