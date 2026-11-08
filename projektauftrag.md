# Projektauftrag: Visual DOM Inspector für JetBrains IDEs

## 1. Projektidee und Zielsetzung

Entwickelt werden soll ein Open-Source-Plugin für JetBrains-IDEs, insbesondere **WebStorm und PhpStorm**, das eine bidirektionale Navigation zwischen einer gerenderten Webseite und dem zugehörigen Quellcode ermöglicht.

Die grundlegende Idee orientiert sich an Adobe Dreamweavers *Live View / Inspect Mode*.

Ein Entwickler soll ein beliebiges HTML-Element in einer Live-Vorschau anklicken können. Die IDE soll daraufhin automatisch zur entsprechenden Stelle im Quellcode navigieren und das zugehörige Element markieren.

Umgekehrt soll es möglich sein, ein Element im Quellcode auszuwählen und dieses in der gerenderten Webseite hervorzuheben.

Das Ziel ist eine moderne, performante und möglichst frameworkübergreifende Lösung, die sich nahtlos in die JetBrains-Entwicklungsumgebung integriert.

Das Plugin soll sich wie eine native IDE-Funktion anfühlen und kein eigenständiger visueller Website-Editor werden.

## 2. Zentrale Funktionen

### 2.1 Visual-to-Code-Navigation

Die wichtigste Kernfunktion:

1. Der Entwickler öffnet eine Webseite in einer integrierten Live-Vorschau.
2. Er aktiviert einen Inspector-Modus.
3. Beim Bewegen der Maus über die Webseite wird das darunterliegende DOM-Element hervorgehoben.
4. Ein Klick auf das Element identifiziert seine Herkunft im Quellcode.
5. Die IDE öffnet automatisch die entsprechende Datei, falls sie noch nicht geöffnet ist.
6. Der Editor scrollt zur relevanten Codeposition und markiert das Element.

Anforderungen:

- Präzise Zuordnung von DOM-Elementen zu Quelldateien.
- Unterstützung verschachtelter HTML-Elemente.
- Funktioniert auch bei tief verschachtelten Komponenten.
- Möglichst geringe Latenz bei der Navigation.
- Kein manuelles Durchsuchen der Quelldateien.
- Verständliche Rückmeldung, falls eine Zuordnung nicht möglich ist.
- Keine willkürliche Navigation zu möglicherweise falschen Quellcodepositionen.

### 2.2 Code-to-Visual-Navigation

Die Navigation soll ebenfalls in die andere Richtung funktionieren.

Wenn der Entwickler im Editor ein HTML-Element beziehungsweise ein entsprechendes Template-Element auswählt, soll die Vorschau:

- Das zugehörige gerenderte Element hervorheben.
- Falls notwendig automatisch zum Element scrollen.
- Optional mehrere Instanzen markieren, wenn eine Komponente mehrfach gerendert wird.
- Zwischen möglichen Instanzen navigieren können.
- Auf Änderungen am Quellcode reagieren.

Eine automatische Synchronisierung bei jeder Cursorbewegung sollte konfigurierbar sein, damit keine unnötigen Aktualisierungen oder Performanceprobleme entstehen.

### 2.3 Integrierte Live-Vorschau

Die Vorschau soll möglichst direkt in die JetBrains-Oberfläche integriert werden.

Gewünschte Darstellungsformen:

- Geteilte Ansicht: Quellcode links, Live-Vorschau rechts.
- Umgekehrte Anordnung.
- Vorschau als separates Tool Window.
- Optional eigenständiger Editor-Tab.

Technische Grundlage könnte JCEF (JetBrains Chromium Embedded Framework) sein.

Die Vorschau soll unter anderem unterstützen:

- HTML, CSS und JavaScript.
- Lokale Entwicklungsserver.
- Dynamisch gerenderte Webanwendungen.
- Hot Module Replacement, sofern vom Entwicklungsserver bereitgestellt.
- Bestehende Authentifizierung und Cookies innerhalb der Browser-Sitzung.
- Navigation zwischen Seiten und Routen.
- Einstellbare Viewport-Größe.
- Responsive Layouts.

