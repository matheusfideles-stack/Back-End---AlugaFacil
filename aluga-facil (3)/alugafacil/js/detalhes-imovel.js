// Card com os detalhes do imóvel, aberto pelo botão "Ver" da lista (sem sair da tela)
(function () {
    var modal = document.getElementById('modal-imovel');
    if (!modal) return;

    var titulo = document.getElementById('det-titulo');
    var status = document.getElementById('det-status');
    var corpo = document.getElementById('det-corpo');
    var editar = document.getElementById('det-editar');
    var fecharBtn = document.getElementById('det-fechar');

    function linha(rotulo, valor) {
        return '<div class="linha-det"><span class="info">' + esc(rotulo) + '</span><strong>' + esc(valor) + '</strong></div>';
    }

    function abrir(id) {
        var imovel = imoveis.find(function (i) { return i.id === id; });
        if (!imovel) return;
        var dono = inquilinos.find(function (t) { return t.id === imovel.inquilino; });

        titulo.textContent = imovel.nome;
        status.textContent = imovel.status;
        status.className = 'selo' + (imovel.status === 'Disponível' ? ' alerta' : '');
        status.style.margin = '0';
        corpo.innerHTML = [
            linha('Tipo', imovel.tipo || 'Não informado'),
            linha('Endereço', (imovel.endereco || 'Não informado') + ' - ' + (imovel.bairro || '') + ', ' + (imovel.cidade || '')),
            linha('Quartos', imovel.quartos ?? 'Não informado'),
            linha('Banheiros', imovel.banheiros ?? 'Não informado'),
            linha('Área', (imovel.area ?? 'Não informado') + ' m²'),
            linha('Vagas', imovel.vagas ?? 'Não informado'),
            linha('Valor mensal', formatos.dinheiro(imovel.valor || 0)),
            linha('Inquilino', dono ? dono.nome : 'Sem inquilino no momento'),
            linha('Descrição', imovel.descricao || 'Não informado')
        ].join('');
        editar.href = 'editar-imovel.html?id=' + imovel.id;

        modal.hidden = false;
        fecharBtn.focus();
    }

    function fechar() {
        modal.hidden = true;
    }

    document.getElementById('lista').addEventListener('click', function (e) {
        var botao = e.target.closest('[data-ver]');
        if (botao) abrir(Number(botao.dataset.ver));
    });
    fecharBtn.addEventListener('click', fechar);
    modal.addEventListener('click', function (e) {
        if (e.target === modal) fechar();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !modal.hidden) fechar();
    });
})();