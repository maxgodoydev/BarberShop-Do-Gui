"""Regras de qualidade. Cada regra devolve contagem e amostra de IDs (nunca dados pessoais)."""
from __future__ import annotations
from dataclasses import dataclass, field
import pandas as pd
from .definitions import STATUS_VALIDOS, OCUPAM_AGENDA, COLUNAS_OBRIGATORIAS


@dataclass
class Achado:
    regra: str
    severidade: str          # ERRO = linha rejeitada; AVISO/CRITICO = linha mantida e sinalizada
    descricao: str
    quantidade: int
    exemplos_ids: list = field(default_factory=list)


def _achado(regra, sev, desc, ids):
    ids = list(ids)
    return Achado(regra, sev, desc, len(ids), [str(i) for i in ids[:5]])


def validar(fato: pd.DataFrame, servicos: pd.DataFrame, profissionais: pd.DataFrame, agora: pd.Timestamp):
    """Recebe o fato já com tipos convertidos. Devolve (linhas_validas, rejeitadas, achados)."""
    achados: list[Achado] = []
    if fato.empty:
        return fato.copy(), fato.copy(), achados
    rejeitar = pd.Series(False, index=fato.index)
    idc = fato["agendamento_id"].astype("string").fillna("(sem id)")

    faltando = [c for c in COLUNAS_OBRIGATORIAS if c not in fato.columns]
    if faltando:
        raise ValueError(f"Colunas obrigatórias ausentes: {faltando}")

    for col in COLUNAS_OBRIGATORIAS:
        m = fato[col].isna()
        if m.any():
            achados.append(_achado("R01_OBRIGATORIO_AUSENTE", "ERRO", f"Campo obrigatório '{col}' ausente", idc[m]))
            rejeitar |= m
    m = fato["status"].notna() & ~fato["status"].isin(STATUS_VALIDOS)
    if m.any():
        achados.append(_achado("R02_STATUS_INVALIDO", "ERRO", "Status fora do domínio permitido", idc[m])); rejeitar |= m
    m = fato["fim"].notna() & fato["inicio"].notna() & (fato["fim"] <= fato["inicio"])
    if m.any():
        achados.append(_achado("R03_PERIODO_INVALIDO", "ERRO", "fim não é posterior ao início", idc[m])); rejeitar |= m
    m = fato["agendamento_id"].notna() & fato["agendamento_id"].duplicated(keep="first")
    if m.any():
        achados.append(_achado("R04_ID_DUPLICADO", "ERRO", "agendamento_id repetido (mantida a primeira ocorrência)", idc[m])); rejeitar |= m
    m = fato["servico_id"].notna() & ~fato["servico_id"].isin(servicos["servico_id"])
    if m.any():
        achados.append(_achado("R05_SERVICO_ORFAO", "ERRO", "servico_id inexistente na dimensão", idc[m])); rejeitar |= m
    m = fato["profissional_id"].notna() & ~fato["profissional_id"].isin(profissionais["profissional_id"])
    if m.any():
        achados.append(_achado("R06_PROFISSIONAL_ORFAO", "ERRO", "profissional_id inexistente na dimensão", idc[m])); rejeitar |= m

    ok = fato[~rejeitar]
    dur = (ok["fim"] - ok["inicio"]).dt.total_seconds() / 60
    m = ok["duracao_minutos"].notna() & ((dur - ok["duracao_minutos"]).abs() > 0.5)
    if m.any():
        achados.append(_achado("R07_DURACAO_INCONSISTENTE", "AVISO", "duracao_minutos difere de fim-início", ok.loc[m, "agendamento_id"]))
    m = (ok["status"] == "CANCELADO") & ok["cancelado_por"].isna()
    if m.any():
        achados.append(_achado("R08_CANCELAMENTO_SEM_ORIGEM", "AVISO", "Cancelado sem origem (cliente/equipe)", ok.loc[m, "agendamento_id"]))
    m = ok["status"].isin(["SOLICITADO", "CONFIRMADO"]) & (ok["inicio"] < agora)
    if m.any():
        achados.append(_achado("R09_VENCIDO_SEM_DESFECHO", "AVISO", "Horário já passou, mas segue SOLICITADO/CONFIRMADO (sem baixa ou resposta)", ok.loc[m, "agendamento_id"]))
    m = ok["antecedencia_horas"].notna() & (ok["antecedencia_horas"] < 0)
    if m.any():
        achados.append(_achado("R10_CRIADO_APOS_INICIO", "AVISO", "Registro criado depois do horário do atendimento", ok.loc[m, "agendamento_id"]))
    ativos = ok[ok["status"].isin(OCUPAM_AGENDA)].sort_values(["profissional_id", "inicio"])
    if len(ativos) > 1:
        prox_inicio = ativos.groupby("profissional_id")["inicio"].shift(-1)
        m = prox_inicio.notna() & (prox_inicio < ativos["fim"])
        if m.any():
            achados.append(_achado("R11_SOBREPOSICAO_ATIVA", "CRITICO", "Dois agendamentos ativos do mesmo profissional se sobrepõem (viola a regra do banco; linhas mantidas para auditoria)", ativos.loc[m, "agendamento_id"]))
    return ok, fato[rejeitar], achados
