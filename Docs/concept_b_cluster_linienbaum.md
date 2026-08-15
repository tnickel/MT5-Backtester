# Konzept: B-Cluster-Linienbaum

Status: Phase 1–5 erledigt.
Bezug: `Docs/concept_interactive_multistage_workflow.md`,
`Docs/concept_master_strategy_lineage.md`,
Tick-Kill-Workflow `ToTheMoon132TickKillWorkflowFactory`

---

## 1. Wozu das hier ist

Die Guided-/Tick-Kill-Pipeline bleibt eine **lineare Task-Kette**
(Optimizer → Filter → Master-Referenz → …). Daraus wird **kein DAG**.
Was sich ändert: Es gibt nicht mehr nur **einen** Champion pro Stufe,
sondern bis zu **zehn Cluster-Linien** B1–B10. **Show Flow** zeigt das
als Stammbaum (Stufen = Stamm, Cluster = Äste), nicht als neue
Workflow-Graphik.

Dieses Dokument ist die vereinbarte Semantik. Die Phasen in Abschnitt 7
sagen, was schon im Code ist und was noch fehlt.

---

## 2. Problem: Ein Champion friert das Grid ein

Heute gewinnt in Automatik der **Top-Score** einer Stufe. Nach g01–g04
(Grid, Taktung, Envelopes) sitzt fast alles auf **einer** Grid-Form.
Spätere Stufen (ADX, ATR, Entry, Exit, Safety) können die
**Equity-Form** kaum noch drehen — sie tunen nur noch auf derselben
Basis.

Tick-Kill macht das nicht besser, wenn die Diversität erst bei
**k12 Top-20** kommt: Dann sind die 20 oft 20 Varianten von Step 900,
nicht 10 verschiedene Grids. Kill auf Tick ist dann zu spät: Die
Form-Entscheidung war schon bei g01–g04 gefallen.

Master-Referenz (`Docs/concept_master_strategy_lineage.md`) misst, ob
*die eine* Linie besser wird. Der Linienbaum hält *mehrere* Linien
am Leben, damit später noch etwas zu messen gibt.

---

## 3. Modell: lineare Kette, mehrere Linien

| Bleibt | Ändert sich |
|---|---|
| Task-Reihenfolge (g01 … g11, dann k12 … k18) | Bis zu 10 Cluster B1–B10 |
| Eine Databank pro Task (`g09_entry_pick`, …) | Jede Strategie trägt eine `clusterId` |
| Automatik / Hand-Pick / Filter | Automatik **pro Cluster**, nicht global Top-1 |
| Ranking am Ende: OOS (`k16_oos_tick`) | Tick-Kills sind **Pass/Fail pro Linie** |

### 3.1 Was ein Cluster ist

Ein Cluster ist eine **Grid-Form**, nicht ein Score-Nachbar. Distanz
auf den Form-Parametern, nicht auf allen 20 EA-Inputs:

- `Grid_Step`
- `Step_Multiplier`
- `Next_Lot_Multiplier`
- `Inp_TakeProfit` (g01 sucht 50–80, Schritt 5; Champion 65 und Live-50 liegen auf dem Raster)
- Envelope-Timeframe / Deviation (oben/unten, je nachdem was im Set steht)

Ziel: nicht 126 Klone von Step 900, sondern z. B. „eng / weit / aggressiv
Lot / anderes Envelope“. Maximal **10** Linien. Innerhalb einer Linie
dürfen **2–3 nahe Verwandte** leben (Feintuning, nicht zweite Form).

### 3.2 Improve-or-die

Jede Stufe versucht, **diese Linie** zu verbessern (gleiche Form,
bessere Kennzahlen / Referenz wie bisher).

- **Gelingt es:** Linie bleibt, Zähler = lebende Strategien in der
  `_pick`-Databank dieser Stufe, Marker `▲` wenn klar besser als vorher.
- **Scheitert es:** Linie **stirbt**. Zähler `0`, Ast bleibt sichtbar
  (`0 ✕`, grau) — Historie, kein Löschen der Vergangenheit.
- Optimizer einer Stufe startet vom **Linien-Champion**, nicht vom
  globalen Projekt-Champion.

Tote Linien laufen nicht weiter in teure Tick-Stufen. Sichtbar bleiben
sie im Baum, damit klar ist, wo die Form verloren ging.

### 3.3 Tick-Kills vs. Ranking

Smoke (k13) und 1Y-Kill (k14) sind **pro Linie** bestanden/durchgefallen.
Sie sortieren nicht. Sortierung bleibt **OOS (k16)**. Ein `★` sitzt auf
der OOS-Gewinner-Linie, nicht auf dem besten Smoke.

---

## 4. Show Flow: Stamm + Äste

Show Flow bleibt der Ort für den Überblick. Statt nur
Parameter-Übergänge der einen Master-Linie: **Linienbaum**.

```
Stamm              B1 Grid-eng    B2 Grid-weit    B3 …
g01 Grid           ● 3            ● 2             ● 1
g02 Taktung        ● 3 ▲          ● 2             ○ 0 ✕
…
g09 Entry          B2 · g09 · 2
…
k16 OOS            ● 1 ★          ● 1             —
```

Knotenbeschriftung: **wie viele Strategien in diesem Ast gerade leben**,
z. B. `B2 · g09 · 2`.

