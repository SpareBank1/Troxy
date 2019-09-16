# Om Troxy
Troxy er et system for å simulere kall til og svar fra fag-/baksystemer når kommunikasjonsprotokollen er HTTP(S).

Ved å sette URL til Troxy foran URL til fagsystemet vil Troxy i opptaksmodus fange opp Request/Response til/fra
fagsystemet, som man så senere i avspillingsmodus kan bruke for å simulere fagsystemet.

For å ta i bruk Troxy må man ha kunnskap om regulære uttrykk da dette er en essensiell del av Troxy.
Regulære uttrykk brukes for å erstatte verdier som endres fra kall til kall, som for eksempel tidsstempel.


## Flytdiagram for Troxy
![Troxy-flyt](/views/help/images/troxy_flow.png)
