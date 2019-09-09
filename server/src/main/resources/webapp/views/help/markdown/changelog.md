# Endringslogg

## Troxy 3.0
* Nytt filformat for Troxy-opptak (.troxy). Eksisterende .xml-opptak konverteres til nytt opptak ved aktivering eller redigering av opptak i brukergrensesnitt.
  Obs: Dersom Troxy ikke har skrivetilgang til xml-filer vil ikke disse bli slettet når ny .troxy-fil blir opprettet.
  Merk at oppgradering av Troxy-instans som har aktiverte opptak vil automatisk konvertere de aktiverte opptakene under oppgraderingen.
  Opptaksformatet ble endret fra XML til eget format da opptak med XML-data var vanskelig å redigere i en egen editor.
* Dersom flere opptak matcher en request vil Troxy nå returnere 418 i stedet for et av de matchende opptakene som tidligere.
  Denne oppførsel kan overstyres ved å sette `troxy.allow_multiple_matching_recordings=true` i `troxy.properties`.
* Opptak kan nå lastes opp/ned i brukergrensesnittet.
* Man kan nå utelate protokoll, domene og port ved avspilling av opptak, gitt at disse feltene er satt til ".*". Eksempel: `${troxy.url}/Customer/42`.
* Respons-forsinkelse er flyttet fra opptak som helhet til hver enkelt respons i et opptak (tillater forskjellige forsinkelser for forskjellige opptak).
* Det er nå mulig å spesifisere en vekting av hvor ofte et opptak skal returneres i stedet for round-robin retur i tidligere Troxy-versjoner.
* Ved aktivering/deaktivering av opptak vises antall av de valgte filene som er aktivert.
* RPM-pakke bygges nå i byggescript i stedet for egen byggeserver.
* I opptaksmodus vil responser fra requester som matcher tidligere opptak legges til opptaket i stedet for at det opprettes nytt opptak.

## Troxy 2.1
* Ctrl-S ved redigering av opptak vil lagre opptaket.
* Tab vil flytte markør fra header til content i stedet for å sette inn tab-tegn (kan slåes av).
* Dokumentasjon er flyttet fra Confluence og inn i Troxy.
* Det er nå mulig å se statistikk for inneværende periode i brukergrensesnittet.
* Troxy vil ikke lenger opprette statistikkfiler dersom det ikke har vært noen aktivitet.
* Knappene Copy, Rename og opprett mappe vil ikke lenger markere lastet opptak som endret.
* Oppsett av HTTP/HTTPS etter installasjon er tilpasset bruk hos SB1.
* Rettet feil der sletting av respons ikke oppdaterte brukergrensesnittet til å vise en annen respons/request.
* Lagt til ny modus: `PLAYBACK_OR_PASSTHROUGH`.
* Rettet at opptak ble lagret med system-tegnsett fremfor UTF-8.
* Troxy skal nå håndtere at linjeskift varierer mellom CRLF og LF.
* Forbedret filter `AutoRegexNewRecording`.
* Implementert mulighet til å inkludere eksterne filer i troxy.properties med `@include <file>`.
* Eget ikon for opptak som både er aktivert og redigeres.
* Avinstallasjon av Troxy 2.1 vil ikke slette filen med aktiverte opptak.
* Rettet feil der `NULL` ble logget som filnavn på opptak ved avspilling.
* Filtreet i opptaksfanen skal ikke bli oppfrisket når man lagrer opptak.
* Lagt inn nytt filter `DelayResponse` som erstatter gamle filter `ExponentialDelay`, `FixedDelay`, `GaussianDelay` og `RandomDelay`.
* Endringslogg er nå tilgjengelig i Troxy.

## Troxy 2.0.1
* Sletting av aktivert opptak fjernet ikke opptaket fra minnet og forårsaket uforutsigbar oppførsel.
* Endret standard loggnivå fra `DEBUG` til `INFO`.
* Rettet feil der nylig opprettede opptak ikke viste tidsforsinkelsesverdier og ga HTTP 500 feil ved avspilling om opptakene først ikke ble lagret og deretter lastet inn igjen.
* Det var mulig å sette inn ugyldige verdier for tidsforsinkelse, noe som medførte at opptaksfilen ble korrumpert.

## Troxy 2.0
* Erstattet Spring-basert webklient med Single Page App-løsning.

  Dette innebærer også at Troxy ikke lenger består av to applikasjoner, men én.

