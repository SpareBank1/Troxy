# Spille av opptak
Når man har laget og redigert opptak er de klare til avspilling, men først må man sette Troxy
i riktig modus og aktivere de aktuelle opptakene.

## Oppsett Troxy
Gå til [statusfanen](/#/status) og sett Troxy modus til et av de følgende alternativer:

* **Avspilling**

  Troxy vil forsøke å finne et aktivert opptak som samsvarer med forespørsel og returnere dette.
  Om ingen aktiverte opptak samsvarer med forespørsel returneres HTTP kode 418.

* **Avspilling eller videresending**

  I stedet for å returnere 418 ved manglende opptak vil Troxy videresende forespørsel til baksystem.

## Aktivere opptak
Det er nødvendig å aktivere de opptakene man ønsker at Troxy skal sjekke forespørsel mot for at
Troxy skal returnere svar til klienten.
Dette gjør man ved å trykke på "Aktiver" i [opptaksfanen](/#/recordings).

## Oppsett klient
I likhet med oppretting av opptak må klienter settes til å gå mot Troxy ved avspilling av opptak.

```
# Vanlig konfigurasjon
externalServiceUrl=http://www.example.com/getCustomer

# Gjennom Troxy
externalServiceUrl=http://${troxy.url}/http://www.example.com/getCustomer
```