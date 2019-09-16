# Lage opptak
Det er to måter man typisk vil lage opptak på. Enten gjennom en klient eller manuell oppretting.

## Lage opptak gjennom klient
Den mest vanlige måten å opprette opptak i Troxy er å sende forespørsler fra klient, gjennom Troxy og til ekstern tjeneste.

### Oppsett Troxy
Gå til [statusfanen](/#/status) og sett Troxy modus til et av de følgende alternativer:

* **Opptak**

  Troxy vil opprette nye opptaksfiler for forespørsler som ikke matcher et eksisterende opptak.
  Dersom forespørsel matcher et eksisterende opptak vil en ny respons bli lagt til eksisterende opptak,
  eller om svar fra server er identisk til siste svar i opptak vil responsvekten for denne økes med 1.

* **Avspilling eller opptak**

  Troxy vil forsøke å finne et eksisterende opptak som samsvarer med forespørsel før det lages et nytt opptak.

Dersom man har tilnærmet identiske forespørsler som inneholder dato/tid eller andre varierende verdier,
så vil Troxy i utgangspunktet anse forespørslene som unike og opprette nye opptak for hver forespørsel.
Standardfilter "AutoRegexNewRecording" hjelper til noe ved å automatisk sette inn regulære uttrykk,
men ytterligere konfigurasjon av dette filteret er for avansert bruk av Troxy og dekkes ikke her.

### Oppsett klient
For å sende en forespørsel gjennom Troxy må klienten legge addressen til Troxy før addressen til det eksterne systemet.

#### Eksempel på endring av oppsett

```
# Vanlig konfigurasjon
externalServiceUrl=http://www.example.com/getCustomer

# Gjennom Troxy
externalServiceUrl=http://${troxy.url}/http://www.example.com/getCustomer
```

## Lage opptak manuelt
I de tilfeller man ikke har mulighet til å lage opptak ved å sende forespørsler fra klienten kan man bruke denne metoden,
men det er anbefalt å ikke starte med denne metoden om man ikke er kjent med Troxy fra før av.

### Opprette ny opptaksfil
1. Gå til "Opptak"-fanen.
2. Trykk på ikon for å lage en ny mappe/fil.
3. Skriv inn navn på fil, det må ende med ".troxy" for å bli gjenkjent som en opptaksfil.

Etter dette vil man få en tom opptaksfil.
Her må man manuelt fylle inn både forespørsel (request) og svar (response).
Redigering av opptaksfiler er forklart i egen fane.

Merk at opptak opprettet på denne måten vil ikke ha validering av regulære uttrykk da det ikke eksisterer et originalopptak.