* Applikasjonen bruker nå kun to porter, en for HTTP og en for HTTPS. Ned fra de tidligere fire portene som ble brukt i Troxy 1.x.
* Byttet bibliotek for å redigere opptaksfiler fra CodeMirror til Ace.
* Byttet bibliotek for trestruktur med opptaksfiler. Trestrukturen er nå enklere og mer intuitiv å bruke.
* Det er nå mulig å redigere opptaksfiler med mer enn én respons, samt legge til flere responser i eksisterende opptaksfiler.
* Rettet feil i filter `AutoRegexNewRecording` der den kunne legge inn regex som ikke matchet opprinnelig tekst.
* Forbedret respons til bruker om at aktivt opptak som endres og lagres faktisk blir lastet på nytt.
* Rettet feil der man ikke kunne slette katalog med filer.
* Kan nå flytte alle markerte filer/kataloger til annen katalog.

## Troxy 1.7
* Oppgradert Troxy til å bruke Java 8.
* Rettet feil der endring av aktivert opptak ikke ble oppdatert i Troxy.
* Filnavn til opptak skrives ut når Troxy returnerer et svar til klient.
* Endret navn på filter `ModifyRequestHeaders` til `ModifyHeadersNewRecording` og `ModifyXmlRequest` til `ModifyXmlNewRecording`.
* Opprettet nytt filter som erstatter vanlig innhold med regulære uttrykk, filteret er på som standardinnstilling.
* Rettet feil der opptak ikke ble markert som endret når man kun slettet innhold i opptaksfilen.
* Troxy bruker nå mindre minne, store opptaksfiler lagres komprimert i minnet.
* Statistikk er nå tilgjengelig i webklienten.

## Troxy 1.6
* Redesign av brukergrensesnittet.
* Når man drar en opptaksfil til toppen eller bunn av opptakstreet vil treet scrolles opp/ned.
* Rettet en feil der korte loggfiler ga feil i brukergrensesnittet.
* Rettet flere grafiske feil i opptakstreet.
* Forbedret visning av feil i brukergrensesnittet.
* Når man endrer responsen til en opptaksfil vises nå en variabelliste når man skriver `$`.
* Det er lagt inn visning av originalopptak for nyere opptak som har originalopptaket tilgjengelig.
* Dersom man ikke får lagret et opptak og går til en ny fane skal man nå få en feilmelding.
* Det er lagt til en knapp for å oppfriske opptakslisten.

## Troxy 1.5
* Det er nå mulig å spesifisere svartid i opptaksfilen, fremfor å bruke et eget filter.
* To filter er implementert for å lette arbeidet med å ta opptak, `ModifyRequestHeaders` og `ModifyXmlRequest`.
* Ved installasjon åpnes automatisk porter som Troxy bruker.
* Man kan nå bruke "named groups" i regulære uttrykk og hente ut verdien i responsen ved å oppgi navnet man ga til det regulær uttrykket.
* Ved nye opptak så lagrer Troxy den opprinnelige dataen som ble sendt til baksystemet og svaret som kom fra baksystemet.
* Troxy husker nå hvilke opptak som er lastet, ved start vil disse opptakene automatisk bli lastet.
* Rettet en feil der Troxy ville slette konfigurasjonen hvis disken var full og man forsøkte å endre konfigurasjonen.
* Flere forbedringer i brukergrensesnittet for å laste inn/ut opptak, redigere opptak, osv:

  Man får tilbakemelding når man redigerer feltene om regulære uttrykk stemmer overens med det originale opptaket.

  For `header` og `content`-feltene får man tilbakemelding på hvor lang tid det tok å kjøre regulært uttrykk mot originalt opptak.

  Man kan markere filer/kataloger og trykke på meny-knappene.

  Dobbeltklikking på fil flytter den over til redigeringsvinduet.

  Hvis man går bort fra Opptak-fanen og tilbake igjen, så huskes det hvilke kataloger som var ekspandert.

## Troxy 1.4
* Både Troxy server og Troxy webklient kjører nå som egen bruker i stedet for root.
* Logging er nå delt opp i to filer, `server` og `access`.

  Access-loggen tar for seg forespørsler til og svar fra Troxy, og det er denne brukere typisk vil forholde seg til for å feilsøke når Troxy ikke gir det svaret man forventer.

  Server-loggen vil vise eventuelle feil i Troxy, og er mest ment for driftere.

  Det er fjernet to loggnivåer, dette er `NOTICE` og `TRACE`. Det er også lagt til 2 nye loggnivåer, `ACCESS` og `TRAIL`.

  De to nye loggnivåene logger kun til access-loggen, der `ACCESS` er mer generell logging og `TRAIL` er detaljert logging.
