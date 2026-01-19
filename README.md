Code mit 
https://www.tutorialspoint.com/online_java_formatter.htm
sauber formatiert



TicTacToeProject – Mini-Startanleitung (TXT)
===========================================

1) Startreihenfolge (lokal testen)
----------------------------------
1. Server starten:
   - Main-Klasse: server.TicTacToeServer
   - Erwartung in der Konsole: "Server gestartet... Port 8088"

2. Client #1 starten:
   - Main-Klasse: client.Main

3. Client #2 starten:
   - Main-Klasse: client.SecondClientMain
   - Hinweis: nutzt eine eigene Settings-Datei, damit Name/Mode/Timer getrennt bleiben.


2) Lobby (Host/Join + Settings)
-------------------------------
- Auto-Connect:
  Beim Öffnen verbindet sich der Client automatisch mit den zuletzt gespeicherten Settings.

- Manuell verbinden (falls nötig):
  Host / Port / Spielername setzen -> "Verbinden"

- Settings:
  Mode:
   - HUMAN: normales Spiel gegen einen zweiten Client
   - BOT: wenn du hostest, startet sofort ein Match gegen die KI (kein zweiter Client nötig)
  Timer (Sekunden):
   - Zeit pro Zug. Läuft die Zeit ab -> Gegner gewinnt (Timeout).

- Räume aktualisieren:
  Fragt die Room-Liste beim Server neu ab.

- Host:
  Erstellt einen Raum mit dem angegebenen Namen.

- Join:
  Joint den markierten Raum aus der Liste.

- Scoreboard:
  Öffnet die Scoreboard-Ansicht.

- Match-History:
  Öffnet die History-Ansicht (mit Filter).


3) Spielansicht (Game)
----------------------
- Spielfeld:
  3x3 Grid aus Buttons.

- Farben:
  X = Rot, O = Blau.

- Gewinn/Unentschieden:
  Bei Sieg wird eine schwarze Linie über die drei Gewinnfelder gezeichnet.
  Bei Unentschieden wird "D" (Draw) ausgewertet.

- Chat:
  Rechts ein einfacher Chat:
   - Text tippen -> "Senden"
   - Nachricht wird an den Gegner weitergeleitet (bzw. lokal bei BOT).

- Timer:
  Pro Zug läuft der Countdown (timerSec).
  Timeout -> Gegner gewinnt.

- Rematch:
  Nach Spielende:
   - Rematch anbieten
   - Gegner kann annehmen
   - Bei Annahme startet ein neues Spiel und die Seiten werden getauscht (der andere beginnt)
   - Wenn Gegner "Zurück in Lobby" drückt -> Rematch gilt als abgelehnt

- Zurück in Lobby:
  Wechselt zurück zur Lobby.
  Dort wird automatisch wieder mit den gespeicherten Settings connected.


4) Scoreboard
-------------
- Zeigt für Spieler:
  Wins / Losses / Draws
- "Aktualisieren" lädt neu vom Server.


5) Match-History
----------------
- Zeigt vergangene Matches aus match_history.csv
- Filter:
  - Spielername (Teilstring reicht)
  - Datum von/bis (optional)


6) Daten / Dateien (wo was liegt)
---------------------------------
- Client Settings:
  - client_settings.properties (Client #1)
  - client_settings_2.properties (Client #2)

- Scoreboard:
  - scores.csv

- Match-History:
  - match_history.csv

- Saves (laufende Spiele / gespeicherte Züge):
  - Ordner saves/


7) Troubleshooting (kurz)
-------------------------
- Client verbindet nicht:
  - Server läuft? Port korrekt (Standard 8088)?
  - Host "localhost" beim lokalen Test.

- Kein Raum sichtbar:
  - "Räume aktualisieren" drücken
  - Host muss einen Raum erstellen (bei HUMAN)

- BOT startet nicht:
  - Mode in Lobby auf BOT stellen
  - dann hosten (Start passiert sofort)

- Nichts klickbar im Spiel:
  - Nur der Spieler am Zug kann klicken (TURN bestimmt das)

- Rematch klappt nicht:
  - Rematch geht nur nach GAME_OVER
  - Gegner darf nicht schon in die Lobby gegangen sein
