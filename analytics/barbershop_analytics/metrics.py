"""Cálculo dos indicadores. Definições idênticas às de database/queries/ e docs/metricas.md."""
from __future__ import annotations
import pandas as pd
from .definitions import PEDIDO, DEMANDA_NAO_CANCELADA


def _pct(num, den):
    return None if not den else round(100.0 * num / den, 1)


def kpis(f: pd.DataFrame, agora: pd.Timestamp) -> dict:
    pedidos = f[f["status"].isin(PEDIDO)]
    venc = pedidos[pedidos["inicio"] < agora]
    conc, canc = int((f["status"] == "CONCLUIDO").sum()), int((f["status"] == "CANCELADO").sum())
    expir = int((f["status"] == "EXPIRADO").sum())
    return {
        "pedidos": len(pedidos), "concluidos": conc, "cancelados": canc, "pedidos_vencidos": len(venc),
        "taxa_cancelamento_pct": _pct(canc, len(pedidos)),
        "taxa_conclusao_pct": _pct(conc, len(venc)),
        "taxa_abandono_reserva_pct": _pct(expir, expir + len(pedidos)),
        "observacao": "Base vazia: nenhum indicador calculado." if f.empty else
                      "Indicadores de preço/receita não são calculados: preço de tabela não é pagamento recebido.",
    }


def por_status(f):
    return f.groupby("status").size().rename("quantidade").reset_index().sort_values("status")


def por_servico(f, dim_servico):
    base = dim_servico[["servico_id", "servico"]].copy()
    g = f.groupby("servico_id")["status"].agg(
        demanda_nao_cancelada=lambda s: int(s.isin(DEMANDA_NAO_CANCELADA).sum()),
        concluidos=lambda s: int((s == "CONCLUIDO").sum()),
        cancelados=lambda s: int((s == "CANCELADO").sum())).reset_index() if len(f) else pd.DataFrame(
        columns=["servico_id", "demanda_nao_cancelada", "concluidos", "cancelados"])
    m = base.merge(g, on="servico_id", how="left").fillna({"demanda_nao_cancelada": 0, "concluidos": 0, "cancelados": 0})
    for c in ("demanda_nao_cancelada", "concluidos", "cancelados"):
        m[c] = m[c].astype(int)
    return m.drop(columns="servico_id").sort_values(["demanda_nao_cancelada", "servico"], ascending=[False, True]).reset_index(drop=True)


def dia_hora(f):
    d = f[f["status"].isin(DEMANDA_NAO_CANCELADA)]
    return d.groupby(["dia_semana_iso", "hora_inicio"]).size().rename("quantidade").reset_index().sort_values(["dia_semana_iso", "hora_inicio"])


def por_profissional(f, dim_prof):
    d = f[f["status"].isin(DEMANDA_NAO_CANCELADA)]
    g = d.groupby("profissional_id").agg(pedidos_nao_cancelados=("status", "size"),
                                          concluidos=("status", lambda s: int((s == "CONCLUIDO").sum()))).reset_index() if len(d) else pd.DataFrame(
        columns=["profissional_id", "pedidos_nao_cancelados", "concluidos"])
    c = f[f["status"] == "CANCELADO"].groupby("profissional_id").size().rename("cancelados").reset_index() if len(f) else pd.DataFrame(columns=["profissional_id", "cancelados"])
    m = dim_prof[["profissional_id", "profissional"]].merge(g, on="profissional_id", how="left").merge(c, on="profissional_id", how="left")
    m = m.fillna({"pedidos_nao_cancelados": 0, "concluidos": 0, "cancelados": 0})
    for col in ("pedidos_nao_cancelados", "concluidos", "cancelados"):
        m[col] = m[col].astype(int)
    total = m["pedidos_nao_cancelados"].sum()
    m["participacao_pct"] = m["pedidos_nao_cancelados"].apply(lambda v: _pct(v, total))
    return m.drop(columns="profissional_id").sort_values(["pedidos_nao_cancelados", "profissional"], ascending=[False, True]).reset_index(drop=True)


def mensal(f):
    if f.empty:
        return pd.DataFrame(columns=["ano_mes", "pedidos", "concluidos", "cancelados"])
    t = f.assign(ano_mes=f["inicio"].dt.year * 100 + f["inicio"].dt.month)
    g = t.groupby("ano_mes")["status"].agg(pedidos=lambda s: int(s.isin(PEDIDO).sum()),
                                           concluidos=lambda s: int((s == "CONCLUIDO").sum()),
                                           cancelados=lambda s: int((s == "CANCELADO").sum())).reset_index()
    return g.sort_values("ano_mes")


def cancelamentos(f):
    c = f[f["status"] == "CANCELADO"]
    return c.assign(origem=c["cancelado_por"].fillna("NAO_INFORMADO")).groupby("origem").size().rename("cancelados").reset_index().sort_values(["cancelados", "origem"], ascending=[False, True]) if len(c) else pd.DataFrame(columns=["origem", "cancelados"])


def pendencias(f, agora):
    v = f[f["status"].isin(["SOLICITADO", "CONFIRMADO"]) & (f["inicio"] < agora)]
    return v.groupby("status").size().rename("vencidos_sem_desfecho").reset_index().sort_values("status") if len(v) else pd.DataFrame(columns=["status", "vencidos_sem_desfecho"])


def calendario(f):
    if f.empty:
        return pd.DataFrame(columns=["data", "ano", "mes", "dia_semana_iso"])
    r = pd.date_range(f["inicio"].min().normalize(), f["inicio"].max().normalize() + pd.Timedelta(days=30), freq="D")
    return pd.DataFrame({"data": r.date, "ano": r.year, "mes": r.month, "dia_semana_iso": r.isocalendar().day.values})
