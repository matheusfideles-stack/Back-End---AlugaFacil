# Deploy gratuito do AlugaFácil (Oracle Cloud + DuckDNS)

Guia para colocar o sistema no ar, com banco, fotos, HTTPS e pagamento real, sem pagar hospedagem e sem comprar domínio.
Antes, leia [`PRODUCAO.md`](PRODUCAO.md) (segurança) e [`PAGAMENTOS.md`](PAGAMENTOS.md) (Mercado Pago).

> **Aviso honesto:** hospedagem gratuita não tem garantia. A Oracle pode ficar sem máquinas livres na sua região, reduziu o
> plano gratuito em 2026 e pode apagar contas paradas. Para dinheiro de verdade, faça **backup toda semana** (passo 9) e
> guarde uma cópia fora da máquina. Se o dinheiro passar a depender do sistema, migre para um servidor pago (a partir de ~R$ 25/mês); os
> arquivos deste guia funcionam igual em qualquer servidor com Docker.

## Como fica

```
internet --(80/443)--> Caddy (HTTPS) --> aplicação (porta 8080, só interna) --> MySQL (só interno)
```

Só o Caddy aparece para a internet. O banco e a aplicação não têm portas abertas. Os arquivos usados:
`docker-compose.prod.yml`, `Caddyfile` e o seu `.env` (que fica só no servidor).

## 1. Criar a máquina na Oracle Cloud

1. Crie a conta em oracle.com/cloud/free. Pede um cartão só para confirmar identidade; no plano Always Free não cobra.
2. Crie uma instância **Compute** com a forma **Ampere A1 (ARM)**, **2 OCPU e 12 GB** (o limite gratuito atual, confira na página da Oracle), sistema **Ubuntu 24.04** e disco de 50 GB.
3. Se aparecer **"Out of host capacity"**, tente de novo mais tarde ou em outro domínio de disponibilidade. É comum.
4. Baixe a chave SSH quando a Oracle oferecer e guarde-a. Sem ela você não entra na máquina.
5. Em **Rede > IP público reservado**, reserve o IP e ligue à instância, para ele não mudar.

## 2. Abrir só as portas 80 e 443

No painel da Oracle: **Rede > sua VCN > Security List > Ingress Rules**, adicione TCP 80 e TCP 443 de `0.0.0.0/0`.
**Não abra 8080 nem 3306.**

O Ubuntu da Oracle também tem um firewall interno que bloqueia tudo. Dentro da máquina:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent && sudo netfilter-persistent save
```

## 3. Endereço gratuito com DuckDNS

1. Entre em duckdns.org e crie um subdomínio, por exemplo `alugafacil-patos`. O endereço será `alugafacil-patos.duckdns.org`.
2. No campo de IP, ponha o **IP público reservado** da máquina e salve.
3. Anote o endereço completo: ele vai em `DOMINIO` no `.env`.

## 4. Instalar o Docker na máquina

```bash
ssh -i sua-chave.key ubuntu@IP_DA_MAQUINA
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && exit   # entre de novo para valer
```

## 5. Levar o projeto

```bash
git clone https://github.com/SEU-USUARIO/SEU-REPOSITORIO.git alugafacil
cd alugafacil
```

O `.env` não vem no git (de propósito). Você o cria na máquina no próximo passo.

## 6. Criar o `.env` no servidor

```bash
cp .env.example .env
nano .env
```

Preencha (valores só na máquina, nunca no git nem em conversa):

| Variável | O que pôr |
|---|---|
| `DOMINIO` | `alugafacil-patos.duckdns.org` (sem `https://`) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | duas senhas longas e diferentes |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | seu e-mail e uma senha de 10+ caracteres |
| `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` | credenciais de **produção** (`APP_USR-...`) |
| `MP_WEBHOOK_SECRET` | o segredo do passo 8 |
| `SPRING_MAIL_*`, `MAIL_REMETENTE`, `MAIL_SUPORTE` | SMTP (Gmail com senha de app); veja [`EMAIL.md`](EMAIL.md) |

`PAGAMENTO_PROVIDER`, `MP_NOTIFICATION_URL` e `APP_URL_BASE` o `docker-compose.prod.yml` já monta sozinho a partir do `DOMINIO`.

## 7. Subir

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f app
```

A primeira subida demora (compila o projeto). Espere a linha `Started AlugaFacilApplication`. Se aparecer
`Configuracao insegura para producao`, a mensagem diz o que corrigir no `.env`. Depois, abra `https://SEU-DOMINIO`.
O certificado HTTPS sai sozinho, em alguns segundos.

## 8. Webhook do Mercado Pago

No painel do Mercado Pago, em **Suas integrações > sua aplicação > Webhooks > Modo de produção**:

1. URL: `https://SEU-DOMINIO/webhooks/mercadopago`
2. Evento: **Pagamentos**.
3. Copie o segredo que o painel mostra para `MP_WEBHOOK_SECRET` no `.env` e reinicie: `docker compose -f docker-compose.prod.yml up -d`.

Sem isso, Pix e boleto pagos nunca passam para "pago" sozinhos.

## 9. Testar e fazer backup

1. Crie uma conta, confira se o código chega por e-mail e entre como administrador.
2. Cadastre um proprietário e um imóvel de valor baixo e pague um Pix real de poucos reais; depois estorne pela tela. Confira se o pagamento virou "pago" sem você clicar em nada.
3. Faça o primeiro backup e baixe uma cópia para o seu computador:

```bash
BACKUP_DB_SENHA='a-senha-do-DB_PASSWORD' ./scripts/backup.sh
```

Repita toda semana (veja [`BACKUP.md`](BACKUP.md), inclusive a restauração).

## Atualizar depois

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

O banco e as fotos ficam em volumes e não são apagados na atualização.

## Se der errado

| Sintoma | Causa provável |
|---|---|
| Site não abre | porta 80/443 fechada na Oracle ou no `iptables` (passo 2), ou DuckDNS com IP errado |
| Sem HTTPS | o endereço do DuckDNS ainda não aponta para a máquina; veja `docker compose -f docker-compose.prod.yml logs caddy` |
| Sistema não sobe | veja `logs app`; a mensagem `Configuracao insegura para producao` lista cada problema |
| Pagamento fica "processando" | webhook não cadastrado ou `MP_WEBHOOK_SECRET` errado (passo 8) |
| Código de e-mail não chega | SMTP errado; teste conforme [`EMAIL.md`](EMAIL.md) |
