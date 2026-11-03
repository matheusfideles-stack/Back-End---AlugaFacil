# Colocar o AlugaFácil em produção

Este guia cobre a segurança mínima antes de abrir o sistema para outras pessoas: segredo do login (JWT), senha do administrador e HTTPS.

## O que o perfil `prod` faz

Ligue com `SPRING_PROFILES_ACTIVE=prod`. Nesse perfil:

- O sistema **se recusa a subir** se o `JWT_SECRET` não existir, for o valor de exemplo (ou tiver "troque"/"trocar" no texto) ou tiver menos de 32 caracteres.
- Se o `ADMIN_SENHA` estiver definido, precisa ter pelo menos 10 caracteres.
- Se já existir um administrador no banco com uma senha conhecida (como `Admin1234`), o sistema não sobe e diz qual e-mail precisa trocar a senha.
- **HTTPS é obrigatório**: quem entra por HTTP é redirecionado para HTTPS, e o navegador recebe o cabeçalho HSTS.
- Os códigos de verificação **não aparecem** na tela, só chegam por e-mail. Configure o SMTP (veja `.env.example`).
- **Pagamento de verdade:** `PAGAMENTO_PROVIDER` precisa ser `mercadopago` (o gateway `fake` aprova tudo sem cobrar), e `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` e `MP_NOTIFICATION_URL` (endereço `https://` do webhook) precisam estar definidos. Sem o segredo do webhook qualquer pessoa poderia enviar avisos falsos de pagamento, e sem a URL o Pix e o boleto nunca passariam para "pago" sozinhos.

Quando algo está errado, a mensagem começa com `Configuracao insegura para producao` e lista cada problema. Ela nunca mostra o segredo nem a senha.

## Variáveis obrigatórias

| Variável | Para quê |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | Chave que assina o login. Mínimo 32 caracteres, aleatória |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Banco MySQL |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | Primeiro administrador (criado só se ainda não existir) |
| `APP_URL_BASE` | Endereço público com `https://`, usado nos e-mails |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | E-mail que envia os códigos |
| `PAGAMENTO_PROVIDER` (`mercadopago`), `MP_ACCESS_TOKEN` | Pagamentos reais (Mercado Pago). O token fica só no servidor |
| `MP_NOTIFICATION_URL`, `MP_WEBHOOK_SECRET` | Webhook: `https://seu-dominio/webhooks/mercadopago` e o segredo que o painel do Mercado Pago mostra ao cadastrar o webhook |
| `MP_PUBLIC_KEY` | Chave pública, usada só para cartão. Passo a passo em `docs/PAGAMENTOS.md` |

Nunca coloque esses valores no código nem no GitHub. Use o arquivo `.env` (ignorado pelo git) ou as variáveis do servidor.

## 1. Gerar o segredo do JWT

No PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Em Linux ou macOS:

```bash
openssl rand -base64 48
```

Copie o resultado para `JWT_SECRET`. **Trocar o segredo desconecta todo mundo**, porque os logins antigos deixam de valer. Guarde o segredo em um lugar seguro: se você perdê-lo, basta gerar outro.

## 2. Senha do administrador

Escolha uma senha longa (10 caracteres ou mais) e única. Defina `ADMIN_EMAIL` e `ADMIN_SENHA` antes da primeira subida em produção.

Se o sistema já tinha sido usado com a senha padrão (`Admin1234`, do perfil de teste), ele recusa subir em `prod` até a senha ser trocada. Duas formas:

1. **Pela tela:** suba uma vez com o perfil normal, entre em "Esqueceu a senha?" com o e-mail do administrador e defina a nova senha pelo código recebido por e-mail. Depois suba em `prod`.
2. **Recriando o administrador:** apague o usuário administrador antigo do banco e suba em `prod` com `ADMIN_EMAIL` e `ADMIN_SENHA` novos. O sistema cria o administrador de novo.

## 3. HTTPS

O sistema não termina o HTTPS sozinho. O jeito recomendado é colocar um **proxy reverso** na frente, que cuida do certificado e repassa o tráfego para a aplicação na porta 8080. O perfil `prod` já entende os cabeçalhos do proxy (`X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port`).

Exemplo com **Caddy** (certificado gratuito e renovação automática, precisa de um domínio apontando para o servidor e das portas 80 e 443 abertas). Arquivo `Caddyfile`:

```
meusite.com.br {
    reverse_proxy localhost:8080
}
```

Exemplo com **nginx** (o certificado vem do seu provedor ou do Certbot):

```nginx
server {
    listen 80;
    server_name meusite.com.br;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name meusite.com.br;
    ssl_certificate     /etc/letsencrypt/live/meusite.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/meusite.com.br/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port  $server_port;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 55m;
    }
}
```

`client_max_body_size 55m` é necessário porque os proprietários enviam até 10 fotos de 5 MB.

Estes exemplos seguem a documentação do Caddy e do nginx, mas **não foram testados neste projeto**. Teste em um servidor de ensaio antes de depender deles.

**Importante:** não deixe a porta 8080 aberta para a internet. Libere só as portas 80 e 443 do proxy. Quem acessar a 8080 direto passa por fora do HTTPS.

## Checklist antes de abrir ao público

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `JWT_SECRET` próprio, com 32 caracteres ou mais
- [ ] `ADMIN_SENHA` forte, ou administrador antigo com a senha trocada
- [ ] HTTPS funcionando e a porta 8080 fechada para fora
- [ ] SMTP configurado e testado (cadastre uma conta e confira se o código chega)
- [ ] Credenciais de produção do Mercado Pago (`MP_ACCESS_TOKEN`) e webhook cadastrado no painel (`MP_NOTIFICATION_URL` + `MP_WEBHOOK_SECRET`)
- [ ] Backup do banco e da pasta `uploads/`
