# E-mail do AlugaFácil

O sistema envia e-mail em cinco situações:

- **Código de verificação** ao criar a conta (e ao pedir um novo código).
- **Código para redefinir a senha**, quando a pessoa esquece a senha.
- **Mensagem do formulário de contato** da página de Ajuda, enviada para o e-mail de suporte.
- **Pagamento confirmado**: quando um pagamento passa para "pago" (na hora, no cartão, ou depois, quando o Pix ou boleto é compensado), o locatário recebe o comprovante (`PG-000123`) e o locador recebe o aviso de recebimento, com imóvel, mês, valor, forma de pagamento e data. O aviso sai uma única vez por pagamento, mesmo que o gateway repita a notificação, e só depois do pagamento estar gravado. Pagamento recusado ou ainda pendente não envia nada.
- **Lembrete de vencimento**: todo dia às 9h (horário de Brasília) o sistema olha os contratos ativos e avisa o locatário que ainda não pagou o mês. Os avisos são: 3 dias antes do vencimento, no dia, e com 1, 3 e 7 dias de atraso. Com 3 dias de atraso o locador também é avisado. Cada aviso sai uma vez só por contrato e mês (o sistema guarda o registro, então reiniciar o servidor não repete e-mail). Se o servidor estiver fora do ar no dia exato, sai só o aviso mais recente quando voltar. Mês já pago ou em processamento não recebe lembrete. O lembrete **não cria cobrança**: ele só leva a pessoa à tela de pagamentos. Para desligar, use `APP_LEMBRETES_ATIVO=false`.

Sem SMTP configurado, nada é enviado: o texto só aparece no log do servidor, e no perfil `dev` o código também aparece na tela, para facilitar o teste. **Em produção (`prod`) o sistema não sobe sem `SPRING_MAIL_HOST`**, porque sem e-mail ninguém consegue confirmar a conta.

Se o envio falhar (servidor de e-mail fora do ar, senha errada), o cadastro não é perdido: o erro vai para o log e a pessoa pode pedir "Reenviar código". O sistema desiste de falar com o servidor de e-mail depois de 10 segundos.

## Opção 1: Gmail (para o P.I e testes pequenos)

1. Na conta Google, ative a **verificação em duas etapas**.
2. Crie uma **senha de app** em https://myaccount.google.com/apppasswords. Ela tem 16 letras e é diferente da sua senha normal.
3. Copie `config/application.yml.exemplo` para `config/application.yml` (esse arquivo não vai para o GitHub) e preencha:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: seuemail@gmail.com
    password: a-senha-de-app-de-16-letras

app:
  email-remetente: seuemail@gmail.com
  email-suporte: seuemail@gmail.com
  url-base: http://localhost:8080
```

4. Reinicie o sistema. No log de subida deve aparecer: `E-mail configurado: mensagens ... serao enviadas de verdade.`

O `email-remetente` precisa ser o mesmo endereço do Gmail, senão o Gmail troca o remetente. O Gmail limita o envio a cerca de 500 mensagens por dia, e as mensagens podem cair no spam.

## Opção 2: serviço de e-mail (para produção)

Use o SMTP de um serviço como Brevo, Mailgun, Amazon SES ou Resend, com um endereço do **seu domínio** (por exemplo `nao-responda@meusite.com.br`). Cada serviço mostra o servidor, a porta, o usuário e a senha. Configure também o SPF e o DKIM do domínio, como o serviço pedir, para as mensagens não irem para o spam.

No Docker, coloque no arquivo `.env`:

```
SPRING_MAIL_HOST=smtp.seuservico.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=usuario-do-servico
SPRING_MAIL_PASSWORD=senha-do-servico
MAIL_REMETENTE=nao-responda@meusite.com.br
MAIL_SUPORTE=suporte@meusite.com.br
APP_URL_BASE=https://meusite.com.br
```

Sem Docker, use as mesmas variáveis no ambiente, ou o `config/application.yml`.

## Testar sem usar o seu e-mail (Mailpit)

O Mailpit é uma caixa de e-mail de mentira: recebe tudo que o sistema enviar e mostra em uma página, sem mandar nada para fora. Ótimo para conferir se o código chega.

No PowerShell, na raiz do projeto:

```powershell
$env:PAGAMENTO_PROVIDER = 'fake'
$env:SPRING_MAIL_HOST = 'mailpit'
$env:SPRING_MAIL_PORT = '1025'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = 'false'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = 'false'
docker compose --profile email-teste up --build -d
```

Crie uma conta em http://localhost:8080 e abra **http://localhost:8025**: o e-mail com o código está lá. O mesmo vale para "Esqueci a senha" e para o formulário de contato.

## Se não funcionar

| Sintoma no log | Causa provável |
|---|---|
| `535 ... Username and Password not accepted` | Senha errada. No Gmail, use a **senha de app**, não a senha normal |
| `Could not connect ... timeout` | A porta 587 está bloqueada (firewall, rede da faculdade). Tente outra rede ou outra porta (465) |
| Aparece `E-mail NAO configurado` na subida | O host de e-mail não chegou ao sistema. Confira as variáveis ou o `config/application.yml` |
| O e-mail foi enviado mas não chega | Olhe o spam. Em produção, configure SPF e DKIM do domínio |
