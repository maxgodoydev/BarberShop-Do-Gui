#!/usr/bin/env python3
"""Paridade entre as DUAS implementações dos indicadores:
   (1) a tela de BI do sistema  -> função SQL fn_bi_resumo (usada por /api/admin/bi)
   (2) o pipeline Python         -> analytics/barbershop_analytics (ETL sobre as views analíticas)

Por que existe: os testes do pipeline já comparam o Python com database/queries/ (via SQLite), mas nada comparava
com fn_bi_resumo. Sem isto, uma mudança de regra em um dos lados passaria despercebida e os dois painéis
mostrariam números diferentes para a mesma métrica.

Compara (mesma janela, sem dados pessoais): pedidos, atendimentos concluídos, taxa de cancelamento e a
distribuição por dia da semana. Receita NÃO entra: o pipeline recusa calcular receita (preço de tabela não é
pagamento), enquanto o BI exibe a soma do preço de tabela dos concluídos.

Uso (a partir da raiz, com o banco já aplicado e variáveis PG* definidas):
    python3 scripts/paridade_bi.py [--agora 2026-10-24T12:00:00] [--dias 3650]
Sai com código 1 se algum indicador divergir. Requer apenas psql e pandas.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import pandas as pd

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ / "analytics"))
from barbershop_analytics import pipeline  # noqa: E402

VIEWS = {"fato_agendamentos.csv": "vw_fato_agendamentos", "dim_servico.csv": "vw_dim_servico",
         "dim_profissional.csv": "vw_dim_profissional"}


def psql(*args: str) -> str:
    r = subprocess.run(["psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", *args], stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, text=True)
    if r.returncode != 0:
        raise SystemExit(f"psql falhou: {r.stderr.strip()[:300]}")
    return r.stdout


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--agora", default="2026-10-24T12:00:00", help="referência de 'agora' (padrão: âncora do seed demo)")
    ap.add_argument("--dias", type=int, default=3650, help="janela do BI em dias (grande = todo o histórico)")
    a = ap.parse_args()
    agora = pd.Timestamp(a.agora)

    bi = json.loads(psql("-tA", "-c", f"SELECT fn_bi_resumo({a.dias}, '{a.agora}'::timestamp)").strip())

    with tempfile.TemporaryDirectory() as tmp:
        entrada, saida = Path(tmp) / "in", Path(tmp) / "out"
        entrada.mkdir()
        for arquivo, view in VIEWS.items():
            psql("-c", f"\\copy (SELECT * FROM {view}) TO '{entrada / arquivo}' CSV HEADER")
        # O BI só considera inicio < agora (e >= agora - dias). O pipeline lê tudo, então aplicamos a mesma janela.
        fato = pd.read_csv(entrada / "fato_agendamentos.csv", dtype=str, keep_default_na=False, na_values=[""])
        inicio = pd.to_datetime(fato["inicio"])
        fato[(inicio < agora) & (inicio >= agora - pd.Timedelta(days=a.dias))].to_csv(entrada / "fato_agendamentos.csv", index=False)
        rel = pipeline.executar("csv", entrada, saida, agora)
        py_dia = pd.read_csv(saida / "agg_dia_hora.csv").groupby("dia_semana_iso")["quantidade"].sum().to_dict()

    k, py = bi["kpis"], rel["kpis"]
    bi_dia = {int(d["dia"]): int(d["quantidade"]) for d in bi["diaSemana"]}
    py_dia = {int(d): int(q) for d, q in py_dia.items()}
    pares = [
        ("pedidos", k["pedidos"], py["pedidos"]),
        ("atendimentos concluídos", k["atendimentos"], py["concluidos"]),
        ("taxa de cancelamento %", float(k["taxaCancelamento"]), float(py["taxa_cancelamento_pct"] or 0)),
        ("pedidos por dia da semana", bi_dia, py_dia),
    ]
    falhas = 0
    print(f"Janela: {a.dias} dias até {a.agora} | linhas lidas pelo pipeline: {rel['linhas_lidas']}")
    print(f"{'indicador':<28}{'BI (fn_bi_resumo)':<42}{'pipeline Python':<42}resultado")
    for nome, x, y in pares:
        ok = x == y
        falhas += not ok
        print(f"{nome:<28}{str(x):<42}{str(y):<42}{'OK' if ok else 'DIVERGE'}")
    if not k["pedidos"]:
        print("AVISO: base vazia, a paridade não prova nada (rode com --demo).")
        return 1
    print("\nOK: BI do sistema e pipeline Python concordam." if not falhas else f"\n{falhas} indicador(es) divergem.")
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
