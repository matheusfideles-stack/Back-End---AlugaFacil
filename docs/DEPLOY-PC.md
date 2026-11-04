# Rodar em produção no seu próprio computador (Tailscale Funnel)

Alternativa ao [`DEPLOY.md`](DEPLOY.md) quando não há servidor: o sistema roda no seu PC, com pagamento real, e o Tailscale Funnel dá um endereço
`https://seupc.xxxx.ts.net` fixo e gratuito para o site e para o webhook do Mercado Pago.

> **Limites:** o computador precisa ficar **ligado, com internet e sem suspender**, senão o site cai. O plano gratuito do Tailscale é para
> uso **não comercial**. Faça backup toda semana ([`BACKUP.md`](BACKUP.md)).

## Passos

1. **Tailscale:** instale o Tailscale for Windows, entre com uma conta e, no painel (login.tailscale.com), ligue o **HTTPS** em *DNS > Enable HTTPS* e libere o Funnel para o seu aparelho.
2. **Endereço:** rode `tailscale funnel 8080`. Ele mostra o endereço público (`https://seupc.xxxx.ts.net`). Esse nome, **sem `https://`**, é o seu `DOMINIO`.
3. **`.env`:** copie `.env.example` para `.env` e preencha como na seção 6 do [`DEPLOY.md`](DEPLOY.md) (`DOMINIO`, `JWT_SECRET`, senhas do banco, administrador, Mercado Pago de produção e SMTP).
4. **Subir:**
   ```bash
   docker compose -f docker-compose.prod.yml -f docker-compose.pc.yml up -d --build db app
   ```
5. **Webhook** no Mercado Pago (modo produção): `https://SEU-DOMINIO/webhooks/mercadopago`, evento Pagamentos; ponha o segredo em `MP_WEBHOOK_SECRET` e reinicie.
6. **Deixar o PC sempre pronto:** Docker Desktop abrindo com o Windows, suspensão desligada (Configurações > Sistema > Energia) e o Funnel ativo.
7. **Testar** com um Pix real de poucos reais e estornar.

Atualizar: `git pull` e o mesmo comando do passo 4.