Die Integration soll möglichst wenig eigene Infrastruktur erfordern.

### 2.4 Inspector-Modus

Der Inspector muss explizit aktiviert und deaktiviert werden können.

Im aktivierten Zustand:

- Werden Elemente unter dem Mauszeiger hervorgehoben.
- Zeigt ein Tooltip grundlegende Informationen zum Element.
- Wird die normale Klickaktion der Webseite abgefangen.
- Führt ein Klick zur entsprechenden Quelldatei.
- Soll die Hervorhebung auch bei verschachtelten Elementen zuverlässig funktionieren.

Mögliche Tooltip-Informationen:

- HTML-Tag.
- ID.
- CSS-Klassen.
- Zugehörige Komponente, sofern ermittelbar.
- Quelldatei und Zeilennummer, sofern bekannt.

Die visuelle Darstellung sollte sich an modernen Browser-DevTools orientieren, aber möglichst unaufdringlich bleiben.

## 3. Framework-Unterstützung

Das Plugin soll langfristig möglichst universell funktionieren.

### Priorität 1: Statisches HTML

- `.html` und `.htm`.
- Direkte DOM-zu-Quellcode-Zuordnung.
- Möglichst ohne zusätzliche Build-Tools.
- Grundlage für einen funktionsfähigen MVP.

### Priorität 2: Moderne Frontend-Frameworks

- React mit JSX und TSX.
- Vue mit Single File Components.
- Svelte und SvelteKit.
- Mit Vite betriebene Anwendungen.

Dabei sollen existierende Source-Location-Metadaten, Source Maps und Framework-Inspector-Technologien untersucht werden.

Insbesondere sollten bestehende Inspector-Ansätze von Svelte, Vue und Vite analysiert werden, bevor eigene Lösungen implementiert werden.

### Priorität 3: Serverseitige Templates

- PHP.
- Twig.
- Blade.
- Weitere verbreitete Template-Systeme, soweit technisch sinnvoll.

Bei serverseitigem Rendering ist zu untersuchen, wie sich gerenderte HTML-Elemente zuverlässig zu ihren Templates zurückverfolgen lassen.

Falls hierfür eine serverseitige Instrumentierung erforderlich ist, soll diese möglichst optional und ohne Veränderungen am produktiven Anwendungscode realisiert werden.

## 4. Technische Architektur

Die Architektur soll modular, erweiterbar und langfristig wartbar sein.

### JetBrains-Integration

Bevorzugte Technologien:

- Kotlin.
- IntelliJ Platform SDK.
- Gradle mit IntelliJ Platform Gradle Plugin.
- JCEF für die integrierte Browser-Vorschau.
- PSI für strukturierte Quellcodeanalyse.
- Native Editor- und Navigations-APIs.

Bitte prüfe die jeweils aktuellen offiziellen JetBrains-APIs und Kompatibilitätsanforderungen, bevor konkrete Implementierungsentscheidungen getroffen werden.

Das Plugin soll nicht auf private oder instabile IDE-APIs angewiesen sein, sofern sich dies vermeiden lässt.

### JavaScript-Bridge

Die Kommunikation zwischen Browser und IDE soll über eine klar definierte Bridge erfolgen.

Denkbare Informationen:

- DOM-Element-Identität.
- CSS-Selektor beziehungsweise DOM-Pfad.
- Framework-Komponentenidentität.
- Quelldatei.
- Zeile und Spalte.
- Source-Range.
- Informationen zu mehrfach gerenderten Instanzen.

Hierbei muss berücksichtigt werden, dass ein CSS-Selektor allein keine zuverlässige Quellcodezuordnung ermöglicht.

### Framework-Adapter

Für unterschiedliche Rendering-Technologien soll ein Adapterkonzept untersucht werden.

Beispielsweise:

