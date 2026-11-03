# Pagamentos — integração Mercado Pago

Integração com a **API de pagamentos do Mercado Pago** (`POST /v1/payments`), cobrindo
**Pix, boleto bancário, cartão de crédito e cartão de débito**.

---

## 1. Como está montado

```
domain/pagamento/gateway/
  PaymentGateway            interface: criarCobranca / consultarCobranca / estornarCobranca
  SolicitacaoCobranca       dados que vão para o provedor (valor, método, pagador, token do cartão…)
  RespostaCobranca          resposta neutra (status já traduzido, QR Code, linha digitável…)
  Pagador                   nome, e-mail, CPF e endereço do locatário
  FakePaymentGateway        aprova tudo localmente, sem rede  (provider=fake)
  mercadopago/
    MercadoPagoGateway            integração real                 (provider=mercadopago)
    MercadoPagoProperties         configuração (token, chaves, prazos)
    MercadoPagoWebhookValidator   validação HMAC-SHA256 do webhook
controller/
  PagamentoController           /pagamentos
  MercadoPagoWebhookController  /webhooks/mercadopago  (público, sem JWT)
```

Trocar de provedor é só mudar `pagamento.gateway.provider`. Nenhuma outra camada conhece o
Mercado Pago.

### Dado importante: cartão nunca passa pelo backend

Número, CVV e validade **não podem** trafegar pela nossa API (exigência de PCI-DSS e do
próprio Mercado Pago). O fluxo correto é:

1. O frontend pega a `publicKey` em `GET /pagamentos/config`.
2. Tokeniza o cartão no navegador (SDK MercadoPago.js ou `POST /v1/card_tokens`).
3. Manda para o nosso backend apenas `tokenCartao` + `bandeiraCartao`.

O token é de uso único: cada tentativa de pagamento exige um token novo.

**O frontend já faz isso** (`static/js/pagamentos.js`): quando o provider não é `fake` e existe `MP_PUBLIC_KEY`,
ele carrega o MercadoPago.js, pede também o **CPF do titular** do cartão e troca os dados por um token direto com o
Mercado Pago. O nosso servidor só recebe `tokenCartao`, `bandeiraCartao` e `parcelas`. Só a **public key** (feita para
ser pública) vai ao navegador: o `MP_ACCESS_TOKEN` e o segredo do webhook ficam somente no servidor. Pix e boleto
são criados inteiramente pelo servidor, sem o navegador falar com o Mercado Pago. Sem `MP_PUBLIC_KEY` a tela
recusa o cartão e oferece só Pix e boleto.

A **bandeira** do cartão (`bandeiraCartao`) também vem do Mercado Pago: o frontend pergunta ao MercadoPago.js qual é o
método dos 8 primeiros dígitos e escolhe o de débito ou o de crédito. A tabela local de bandeiras só vale no modo
`fake` e como reserva quando o Mercado Pago não reconhece o cartão. Se o cartão existe mas não é do tipo escolhido
(débito num cartão só de crédito), a tela avisa antes de gastar o token.

---

## 2. Configuração

Credenciais **nunca** vão para o git. Duas formas de configurar:

**A) arquivo local** — `src/main/resources/application-local.yml` (já está no `.gitignore`),
e suba com o perfil `local`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**B) variáveis de ambiente:**

| Variável | Para que serve |
|---|---|
| `MP_ACCESS_TOKEN` | access token da aplicação (`TEST-…` em sandbox) |
| `MP_PUBLIC_KEY` | public key, devolvida ao frontend |
| `MP_NOTIFICATION_URL` | URL pública do webhook (deixe vazio em localhost) |
| `MP_WEBHOOK_SECRET` | assinatura secreta do webhook |
| `PAGAMENTO_PROVIDER` | `mercadopago` ou `fake` |

> **Em produção (`prod`) o sistema não sobe** com `fake`, nem sem `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` e uma
> `MP_NOTIFICATION_URL` `https://` (veja `docs/PRODUCAO.md`).

> Em `localhost` deixe `MP_NOTIFICATION_URL` vazio: o Mercado Pago valida a URL e **recusa a
> cobrança** se ela não for pública. Use um túnel (`ngrok http 8080`) quando quiser testar o webhook.

---

