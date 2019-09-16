# Redigere opptak
Etter å ha opprettet opptaksfiler vil man typisk endre på verdier i forespørsel (request) og svar (response)
så man får treff ved fremtidig avspilling av opptak. Dette gjør man på [opptaksfanen](/#/recordings).
For å redigere opptaksfiler må man ha kunnskap om regulære uttrykk, da variable verdier i forespørsler må
erstattes med regulære uttrykk for at Troxy skal finne opptak som passer til forespørsler.

## Endre forespørsel (Request)
En forespørsel består av 8 felter:
`Protocol`, `Host`, `Method`, `Path`, `Port`, `Query`, `Header` og `Content`

I samtlige av disse felter kan man sette inn regulære uttrykk som Troxy i avspillingsmodus vil sammenligne
med innkommende forespørsler for å finne et opptak som passer.

Ved å bruke "group capturing" med de regulære uttrykkene kan man også hente ut verdier fra forespørsler og
sette de inn i svaret som Troxy vil sende tilbake til klienten. Et par eksempler på "group capturing":
* `(.*)` - Finner alle tegn og legger det i en ikke navngitt gruppe.
* `(<ssn>.*)` - Finne alle tegn og legger det i en gruppe med navn "ssn".

Det anbefales å navngi de grupperte regulære uttrykkene som inneholder data man skal bruke i svaret.

Merk at man i editorene for Header og Content-feltene kan trykke på ctrl-space for å få opp en kort liste
med vanlige regulære uttrykk.

## Endre svar (Response)
Et svar består av 3 felter:
`Code`, `Header` og `Content`

Vanligvis vil man ikke endre stort på svar, men i noen tilfeller er det nødvendig å sette inn verdier,
for eksempel verdier man har hentet ut fra forespørselen.

Formatet for å hente ut verdier fra forespørselen er `$<felt>:<indeks/navn>$`. Et par eksempler:
* `$header:1$` - Henter ut verdien fra første gruppe i Header-feltet til innsendt forespørsel.
* `$content:ssn$` - Henter ut verdien for gruppe "ssn" tatt ut fra Content-feltet i forespørselen.

Trykker man ctrl-space i editorene for Header og Content-feltene vil man få opp en liste med grupper
hentet ut fra forespørsel.

### Eksempel på opptaksfil

#### Request
**Protocol:** `https`

**Host:** `.*`

**Method:** `POST`

**Path:** `.*MultiSignOrderService`

**Port:** `443`

**Query:** `.*`

**Header:**
```
.*SOAPAction: "cancelOrder".*
```

**Content:**
```
.*<.*OrderID>(<orderid>.*)</.*OrderID>.*CancelOrder.*
```

#### Response
**Code:** `200`

**Header:**
```
Keep-Alive: timeout=15, max=100
Connection: Keep-Alive
Content-Length: 782
Date: Tue, 21 Jul 2015 07:32:04 GMT
X-Powered-By: Servlet/2.5 JSP/2.1
Content-Type: text/xml; charset=utf-8
```

**Content:**
```
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Header><srv:TrustSignHeader xmlns:srv="http://msos.sign.model.sb1/service/v4" xmlns:tsm="http://www.bbs.no/tt/trustsign/2013/01/tsm#"><tsm:MerchantID>2010</tsm:MerchantID><tsm:Time>2015-07-21T07:32:05+00:00</tsm:Time><tsm:MessageID>9011543227333916385--28efc971.14eab9b994d.-5a2b</tsm:MessageID></srv:TrustSignHeader></soapenv:Header><soapenv:Body><tsm:CancelOrderResponse xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:tsm="http://www.bbs.no/tt/trustsign/2013/01/tsm#"><tsm:OrderID>$content:orderid$</tsm:OrderID><tsm:TransRef>7284141330864384853</tsm:TransRef></tsm:CancelOrderResponse></soapenv:Body></soapenv:Envelope>
```