- HTML-Adapter.
- React-Adapter.
- Vue-Adapter.
- Svelte-Adapter.
- PHP-/Template-Adapter.

Ein Adapter soll nach Möglichkeit nur die technologiespezifische Quellcodezuordnung übernehmen.

Browserkommunikation, UI, Editor-Navigation und Elementhervorhebung sollen gemeinsam genutzt werden.

### Sicherheit

Da die Vorschau Webseiten und JavaScript ausführt, muss die Kommunikation zwischen Browser und IDE abgesichert werden.

Insbesondere:

- Keine unkontrollierte Ausführung von Betriebssystembefehlen durch Webseiten.
- Keine willkürlichen Dateizugriffe über die JavaScript-Bridge.
- Strikte Validierung von Nachrichten und Dateipfaden.
- Berücksichtigung nicht vertrauenswürdiger Webseiten.
- Beschränkung privilegierter Funktionen auf vertrauenswürdige Projektkontexte.
- Keine unbeabsichtigte Übertragung von Quellcode an externe Dienste.

## 5. Benutzeroberfläche und Bedienung

Die Bedienung soll möglichst einfach sein.

Gewünschte UI-Elemente:

- Toolbar-Schaltfläche zum Aktivieren des Inspectors.
- URL-Eingabefeld für lokale Entwicklungsserver.
- Vorwärts-/Zurück-Navigation.
- Reload-Schaltfläche.
- Umschaltung zwischen Vorschau und Inspector.
- Auswahl unterschiedlicher Viewport-Größen.
- Konfigurierbare automatische Synchronisierung.

Optional:

- Tastenkürzel für den Inspector.
- Kontextmenüeintrag „In Live Preview anzeigen“.
- Kontextmenüeintrag „Zum Quellcode navigieren“.
- Anzeige der aktuellen Komponenten-Hierarchie.
- Unterstützung mehrerer gleichzeitig geöffneter Vorschauen.

Die Oberfläche soll sich an den JetBrains-New-UI-Konventionen orientieren.

## 6. Performance und Zuverlässigkeit

Besondere Anforderungen:

- Keine spürbare Beeinträchtigung der IDE-Performance.
- Keine permanenten vollständigen DOM-Scans.
- Keine unnötige Neuinitialisierung der Browser-Instanz.
- Kein Blockieren des IDE-UI-Threads.
- Sauberes Ressourcenmanagement beim Schließen der Vorschau.
- Unterstützung großer Projekte.
- Sinnvolle Zwischenspeicherung von Source-Mappings.
- Fehlerbehandlung bei HMR, DOM-Neurendering und Navigation.

Der Inspector soll möglichst unmittelbar auf Mausbewegungen reagieren.

## 7. Nichtziele

Folgende Funktionen sind zunächst ausdrücklich nicht vorgesehen:

- Visuelles Bearbeiten von Webseiten per Drag-and-drop.
- Generierung von HTML durch WYSIWYG-Operationen.
- Ersatz für Browser-DevTools.
- Eigenständiger Website-Builder.
- Vollständiger visueller CSS-Editor.
- Cloud-Backend oder Benutzerkonten.
- Abhängigkeit von proprietären KI-Diensten.

Der Schwerpunkt liegt ausschließlich auf der zuverlässigen Navigation und Synchronisierung zwischen Browser und Quellcode.

## 8. Entwicklungsstrategie

Bitte strukturiere die Entwicklung in mehrere eigenständig testbare Phasen.

**Phase 1 – Architektur und technische Machbarkeit**

- Aktuelle JetBrains-Plugin-Schnittstellen prüfen.
- Bereits vorhandene IDE-Funktionen und Plugins recherchieren.
- Vorhandene Open-Source-Inspector-Technologien untersuchen.
- Möglichkeiten und Grenzen der Source-to-DOM-Zuordnung bewerten.
- Sicherheitsmodell definieren.
- Architektur und Schnittstellen festlegen.

**Phase 2 – Minimal Viable Product**

