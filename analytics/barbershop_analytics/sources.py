"""Leitura das fontes: exportação CSV controlada ou views do PostgreSQL (somente leitura)."""
from __future__ import annotations
import os
from pathlib import Path
import pandas as pd

TABELAS = {"fato": "vw_fato_agendamentos", "servico": "vw_dim_servico", "profissional": "vw_dim_profissional"}
ARQUIVOS = {"fato": "fato_agendamentos.csv", "servico": "dim_servico.csv", "profissional": "dim_profissional.csv"}


def ler_csv(pasta: Path) -> dict[str, pd.DataFrame]:
    saida = {}
    for chave, nome in ARQUIVOS.items():
        caminho = Path(pasta) / nome
        if not caminho.is_file():
            raise FileNotFoundError(f"Arquivo esperado não encontrado: {caminho}")
        saida[chave] = pd.read_csv(caminho, dtype=str, keep_default_na=False, na_values=[""])
    return saida


def ler_postgres(url: str | None = None) -> dict[str, pd.DataFrame]:
    """Lê as views. A URL vem de ANALYTICS_DB_URL (nunca do código). Requer `psycopg`."""
    url = url or os.environ.get("ANALYTICS_DB_URL")
    if not url:
        raise RuntimeError("Defina ANALYTICS_DB_URL (ex.: postgresql://analytics_ro:SENHA@host:5432/barbershop_gui).")
    try:
        import psycopg
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("Instale psycopg: pip install 'psycopg[binary]'") from exc
    saida = {}
    with psycopg.connect(url, connect_timeout=10) as conn, conn.cursor() as cur:
        for chave, view in TABELAS.items():
            cur.execute(f"SELECT * FROM {view}")  # nome de view fixo, sem entrada do usuário
            cols = [d.name for d in cur.description]
            saida[chave] = pd.DataFrame(cur.fetchall(), columns=cols).astype(object).where(lambda d: d.notna(), None).astype(str).replace({"None": None})
    return saida