## 3. Endpoints

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/pagamentos/config` | provider ativo + public key para o frontend |
| `POST` | `/pagamentos` | gera a cobrança do mês |
| `GET` | `/pagamentos/{id}` | detalha o pagamento |
| `GET` | `/pagamentos/contrato/{id}` | lista os pagamentos do contrato |
| `POST` | `/pagamentos/{id}/sincronizar` | reconsulta o status no Mercado Pago |
| `POST` | `/pagamentos/{id}/estornar` | estorna um pagamento confirmado |
| `POST` | `/webhooks/mercadopago` | recebe as notificações (público, valida HMAC) |

### Corpo de `POST /pagamentos`

```jsonc
{
  "contratoId": 1,
  "referenciaMes": "2026-10",     // AAAA-MM
  "metodo": "PIX",                // PIX | BOLETO | CARTAO_CREDITO | CARTAO_DEBITO

  // só para cartão:
  "tokenCartao": "cb51fb7fbc979c65856c7771c2f80adb",
  "bandeiraCartao": "master",     // payment_method_id do Mercado Pago
  "parcelas": 3                   // débito aceita só 1
}
```

`bandeiraCartao` mais usados: crédito `master`, `visa`, `elo`, `amex`, `hipercard`;
débito `debmaster`, `debvisa`, `debelo`. A lista completa vem de
`GET https://api.mercadopago.com/v1/payment_methods`.

### Status

| Nosso status | Status do Mercado Pago |
|---|---|
| `PAGO` | `approved` |
| `PROCESSANDO` | `authorized`, `in_process`, `in_mediation` |
| `PENDENTE` | `pending` (Pix/boleto aguardando pagamento) |
| `RECUSADO` | `rejected`, `cancelled` |
| `ESTORNADO` | `refunded`, `charged_back` |

`statusDetalhe` traz o motivo cru do provedor — é o que explica uma recusa
(`cc_rejected_bad_filled_security_code`, `cc_rejected_insufficient_amount`…).

---

## 4. Como testar

### 4.1 Subir a aplicação

```bash
docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4.2 Montar a base de teste

```bash
API=http://localhost:8080

curl -s -X POST $API/auth/registro -H "Content-Type: application/json" -d '{"nome":"Maria Locadora","email":"locadora@alugafacil.com","cpf":"11144477735","senha":"senha12345","perfil":"LOCADOR"}'
curl -s -X POST $API/auth/registro -H "Content-Type: application/json" -d '{"nome":"Joao da Silva","email":"joao@alugafacil.com","cpf":"19119119100","senha":"senha12345","perfil":"LOCATARIO"}'

TKL=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" -d '{"email":"locadora@alugafacil.com","senha":"senha12345"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
TK=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" -d '{"email":"joao@alugafacil.com","senha":"senha12345"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -s -X POST $API/imoveis -H "Authorization: Bearer $TKL" -H "Content-Type: application/json" \
  -d '{"titulo":"Apto 101","tipo":"APARTAMENTO","logradouro":"Avenida Paulista","numero":"1000","bairro":"Bela Vista","cidade":"Sao Paulo","uf":"SP","cep":"01310100","valorAluguel":1400.00,"valorCondominio":100.00}'

curl -s -X POST $API/contratos -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
  -d '{"imovelId":1,"dataInicio":"2026-09-01","dataFim":"2027-09-01","diaVencimento":10}'
```

> O e-mail do pagador **não pode ser o da conta vendedora** nem um domínio reservado
> (`@testuser.com`), senão o Mercado Pago devolve `Payer email forbidden`.

### 4.3 Pix

```bash
curl -s -X POST $API/pagamentos -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
  -d '{"contratoId":1,"referenciaMes":"2026-10","metodo":"PIX"}'
```

Volta `status: PENDENTE` com `pixCopiaECola`, `pixQrCodeBase64` (PNG em base64) e
`linkPagamento` (página do Pix no sandbox). Pagamento real de Pix não dá para simular
via API; a confirmação chega pelo webhook.

### 4.4 Boleto

```bash
curl -s -X POST $API/pagamentos -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
  -d '{"contratoId":1,"referenciaMes":"2026-11","metodo":"BOLETO"}'
