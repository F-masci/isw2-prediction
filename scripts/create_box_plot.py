import sys
import os
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

csv_dir = os.path.join('..', 'output')
file_name = sys.argv[1]

csv_path = os.path.join(csv_dir, file_name)
df = pd.read_csv(csv_path, sep='\t|\s*;|,', engine='python')

metrics = [
    'Precision',
    'Recall',
    'F1-score',
    'Area Under ROC (AUC)',
    'Kappa',
    'Accuracy',
    'True Positive Rate (TPR)',
    'False Positive Rate (FPR)',
    'True Negative Rate (TNR)',
    'False Negative Rate (FNR)',
    'NPofB20'
]
palette = sns.color_palette("Pastel1", n_colors=df['Feature Selection'].nunique())

# Box plot singoli per ogni metrica
for metric in metrics:
    plt.figure(figsize=(16, 7))
    ax = sns.boxplot(
        x='Model',
        y=metric,
        hue='Feature Selection',
        data=df,
        palette=palette,
        dodge=True,
        width=0.6,
        linewidth=1
    )
    plt.title(f'Box Plot {metric}')
    plt.xticks(rotation=0)
    plt.tight_layout()
    plt.legend(title='Feature Selection')
    plt.savefig(f'{metric.lower().replace(" ", "_").replace("(", "").replace(")", "")}_boxplot.png')
    plt.close()

# Box plot unito: griglia, ogni riga un modello, ogni colonna una metrica principale
# Box plot unito: griglia, modelli come colonne, metriche come righe
main_metrics = [
    'Precision',
    'Recall',
    'F1-score',
    'Area Under ROC (AUC)',
    'Kappa',
    'NPofB20'
]
df_melted = df.melt(
    id_vars=['Model', 'Feature Selection'],
    value_vars=main_metrics,
    var_name='Metric',
    value_name='Value'
)

g = sns.FacetGrid(
    df_melted,
    col='Model',
    row='Metric',
    hue='Feature Selection',
    margin_titles=True,
    sharey=False,
    height=5,
    aspect=1.8,
    palette=palette
)
g.map_dataframe(sns.boxplot, x='Feature Selection', y='Value', order=df['Feature Selection'].unique(), dodge=True, width=0.6, linewidth=1)
g.add_legend(title='Feature Selection', bbox_to_anchor=(1.05, 0.5), loc='center left', borderaxespad=0)
g.set_titles(row_template='{row_name}', col_template='{col_name}')
plt.legend(title='Feature Selection')
plt.savefig('main_metrics_grid_boxplot.png', dpi=200)
plt.close()

# Crea una colonna combinata
df['Model_FS'] = df['Model'] + ' / ' + df['Feature Selection']

# Calcola la media delle metriche per ogni combinazione modello-feature selection
mean_df = df.groupby('Model_FS')[main_metrics].mean().reset_index()
mean_df_melted = mean_df.melt(id_vars=['Model_FS'], value_vars=main_metrics, var_name='Metric', value_name='Mean Value')

# Trova la combinazione Model_FS con la media massima per ogni metrica
highlight_fs = {}
for metric in main_metrics:
    metric_df = mean_df_melted[mean_df_melted['Metric'] == metric]
    max_idx = metric_df['Mean Value'].idxmax()
    highlight_fs[metric] = metric_df.loc[max_idx, 'Model_FS']

base_palette = sns.color_palette("Pastel1", n_colors=len(main_metrics))
highlight_colors = ['crimson', 'royalblue', 'limegreen', 'purple', 'orange', 'yellow']

plt.figure(figsize=(18, 7))
ax = sns.barplot(
    data=mean_df_melted,
    x='Model_FS',
    y='Mean Value',
    hue='Metric',
    palette=base_palette,
    dodge=True
)

# Associa i colori alle barre in base ai dati
for bar in ax.patches:
    # Ricava Model_FS e Metric dalla posizione della barra
    x = bar.get_x() + bar.get_width() / 2
    height = bar.get_height()
    # Trova la riga corrispondente nel dataframe
    for idx, row in mean_df_melted.iterrows():
        if abs(row['Mean Value'] - height) < 1e-6:
            if row['Model_FS'] in highlight_fs.values() and row['Mean Value'] == mean_df_melted[(mean_df_melted['Metric'] == row['Metric'])]['Mean Value'].max():
                color = highlight_colors[main_metrics.index(row['Metric'])]
            else:
                color = base_palette[main_metrics.index(row['Metric'])]
            bar.set_color(color)
            break

plt.title('Media delle metriche per ogni coppia Model / Feature Selection (max evidenziato)')
plt.xlabel('Model / Feature Selection')
plt.ylabel('Media')
plt.xticks(rotation=30)
plt.legend(title='Metrica', bbox_to_anchor=(1.05, 1), loc='upper left')
plt.tight_layout()
plt.savefig('mean_metrics_model_fs_barplot_highlighted.png', dpi=200)
plt.close()