- Grundlegendes JetBrains-Plugin erstellen.
- Integrierte JCEF-Vorschau implementieren.
- Statisches HTML laden.
- DOM-Hover-Highlighting.
- Klickbasierte Navigation in den HTML-Quellcode.
- Einfache Gegenrichtung vom Editor zur Vorschau.

**Phase 3 – Framework-Integration**

- Adapterarchitektur implementieren.
- React, Vue und Svelte schrittweise integrieren.
- Entwicklungsserver und HMR berücksichtigen.
- Mehrdeutige DOM-Zuordnungen behandeln.

**Phase 4 – Erweiterte Integration**

- PHP- und Template-Unterstützung evaluieren.
- UI und Entwicklererfahrung verbessern.
- Performance optimieren.
- Kompatibilität über verschiedene IDE-Versionen testen.

**Phase 5 – Veröffentlichung**

- Automatisierte Tests.
- Plugin-Verifikation.
- CI/CD.
- Dokumentation.
- Vorbereitung einer Veröffentlichung im JetBrains Marketplace.

## 9. Qualitätssicherung

Das Projekt soll von Beginn an automatisiert testbar sein.

Gewünscht sind:

- Unit-Tests für Mapping- und Navigationslogik.
- Integrationstests für IntelliJ-Platform-Funktionen.
- Testprojekte für HTML und später unterstützte Frameworks.
- Tests für mehrfach gerenderte Komponenten.
- Tests für dynamische DOM-Änderungen.
- Tests für nicht zuordenbare DOM-Elemente.
- Performance- und Ressourcenprüfungen.

Die Implementierung soll durch Tests abgesichert werden, bevor weitere Frameworks integriert werden.

## 10. Erwartungen an Claude Code

Übernimm die Rolle eines erfahrenen JetBrains-Plugin-Entwicklers und Softwarearchitekten.

Arbeite zunächst konzeptionell und forschungsorientiert.

**Beginne nicht unmittelbar mit der vollständigen Implementierung.**

Ich möchte zuerst gemeinsam mit dir die Architektur, technische Machbarkeit und wichtigsten Designentscheidungen diskutieren.

Deine erste Aufgabe ist:

1. Prüfe die aktuelle IntelliJ-Platform-API und die relevanten Möglichkeiten zur Integration von JCEF, Editor-Navigation und PSI.
2. Recherchiere vorhandene Open-Source-Lösungen für die bidirektionale Browser-zu-Quellcode-Navigation.
3. Erkläre, wie eine zuverlässige DOM-zu-Quellcode-Zuordnung technisch funktionieren kann und wo ihre Grenzen liegen.
4. Vergleiche eine vollständig in das JetBrains-Plugin integrierte Lösung mit einer Architektur aus IDE-Plugin und optionalem Browser-/Framework-Adapter.
5. Bewerte, welche Frameworks sich mit vertretbarem Aufwand unterstützen lassen.
6. Schlage eine konkrete, modular aufgebaute Architektur vor.
7. Identifiziere die größten technischen Risiken und sinnvolle Prototypen zur Überprüfung.
8. Erstelle einen realistischen Entwicklungsplan mit priorisierten Arbeitspaketen.

Unterscheide dabei konsequent zwischen nachweislich unterstützten APIs, technisch plausiblen Ansätzen und noch experimentell zu prüfenden Möglichkeiten.

**Wichtig:** Halte die Architektur möglichst einfach. Vermeide unnötige Abstraktionen, eigene Build-Systeme oder zusätzliche Hintergrunddienste, wenn bestehende Technologien die Anforderungen bereits erfüllen.

Stelle mir nach deiner ersten Analyse gezielte Fragen zu wichtigen Architekturentscheidungen, damit wir das Konzept gemeinsam finalisieren können, bevor die Implementierung beginnt.

Das langfristige Ziel ist ein leistungsfähiges, intuitives und Open-Source-fähiges Visual-Inspector-Plugin für JetBrains-IDEs, das die Navigation zwischen Browser und Quellcode erheblich vereinfacht.