```

Volta `linhaDigitavel` e `linkPagamento` (PDF do boleto).

> O Mercado Pago **recusa boleto com vencimento a mais de 29 dias** da emissão. Se o
> vencimento do contrato passar disso, o boleto sai com a data máxima permitida
> (`boleto-dias-maximos`, padrão 28) — o campo `vencimento` do contrato continua intacto.

### 4.5 Cartão de crédito

Primeiro gere o token (é o que o frontend faz):

```bash
PUBKEY=TEST-sua-public-key-aqui

CTOK=$(curl -s -X POST "https://api.mercadopago.com/v1/card_tokens?public_key=$PUBKEY" \
  -H "Content-Type: application/json" \
  -d '{"card_number":"5031433215406351","expiration_month":11,"expiration_year":2030,"security_code":"123","cardholder":{"name":"APRO","identification":{"type":"CPF","number":"19119119100"}}}' \
  | grep -o '"id":"[a-f0-9]\{32\}"' | head -1 | cut -d'"' -f4)

curl -s -X POST $API/pagamentos -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
  -d "{\"contratoId\":1,\"referenciaMes\":\"2026-12\",\"metodo\":\"CARTAO_CREDITO\",\"tokenCartao\":\"$CTOK\",\"bandeiraCartao\":\"master\",\"parcelas\":3}"
```

Resultado esperado: `status: PAGO`, `statusDetalhe: accredited`.

### 4.6 Cartão de débito

Mesma coisa, com um cartão de débito e sem parcelamento:

```bash
DTOK=$(curl -s -X POST "https://api.mercadopago.com/v1/card_tokens?public_key=$PUBKEY" \
  -H "Content-Type: application/json" \
  -d '{"card_number":"5067766783888311","expiration_month":11,"expiration_year":2030,"security_code":"123","cardholder":{"name":"APRO","identification":{"type":"CPF","number":"19119119100"}}}' \
  | grep -o '"id":"[a-f0-9]\{32\}"' | head -1 | cut -d'"' -f4)

curl -s -X POST $API/pagamentos -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
  -d "{\"contratoId\":1,\"referenciaMes\":\"2027-01\",\"metodo\":\"CARTAO_DEBITO\",\"tokenCartao\":\"$DTOK\",\"bandeiraCartao\":\"debelo\"}"
```

Quando o banco exigir autenticação 3DS, a resposta vem com `status: PENDENTE` e
`urlAutenticacao3ds` — o frontend abre essa URL para o cliente concluir o desafio.

### 4.7 Cartões e nomes de teste

| Bandeira | Número | CVV | Validade |
|---|---|---|---|
| Mastercard crédito | `5031 4332 1540 6351` | 123 | 11/30 |
| Visa crédito | `4235 6477 2802 5682` | 123 | 11/30 |
| Elo débito | `5067 7667 8388 8311` | 123 | 11/30 |

> O Visa de teste é um cartão **internacional**: o Mercado Pago recusa parcelamento nele (`installments_excludes_country`). Para testar crédito parcelado use o Mastercard de teste.

O **nome do titular** controla o resultado no sandbox:

| Nome | Resultado |
|---|---|
| `APRO` | aprovado |
| `OTHE` | recusado por erro geral |
| `FUND` | recusado por saldo insuficiente |
| `SECU` | recusado por CVV inválido |
| `EXPI` | recusado por data de validade |
| `CALL` | recusado, precisa autorizar com o banco |

CPF de teste: `19119119100`.

### 4.8 Consultar, sincronizar e estornar

```bash
curl -s $API/pagamentos/1 -H "Authorization: Bearer $TK"
curl -s -X POST $API/pagamentos/1/sincronizar -H "Authorization: Bearer $TK"
curl -s -X POST $API/pagamentos/1/estornar -H "Authorization: Bearer $TK"
```

`sincronizar` reconsulta o Mercado Pago e atualiza o status local — é a rede de segurança
para quando o webhook não chega (rodando em localhost, por exemplo).

### 4.9 Webhook

Em produção o Mercado Pago chama `POST /webhooks/mercadopago`. Para testar em localhost:

```bash
ngrok http 8080
```

Alternativa **sem criar conta**: `winget install Cloudflare.cloudflared` e depois
`cloudflared tunnel --url http://localhost:8080`. Ele imprime um endereço `https://….trycloudflare.com`; a URL do
webhook é esse endereço mais `/webhooks/mercadopago`. O endereço muda a cada vez que o túnel é reiniciado, então
suba o túnel primeiro, use a URL em `MP_NOTIFICATION_URL` e atualize o painel do Mercado Pago quando ela mudar.

