# Bruke filter
Filter er en utvidelse av funksjonaliteten til Troxy, og de kan modifisere en forespørsel (Request),
svar (Response) eller et opptak under opptak og/eller avspilling.

## Filterfaser
Det er fem faser hvor filter kan påvirke en forespørsel, svar eller opptak, og de aktiveres i denne rekkefølgen:
1. Ved mottatt forespørsel fra klient, før Troxy leter etter samsvarende opptak.
2. Før forespørsel videresendes til baksystem.
3. Ved mottatt svar fra baksystem.
4. Ved oppretting av nytt opptak (gjelder ikke for opptak man oppretter gjennom brukergrensesnittet).
5. Før svar sendes til klient.

Dersom man ikke kommuniserer med et baksystem vil ikke fase 2, 3 og 4 aktiveres.
Se også flydiagram under fanen "Om Troxy".

## Konfigurasjon av filter
All konfigurasjon av filter gjøres i konfigurasjonsfilen som man finner i [oppsetsfanen](/#/configuration).

Alle filter har samme oppsett for aktivering av filter og gruppering av forespørsler.

### Aktivere filter
Et filter vil ikke kjøre med mindre det først er aktivert:

```filter.<filter>.enabled=true```

### Global begrensning av forespørsler/svar
Dersom man ønsker at et filter kun skal kjøres for gitte forespørsler/svar er det mulig å sette opp
global begrensning med regulære uttrykk.
For hvert av de åtte feltene i en forespørsel kan man sette opp ett eller flere regulære uttrykk,
hvert regulære uttrykk må navngis med en unik id:
* `filter.<filter>.request.protocol.<id> = <regex>`
* `filter.<filter>.request.host.<id> = <regex>`
* `filter.<filter>.request.port.<id> = <regex>`
* `filter.<filter>.request.path.<id> = <regex>`
* `filter.<filter>.request.query.<id> = <regex>`
* `filter.<filter>.request.method.<id> = <regex>`
* `filter.<filter>.request.header.<id> = <regex>`
* `filter.<filter>.request.content.<id> = <regex>`

Det samme gjelder for de tre feltene i et svar:
* `filter.<filter>.response.code.<id> = <regex>`
* `filter.<filter>.response.header.<id> = <regex>`
* `filter.<filter>.response.content.<id> = <regex>`

Dersom man setter opp flere regulære uttrykk for et felt vil dette fungere som en logisk OR-operasjon.
Hvis man ikke setter opp noen global begrensning vil filteret kjøre for samtlige forespørsler/svar.

### Knytte forespørsel/svar til gruppe
Hvis man ønsker spesifikk filterkonfigurasjon for gitte forespørsler/svar er det mulig å skille ut disse
forespørslene/svarene med regulære uttrykk til en gruppe.
Her gjelder de samme reglene som for den globale begrensningen, så for forespørsel:
* `filter.<filter>.<group>.request.protocol.<id> = <regex>`
* `filter.<filter>.<group>.request.host.<id> = <regex>`
* `filter.<filter>.<group>.request.port.<id> = <regex>`
* `filter.<filter>.<group>.request.path.<id> = <regex>`
* `filter.<filter>.<group>.request.query.<id> = <regex>`
* `filter.<filter>.<group>.request.method.<id> = <regex>`
* `filter.<filter>.<group>.request.header.<id> = <regex>`
* `filter.<filter>.<group>.request.content.<id> = <regex>`

Tilsvarende for svar:
* `filter.<filter>.<group>.response.code.<id> = <regex>`
* `filter.<filter>.<group>.response.header.<id> = <regex>`
* `filter.<filter>.<group>.response.content.<id> = <regex>`

### Filterspesifikk konfigurasjon
Hvert filter har sin egen unike konfigurasjon. Konfigurasjon kan gjøres på globalt nivå:

`filter.<filter>.config.<key>=<value>`

Hvis man har opprettet grupper som beskrevet over er det også mulig å sette gruppespesifikk konfigurasjon:

`filter.<filter>.<group>.config.<key>=<value>`

## Filter som følger med Troxy
Det følger med noen filter i Troxy som forenkler prosessen med å opprette nye opptak.
Se også konfigurasjon i [oppsetsfanen](/#/configuration) for eksempler på bruk av filtrene.

### AutoRegexNewRecording
Dette filteret søker etter tekst i nye opptak som samsvarer med angitt regulært uttrykk,
og erstatter så teksten med det angitte regulære uttrykket, eller en annen spesifisert verdi.

### KeepRegexNewRecording
Dette filteret søker etter tekst i nye opptak som samsvarer med angitte regulære uttrykk,
og beholder tekstene, men erstatter annet innhold med regulært uttrykk `.*`.

### DelayResponse
Et filter som setter inn tidsforsinkelse på opptak.

### KeyBasedReplace
Søker etter nøkkel i forespørsel, finner verdi for nøkkel i en mapping-fil og setter verdien inn i svaret.

### SetRecordingDelay
Setter tidsforsinkelse på nye opptak basert på tid det tok å lage opptaket.
