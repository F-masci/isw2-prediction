# Analisi della Buggyness nei Metodi Software: Un Approccio Data-Driven al Refactoring

Questo progetto universitario esplora la previsione e la prevenzione dei bug nel software attraverso l'analisi delle metriche di codice. Il lavoro propone un approccio basato sui dati per identificare e quantificare i metodi a rischio di difetti, dimostrando come interventi mirati di refactoring possano migliorare la manutenibilità e la robustezza del codice.

Il progetto è stato sviluppato da Francesco Masci (0365258) per il corso di Ingegneria del Software 2 presso l'Università degli Studi di Roma "Tor Vergata" nell'anno accademico 2024/2025.

## Contesto e Obiettivi

La manutenibilità è un pilastro fondamentale per lo sviluppo di sistemi software sostenibili. Sebbene l'attenzione si concentri spesso sulla qualità del codice, la ricerca empirica ha una lacuna nel quantificare l'impatto diretto del refactoring sulla riduzione dei difetti a livello di singolo metodo.

L'obiettivo principale del progetto è fornire uno strumento in grado non solo di individuare i metodi a rischio, ma anche di stimare quanti difetti potrebbero essere prevenuti tramite refactoring.

Le **domande di ricerca (RQ)** fondamentali sono due:

* **RQ1**: Quale classificatore è in grado di prevedere con maggiore accuratezza se un metodo sarà "buggy" in una data release?
* **RQ2**: Quanti metodi buggy si potrebbero potenzialmente evitare applicando tecniche di predizione efficaci a supporto del refactoring?

-----

## Metodologia

Il progetto si basa sulla creazione di un dataset dettagliato a partire da due progetti open source di rilievo: **Apache BookKeeper** e **Apache OpenJPA**. La metodologia adottata si articola in diverse fasi:

### 1\. Creazione del Dataset

I dati sono stati estratti dai repository Git e dalle informazioni di tracciamento dei bug di Jira per mappare l'evoluzione di ogni singolo metodo e associarne lo stato di "buggyness" nel tempo. Per i casi in cui le versioni affette da bug non erano esplicitate, è stata utilizzata la tecnica di inferenza statistica **"Proportion"**, che sfrutta il comportamento storico del progetto per stimare le versioni coinvolte.

### 2\. Raccolta delle Metriche

Sono state raccolte 22 metriche a livello di metodo, suddivise in due categorie:

* **Metriche strutturali**: Dati sulla complessità intrinseca del codice (es. Linee di Codice, Complessità Ciclomatica, Complessità Cognitiva).
* **Metriche storiche**: Dati sull'evoluzione del codice (es. lo storico delle modifiche e il churn).

L'analisi di correlazione ha rivelato che in **BookKeeper** le **metriche storiche** sono i predittori più influenti, mentre in **OpenJPA** sono le **metriche strutturali** a dominare.

### 3\. Protocollo Sperimentale

Per garantire risultati realistici e robusti, è stato adottato un protocollo di validazione **walk-forward**. Questo approccio simula un ambiente di produzione addestrando il modello solo sui dati delle release precedenti, evitando il *data leakage*.

### 4\. Addestramento e Confronto dei Modelli

Sono stati addestrati e confrontati tre classificatori per prevedere la presenza di bug:

* **Naive Bayes**
* **IBk** (k-Nearest Neighbors)
* **Random Forest**

Per l'ottimizzazione dei modelli, è stata utilizzata la tecnica di selezione delle feature **Information Gain**, che ha ridotto la dimensionalità del dataset senza compromettere le prestazioni.

-----

## Risultati

I risultati hanno dimostrato che il modello **Random Forest**, combinato con la selezione delle feature tramite Information Gain, rappresenta il classificatore più equilibrato e robusto, offrendo il miglior compromesso tra accuratezza, stabilità e interpretabilità.

### Refactoring Guidato dai Dati

Il progetto ha identificato due metodi specifici come candidati ideali per il refactoring, basandosi sulle metriche di codice più correlate alla "buggyness" per ciascun progetto:

* **BookKeeper**: Il metodo `processPacket` (`BookieServer`), con una complessità ciclomantica di 34.
* **OpenJPA**: Il metodo `eval` (`JPQLExpressionBuilder`), con 175 statement.

-----

## Conclusioni e Prospettive Future

Il progetto conferma che gli approcci data-driven possono supportare in modo efficace le decisioni di refactoring e contribuire alla prevenzione dei bug. Le analisi condotte aprono la strada a diversi sviluppi futuri, tra cui:

* **Analisi comparativa**: Applicare la stessa metodologia a progetti con architetture non object-oriented.
* **Integrazione dei dati**: Arricchire il dataset con metriche a runtime e dati di performance.
* **Sviluppo di strumenti interattivi**: Creare tool per gli sviluppatori che evidenzino in tempo reale i metodi critici.

-----

## Contributi e Riferimenti

* **Autore**: Francesco Masci 0365258
* **Corso**: Ingegneria del Software 2 (ISW2)
* **Università**: Università degli Studi di Roma "Tor Vergata"
* **Anno Accademico**: 2024/2025
* **Repository GitHub**: [isw2-prediction](https://github.com/F-masci/isw2-prediction)
* **Analisi SonarCloud**: [isw2-prediction](https://sonarcloud.io/project/overview?id=F-masci_isw2-prediction)