No ambiente de teste o Mercado Pago avisa o webhook assim que um pagamento é criado, com assinatura. Foi assim que
se confirmou (2026-11-02) que a assinatura é aceita e que uma chamada sem assinatura ou com assinatura falsa volta
`401`. O aviso de um pagamento que já está `PAGO` não muda nada e não manda e-mail de novo.

Configure no painel do Mercado Pago (*Suas integrações > Webhooks*) a URL
`https://SEU-TUNEL.ngrok-free.app/webhooks/mercadopago`, evento **Pagamentos**, copie a
assinatura secreta e coloque em `MP_WEBHOOK_SECRET` / `application-local.yml`.

Simulando uma notificação assinada na mão:

```bash
SECRET="sua-assinatura-secreta"
DATAID="1328142232"      # id do pagamento no Mercado Pago
RID="req-abc-123"
TS=$(date +%s)

SIG=$(printf '%s' "id:${DATAID};request-id:${RID};ts:${TS};" \
      | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.*= //')

curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/webhooks/mercadopago \
  -H "Content-Type: application/json" \
  -H "x-signature: ts=${TS},v1=${SIG}" \
  -H "x-request-id: ${RID}" \
  -d "{\"type\":\"payment\",\"data\":{\"id\":\"${DATAID}\"}}"
```

Esperado: `200` com assinatura correta, `401` com assinatura errada ou ausente.
Sem `MP_WEBHOOK_SECRET` configurado a validação é pulada (só para desenvolvimento —
aparece um `WARN` no log). Em `prod` o sistema nem sobe sem ele.

---

## 5. Regras de negócio

- **Uma cobrança por mês de referência.** A tabela tem `UNIQUE (contrato_id, referencia_mes)`.
- **Retentativa reaproveita o registro.** Se o cartão for recusado, uma nova chamada para o
  mesmo mês reusa a mesma linha e limpa os dados da tentativa anterior. Sem isso o locatário
  ficaria travado sem conseguir pagar o mês por causa da restrição única.
- **Meses `PAGO` ou `PROCESSANDO` são bloqueados** com `422`.
- **Só participantes do contrato** (locatário, locador do imóvel ou ADMIN) geram, consultam
  e estornam cobranças.
- **O webhook nunca confia no corpo da notificação:** dele só usamos o id, e o status vem de
  uma consulta à API do Mercado Pago.
- **Pagamento confirmado avisa por e-mail:** quando o status passa para `PAGO` (na hora, no cartão; depois, em Pix e
  boleto), o locatário recebe o comprovante `PG-xxxxxx` e o locador recebe o aviso de recebimento, uma vez só, mesmo
  que o webhook chegue repetido. Falha no e-mail nunca desfaz o pagamento (veja `docs/EMAIL.md`).
- **Falha no gateway derruba a transação** (`502` + rollback), então não sobra pagamento órfão
  no banco.

---

## 6. Erros comuns

| Erro | Causa |
|---|---|
| `Payer email forbidden` | e-mail do pagador é o da conta vendedora ou de domínio reservado |
| `The expiration date can not be greater than 29 days` | vencimento do boleto fora da janela (já tratado pelo clamp) |
| `Invalid users involved` | access token e public key de contas diferentes |
| `installments_excludes_country` | parcelamento em cartão internacional (o Visa de teste é um); use o Mastercard |
| `500 internal_error` sem detalhe, em toda criação de pagamento | problema do lado do Mercado Pago ou da aplicação recém-criada/alterada; passou sozinho horas depois da troca do tipo da integração para Checkout Transparente |
| `Invalid card token` | token já usado (é de uso único) ou expirado |
| `pagamento.mercadopago.access-token nao configurado` | subiu com `provider=mercadopago` sem credencial |

---

## 7. O que ainda falta

- Job agendado para gerar as cobranças do mês automaticamente a partir dos contratos ativos.
- Registrar o histórico de tentativas recusadas em tabela separada (hoje a retentativa
  sobrescreve a tentativa anterior).
- Já testado com credenciais de teste reais (2026-11-02): Pix, boleto, débito, crédito à vista e parcelado, pelo
  servidor **e** pelo formulário do navegador com o MercadoPago.js de verdade (inclusive cartão recusado `OTHE`).
- Testar o webhook de verdade (precisa de URL pública).