| Marker | Bedeutung |
|---|---|
| Zahl | Lebende in der `_pick` dieser Stufe / dieses Clusters |
| `▲` | Diese Stufe hat die Linie verbessert |
| `0 ✕` | Tot (grau, Historie) |
| `★` | OOS-Gewinner (k16) |
| `—` | Stufe für diese Linie nicht mehr gelaufen |

Klick auf einen Ast öffnet die **bestehende Equity-Galerie**, gefiltert
auf diesen Cluster. Keine 10 000 Thumbnails im Baum.

---

## 5. Census: nur `_pick`, nie `_raw`

Die Zähler im Baum kommen ausschließlich aus den **Pick-Databanken**
(`g01_grid_pick`, `g09_entry_pick`, `k12_dev_top20`, …).

Optimizer-`_raw` (tausende Pässe) zählt nicht. Sonst zeigt Show Flow
Müll und wird langsam. `ClusterCensus` am `CustomProject` aggregiert
genau das: clusterId → Stufe → Anzahl lebend.

---

## 6. Was du in der UI erwarten sollst (wenn gebaut)

- Workflow-Editor: Kette wie bisher. Kein zweiter Graph zum Umsortieren
  von Tasks.
- **Show Flow:** Baum wie oben. Grau = tot, Stern = OOS.
- Klick Ast → Galerie nur dieser Linie.
- Automatik: Top-Score **innerhalb** B3, nicht „globaler Sieger tötet
  B1–B10“.
- Hand-Pick bleibt möglich; Cluster-ID der gewählten Strategie bleibt
  kleben.

Ohne Census (alte Projekte): Show Flow bleibt die lineare Timeline.
Mit Census: Linienbaum oben, Parameter-Board darunter wie bisher.
Klick auf eine Cluster-Zelle öffnet die Equity-Galerie (Pick-Databank, gefiltert auf diese Linie).
Klick auf die Stufe (Stamm) bleibt die Parameter-Übernahme.
Phase 4: Automatik ist **pro lebender Linie**. Tote Linien bleiben im Census (`DEAD`, `0 ✕`)
und gehen nicht in den nächsten Optimizer. Folge-Optimizer mit 2+ lebenden Linien
laufen **nacheinander** auf demselben Terminal (`MetaTraderRunLock`), nicht parallel.

---

## 7. Roadmap (Stand der Umsetzung)

Damit die Doku nicht so tut, als wäre der Baum schon klickbar:

| Phase | Inhalt | Stand |
|---|---|---|
| **1** | `clusterId` an `CombinedPass`; `ClusterCensus` am `CustomProject`; IDs stempeln; Tests. **Keine UI.** | erledigt |
| **2** | Show Flow als Linienbaum (Stamm/Äste, Zähler, `▲` / `0 ✕`; `★` sobald OOS im Census) | erledigt (Show Flow → Linienbaum; ohne Census linearer Fallback) |
| **3** | Klick auf Ast → Equity-Galerie gefiltert auf Cluster | erledigt (Zelle = Galerie auf Pick/`clusterId`; Stamm = Handoff; leere Linie = leere Galerie) |
| **4** | Improve-or-die in der Pipeline; Automatik **pro Cluster** | erledigt (MASTER_REFERENCE misst jede LIVE-Linie; tot → nicht adoptiert; Folge-Optimizer sequentiell pro Linie, ein Terminal; ohne Census/clusterId bleibt die alte Einzel-Automatik) |
| **5** | Tick-Kill- und Guided-Factories stempeln Cluster-IDs ab **g01** | erledigt (nach g01-Qualitätsfilter: Diversität auf Grid-Form → `g01_grid_pick` mit B1–B10; k12/g11 Top-20 ist Re-Diversität der Überlebenden und behält `clusterId`; max. 10 Linien) |

Phase 5 hängt an 1+4: Ohne Stempel ab g01 gibt es in k12 wieder nur
einen Haufen gleicher Grids. Die Factories legen dafür einen eigenen
`DIVERSITY_FILTER` (`01 Grid-Fundament — Diversität (B-Cluster)`) zwischen
`g01_grid_quality` und `g01_grid_pick`. g02 liest die geclusterten Picks.

---

## 8. Abgrenzung

- **Kein** Umbau der Task-Liste in einen gerichteten Graphen.
- **Kein** Ranking auf Tick-Smoke.
- **Keine** Census-Zahlen aus Optimizer-Rohdaten.
- Diversitäts-Clustering-Task (Benutzerhandbuch 12.1) bleibt ein
  eigener Task-Typ für beliebige Projekte. B-Cluster ist die
  **ToTheMoon-Stufenkette**: Form-Distanz auf Grid-Parametern, fest
  verdrahtet in Guided/Tick-Kill, nicht derselbe Dialog.

---

## 9. Kurz für den Alltag

1. Früh (g01) mehrere Grid-Formen festhalten, nicht eine.
2. Jede Form getrennt weiterzüchten; wer nicht besser wird, stirbt
   sichtbar.
3. Teure Ticks nur noch für lebende Linien, Kill = Ja/Nein.
4. Gewinner erst bei k16.
5. Show Flow = wer lebt noch, nicht 10k Kurven im Baum.