* Rettet en feil der det var mulig å lagre opptaksfiler et annet sted enn i opptakskatalogen.
* Det er implementert innsamling av statistikk.
* Rettet feil der `content` ikke ble lagret i svar som returnerte statuskode annet enn 200.
* Opptaksvinduet i webklienten er restrukturert. Vindu for lastede opptak er fjernet, i stedet vises lastede filer i trestrukturen over
  tilgjengelige opptak.
* Redigeringsvindu for opptak er delt opp i flere felter som forenkler redigering av opptaksfiler.
* Troxy er oppgradert til å bruke Java 7.
* Nye lys i webklient for å vise statusene `PLAYBACK_OR_RECORD` og `PASSTHROUGH`.

## Troxy 1.3
* Det er innført et system for å automatisk lage leveransepakke.
* Man kan nå spesifisere hvilket alias/key i en keystore man skal bruke, ikke lenger nødvendig at keystore kun har ett alias hvor passord er
  det samme som for selve keystore.
* Jetty oppgradert til 8.1.8.
* Mer informasjon listes på statussiden til webklienten.
* Sesjoner fra de siste 24 timene vises på statussiden.
* Begrenset antall logglinjer som vises i webklienten for å forhindre at nettleser går tom for minne.
* Midlertidig datakatalog for webklient er flyttet fra /tmp til /opt/troxy_webclient/application for å forhindre at den slettes og brekker
  webklienten.
* Redigeringsvinduet i webklienten skal nå justere størrelsen automatisk.
* Fullt filnavn vil vises i webklienten for opptaktsfiler når man holder musa over opptaket.
* Rettet feil i Troxy der serveren kunne gå tom for minne ved `DEBUG`-logging av store opptak.
* Kommandoen `clear` er fjernet da `unload` uten parameter gjør samme nytte.
* Ny kommando `reload` laster innlastede opptak på nytt.
* Webklienten logger nå mindre og roterer loggfiler.
* Oppdatert bibliotek for fargekoding av opptaksfiler som skal ha løst noen av problemene med å redigere opptaksfiler.
* Troxy vil nå logge hvilken fil responsen man returnerer kommer fra.

## Troxy 1.2
* Webklient er implementert for å forenkle redigering av opptak og konfigurering av Troxy.
* Kommandoen `reload` har endret navn til `reconfigure`.
* Det er lagt til en `firewall.conf` som spesifiserer hvilke porter som må være åpne for at Troxy skal fungere.
* Ved oppstart vil brukeren få mer tilbakemelding på at Troxy ble startet.
* Ytelse på logging er signifikant forbedret.
* Potensiell feil ved lasting av opptak samtidig som det er avspilling er rettet.
* Forbedret feilmelding når keystore ikke kan leses.
* Opptak lagres nå automatisk, `save`-kommandoen er fjernet.
* Det er lagt til en `unload`-kommando for å fjerne lastede opptak fra Troxy.
* Det er nå mulig å laste opptaksfiler (og ikke bare kataloger med opptaksfiler) med kommandoen `load`.

## Troxy 1.1
* Opptaksformatet er endret. Opptak fra Troxy 1.0 vil bli automatisk konvertert til det nye formatet når de lastes, men brukeren må selv
lagre opptakene på nytt etter at det er lastet for at de skal blir oppgradert permanent. Etter planen vil muligheten for å oppgradere opptak
fjernes i Troxy 1.2 (eller 2.0 om denne kommer først), så eksisterende opptak bør oppgraderes så fort som mulig.
* Troxy vil nå huske filnavnet på lastede opptak og overskrive filen dersom opptak lagres etter at de er lastet.
* Konfigurasjonsverdier som blir fjernet fra `troxy.properties` skal nå også bli gjenspeilet i Troxy når konfigurasjonen lastes på nytt.
* Navn på metoder i filter er endret:

  `filterRequest()` -> `filterClientRequest()`

  `filterResponse()` -> `filterClientResponse()`

  `filterRemoteRequest()` -> `filterServerRequest()`

  `filterRemoteResponse()` -> `filterServerResponse()`

* Lagt opp til at man kan ha flere grupper med opptak på en Troxy-instans ved at man kan gå mot
`http://<troxy URL>/<gruppe>/<URL til baksystem>` i tillegg til `http://<troxy URL>/<URL til baksystem>`.

  Foreløpig har grupper ingen effekt, denne endringen er gjort for fremtidig funksjonalitet.
* Forsinkelsesfilter er forbedret, tar nå høyde for tiden Troxy har brukt på å håndtere forespørselen.
* Forenklet Troxy til kun å være designet for kall over HTTP.
* Forbedret tilbakemeldinger i konsoll ved bruk av Troxy.

## Troxy 1.0
* Første versjon av Troxy. Den var kun brukt internt og ingen leveranse ble laget for denne versjonen.