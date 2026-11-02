"""Orquestração: ler -> validar -> tratar -> calcular -> exportar. Sem dados pessoais em arquivos ou logs."""
from __future__ import annotations
import argparse, json, logging, sys
from pathlib import Path
import pandas as pd
from . import metrics, quality, sources
from .definitions import COLUNAS_FATO

log = logging.getLogger("barbershop_analytics")


def tratar(raw: pd.DataFrame) -> pd.DataFrame:
    """Padroniza tipos e categorias. Valores impossíveis de converter viram NaN/NaT (e são tratados em quality)."""
    f = raw.copy()
    for c in COLUNAS_FATO:
        if c not in f.columns:
            f[c] = None
    for c in ("inicio", "fim", "criado_em"):
        f[c] = pd.to_datetime(f[c], errors="coerce")
    for c in ("agendamento_id", "servico_id", "profissional_id", "dia_semana_iso", "hora_inicio", "ano_mes", "duracao_minutos"):
        f[c] = pd.to_numeric(f[c], errors="coerce").astype("Int64")
    f["preco_referencia"] = pd.to_numeric(f["preco_referencia"], errors="coerce")
    f["antecedencia_horas"] = pd.to_numeric(f["antecedencia_horas"], errors="coerce")
    f["status"] = f["status"].astype("string").str.strip().str.upper()
    f["cancelado_por"] = f["cancelado_por"].astype("string").str.strip().str.upper()
    f.loc[f["cancelado_por"] == "", "cancelado_por"] = pd.NA
    # derivados determinísticos (quando ausentes na fonte)
    f["data_inicio"] = f["inicio"].dt.date
    f["data_criacao"] = f["criado_em"].dt.date
    f["dia_semana_iso"] = f["dia_semana_iso"].fillna(f["inicio"].dt.isocalendar().day).astype("Int64")
    f["hora_inicio"] = f["hora_inicio"].fillna(f["inicio"].dt.hour).astype("Int64")
    f["ano_mes"] = pd.to_numeric(f["ano_mes"], errors="coerce").fillna(f["inicio"].dt.year * 100 + f["inicio"].dt.month).astype("Int64")
    return f[COLUNAS_FATO]


def dims(d: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    s = d["servico"].copy(); p = d["profissional"].copy()
    s["servico_id"] = pd.to_numeric(s["servico_id"], errors="coerce").astype("Int64")
    p["profissional_id"] = pd.to_numeric(p["profissional_id"], errors="coerce").astype("Int64")
    return s, p


def executar(fonte: str, entrada: Path, saida: Path, agora: pd.Timestamp, parquet: bool = False) -> dict:
    saida.mkdir(parents=True, exist_ok=True)
    dados = sources.ler_csv(entrada) if fonte == "csv" else sources.ler_postgres()
    servicos, profissionais = dims(dados)
    bruto = len(dados["fato"])
    fato = tratar(dados["fato"])
    validas, rejeitadas, achados = quality.validar(fato, servicos, profissionais, agora)
    log.info("linhas lidas=%d válidas=%d rejeitadas=%d", bruto, len(validas), len(rejeitadas))

    k = metrics.kpis(validas, agora)
    saidas = {
        "fato_agendamentos_tratado": validas, "agg_status": metrics.por_status(validas) if len(validas) else pd.DataFrame(columns=["status", "quantidade"]),
        "agg_servico": metrics.por_servico(validas, servicos), "agg_dia_hora": metrics.dia_hora(validas),
        "agg_profissional": metrics.por_profissional(validas, profissionais), "agg_mensal": metrics.mensal(validas),
        "agg_cancelamentos": metrics.cancelamentos(validas), "agg_pendencias": metrics.pendencias(validas, agora),
        "dim_servico": servicos, "dim_profissional": profissionais, "dim_calendario": metrics.calendario(validas),
    }
    for nome, df in saidas.items():
        df.to_csv(saida / f"{nome}.csv", index=False, encoding="utf-8")
        if parquet:
            try:
                df.to_parquet(saida / f"{nome}.parquet", index=False)
            except ImportError:
                log.warning("pyarrow não instalado: Parquet ignorado."); parquet = False
    rejeitadas[["agendamento_id", "status"]].to_csv(saida / "rejeitados_ids.csv", index=False)  # somente IDs/status, sem PII
    relatorio = {
        "gerado_para_referencia_agora": agora.isoformat(), "linhas_lidas": bruto, "linhas_validas": len(validas),
        "linhas_rejeitadas": len(rejeitadas), "completude_pct": None if bruto == 0 else round(100 * len(validas) / bruto, 1),
        "achados": [a.__dict__ for a in achados], "kpis": k,
        "aviso": "Dados de demonstração são fictícios." if fonte == "csv" else "Fonte: views do PostgreSQL.",
    }
    (saida / "quality_report.json").write_text(json.dumps(relatorio, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    (saida / "kpis.json").write_text(json.dumps(k, ensure_ascii=False, indent=2), encoding="utf-8")
    (saida / "quality_report.md").write_text(_md(relatorio), encoding="utf-8")
    return relatorio


def _md(r: dict) -> str:
    L = ["# Relatório de qualidade dos dados", "", f"- Referência de 'agora': `{r['gerado_para_referencia_agora']}`",
         f"- Linhas lidas: **{r['linhas_lidas']}** · válidas: **{r['linhas_validas']}** · rejeitadas: **{r['linhas_rejeitadas']}**",
         f"- Completude (válidas/lidas): **{r['completude_pct']}%**" if r["completude_pct"] is not None else "- Base vazia.", f"- {r['aviso']}", ""]
    if r["achados"]:
        L += ["| Regra | Severidade | Qtde | Descrição | Exemplos de ID |", "|---|---|---:|---|---|"]
        L += [f"| {a['regra']} | {a['severidade']} | {a['quantidade']} | {a['descricao']} | {', '.join(a['exemplos_ids'])} |" for a in r["achados"]]
    else:
        L.append("Nenhum problema de qualidade encontrado.")
    return "\n".join(L) + "\n"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="barbershop_analytics", description=__doc__)
    ap.add_argument("--source", choices=["csv", "postgres"], default="csv")
    ap.add_argument("--input", type=Path, default=Path("sample_data"), help="pasta com os CSVs (modo csv)")
    ap.add_argument("--output", type=Path, default=Path("output"))
    ap.add_argument("--agora", default=None, help="data/hora de referência ISO; padrão = agora (ex.: 2026-10-24T12:00:00)")
    ap.add_argument("--parquet", action="store_true", help="exporta Parquet também (requer pyarrow)")
    a = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    agora = pd.Timestamp(a.agora) if a.agora else pd.Timestamp.now().floor("min")
    try:
        r = executar(a.source, a.input, a.output, agora, a.parquet)
    except Exception as exc:  # mensagem curta, sem despejar linhas de dados
        log.error("Falha no pipeline: %s: %s", type(exc).__name__, exc)
        return 1
    print(json.dumps(r["kpis"], ensure_ascii=False, indent=2)); return 0


if __name__ == "__main__":
    sys.exit(main())
