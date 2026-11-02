#!/usr/bin/env python3
"""Gera dados 100% FICTÍCIOS de demonstração (seed SQL + CSVs espelhando as views analíticas).

Determinístico: a mesma semente e a mesma data-âncora produzem os mesmos arquivos.
Nenhum nome, e-mail ou telefone é real: e-mails usam o domínio reservado `.invalid`.
Uso: python3 scripts/gerar_dados_demo.py [--anchor AAAA-MM-DD] [--seed 42]
"""
from __future__ import annotations
import argparse, csv, random
from datetime import date, datetime, time, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# (id, nome, descricao, preco, duracao, requer_avaliacao, peso_demanda)
SERVICOS = [
    (1, "Corte tradicional", "Corte clássico na tesoura e máquina.", 40.00, 30, False, 18),
    (2, "Degradê", "Degradê com acabamento.", 45.00, 40, False, 20),
    (3, "Corte infantil", "Corte para crianças.", 35.00, 30, False, 8),
    (4, "Corte na máquina", "Corte com máquina em um só comprimento.", 30.00, 20, False, 10),
    (5, "Barba", "Barba com toalha quente e acabamento.", 25.00, 20, False, 12),
    (6, "Cabelo e barba", "Combo de corte e barba.", 60.00, 50, False, 14),
    (7, "Luzes", "Mechas. Exige avaliação prévia do cabelo.", 150.00, 120, True, 1),
    (8, "Platinado ou descoloração", "Descoloração global. Exige avaliação prévia.", 200.00, 150, True, 1),
    (9, "Coloração ou pigmentação", "Cor ou pigmentação. Exige avaliação prévia.", 70.00, 45, True, 2),
    (10, "Sobrancelha", "Design de sobrancelha.", 15.00, 10, False, 5),
    (11, "Acabamento", "Acabamento de contorno (pezinho).", 15.00, 10, False, 6),
    (12, "Hidratação", "Hidratação capilar.", 35.00, 20, False, 2),
    (13, "Lavagem", "Lavagem simples.", 15.00, 10, False, 1),
]
QUIMICOS = {7, 8, 9}
PROFISSIONAIS = [(1, "Profissional Demonstração 1"), (2, "Profissional Demonstração 2"), (3, "Profissional Demonstração 3")]
# serviços executados por profissional
EXEC = {1: {s[0] for s in SERVICOS} - {8}, 2: {s[0] for s in SERVICOS}, 3: {1, 2, 3, 4, 5, 6, 10, 11, 13}}
# janelas ISO (1=seg..7=dom) - HORÁRIOS DEMONSTRATIVOS, não são os da barbearia
JANELAS = {
    1: {d: [(time(9), time(12)), (time(13), time(19))] for d in range(2, 7)},
    2: {d: [(time(9), time(12)), (time(13), time(19))] for d in range(2, 7)},
    3: {d: [(time(10), time(12)), (time(13), time(18))] for d in range(3, 7)},
}
PESO_DIA = {1: 0, 2: 6, 3: 8, 4: 9, 5: 14, 6: 20, 7: 0}
PESO_HORA = {9: 6, 10: 10, 11: 9, 13: 6, 14: 8, 15: 9, 16: 12, 17: 12, 18: 6}


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def fmt(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def gerar(anchor: date, seed: int):
    rnd = random.Random(seed)
    agora = datetime.combine(anchor, time(12, 0))
    clientes = [(i, f"Cliente Demonstração {i:03d}", f"cliente{i:03d}@exemplo.invalid", None if i % 3 else "(00) 00000-0000",
                 agora - timedelta(days=rnd.randint(95, 400))) for i in range(1, 141)]
    ag, ocupado, nid = [], {p[0]: [] for p in PROFISSIONAIS}, 1
    preco = {s[0]: s[3] for s in SERVICOS}; dur = {s[0]: s[4] for s in SERVICOS}

    def livre(pid, i, f):
        return all(f <= a or i >= b for a, b in ocupado[pid])

    dias = [anchor - timedelta(days=90) + timedelta(days=k) for k in range(90 + 15)]
    for dia in dias:
        iso = dia.isoweekday()
        for pid, _ in PROFISSIONAIS:
            janelas = JANELAS[pid].get(iso, [])
            if not janelas:
                continue
            tentativas = int(PESO_DIA[iso] * rnd.uniform(0.8, 1.5)) + 2
            for _t in range(tentativas):
                sid = rnd.choices([s[0] for s in SERVICOS if s[0] in EXEC[pid]], [s[6] for s in SERVICOS if s[0] in EXEC[pid]])[0]
                ini_j, fim_j = rnd.choice(janelas)
                horas = [h for h in PESO_HORA if ini_j.hour <= h < fim_j.hour]
                if not horas:
                    continue
                h = rnd.choices(horas, [PESO_HORA[h] for h in horas])[0]
                inicio = datetime.combine(dia, time(h, rnd.choice([0, 15, 30, 45])))
                fim = inicio + timedelta(minutes=dur[sid])
                if fim > datetime.combine(dia, fim_j) or inicio < datetime.combine(dia, ini_j):
                    continue
                if not livre(pid, inicio, fim):
                    continue
                passado = inicio < agora
                r = rnd.random()
                cancelado_por, status = None, None
                if passado:
                    if r < 0.76: status = "CONCLUIDO"
                    elif r < 0.93: status = "CANCELADO"
                    elif r < 0.97: status = "CONFIRMADO"   # sem baixa: problema de qualidade proposital
                    else: status = "SOLICITADO"            # sem resposta
                else:
                    status = "CONFIRMADO" if r < 0.62 else ("SOLICITADO" if r < 0.92 else "CANCELADO")
                if status == "CANCELADO":
                    cancelado_por = "CLIENTE" if rnd.random() < 0.7 else "EQUIPE"
                    if rnd.random() < 0.04:
                        cancelado_por = None               # origem ausente: aparece no relatório de qualidade
                else:
                    ocupado[pid].append((inicio, fim))
                antec = rnd.choice([2, 6, 24, 48, 72, 120, 200])
                criado = inicio - timedelta(hours=antec, minutes=rnd.randint(0, 59))
                if criado > agora:
                    criado = agora - timedelta(hours=rnd.randint(1, 48))
                cli = rnd.choice(clientes)[0]
                ag.append(dict(id=nid, cliente_id=cli, servico_id=sid, profissional_id=pid, inicio=inicio, fim=fim,
                               duracao=dur[sid], preco=preco[sid], status=status, observacao=None,
                               cancelado_por=cancelado_por, criado_em=criado))
                nid += 1
    # reservas temporárias abandonadas (expiradas): sem cliente, não bloqueiam
    for _ in range(70):
        dia = rnd.choice(dias[:90]); pid = rnd.choice(PROFISSIONAIS)[0]
        if not JANELAS[pid].get(dia.isoweekday()):
            continue
        sid = rnd.choice(sorted(EXEC[pid])); h = rnd.choice([10, 11, 14, 15, 16, 17])
        inicio = datetime.combine(dia, time(h, rnd.choice([0, 30]))); criado = inicio - timedelta(hours=rnd.choice([3, 24, 72]))
        ag.append(dict(id=nid, cliente_id=None, servico_id=sid, profissional_id=pid, inicio=inicio, fim=inicio + timedelta(minutes=dur[sid]),
                       duracao=dur[sid], preco=preco[sid], status="EXPIRADO", observacao=None, cancelado_por=None, criado_em=criado))
        nid += 1
    ag.sort(key=lambda a: a["id"])
    return agora, clientes, ag


def escrever(anchor: date, seed: int):
    agora, clientes, ag = gerar(anchor, seed)
    L = [f"-- DADOS FICTÍCIOS GERADOS por scripts/gerar_dados_demo.py (semente={seed}, âncora={anchor}).",
         "-- Nomes, e-mails (.invalid) e preços são demonstrativos. NÃO representam a barbearia real.",
         "-- Aplicar somente em banco de DEMONSTRAÇÃO, uma vez, após schema.sql. Reexecutar é seguro (ON CONFLICT DO NOTHING).",
         "BEGIN;"]
    L.append("INSERT INTO servicos(id,nome,descricao,preco,duracao_minutos,requer_avaliacao,ilustrativo,ativo,ordem) VALUES")
    L.append(",\n".join(f"({s[0]},{q(s[1])},{q(s[2])},{s[3]:.2f},{s[4]},{q(s[5])},TRUE,TRUE,{s[0]})" for s in SERVICOS) + "\nON CONFLICT (id) DO NOTHING;")
    L.append("INSERT INTO profissionais(id,nome,ativo,ordem) VALUES\n" + ",\n".join(f"({i},{q(n)},TRUE,{i})" for i, n in PROFISSIONAIS) + "\nON CONFLICT (id) DO NOTHING;")
    ps = [f"({p},{s})" for p, ss in EXEC.items() for s in sorted(ss)]
    L.append("INSERT INTO profissional_servicos(profissional_id,servico_id) VALUES\n" + ",".join(ps) + "\nON CONFLICT DO NOTHING;")
    hs, hid = [], 1
    for p, dias in JANELAS.items():
        for d, js in dias.items():
            for a, b in js:
                hs.append(f"({hid},{p},{d},'{a}','{b}')"); hid += 1
    L.append("INSERT INTO horarios_atendimento(id,profissional_id,dia_semana,hora_inicio,hora_fim) VALUES\n" + ",\n".join(hs) + "\nON CONFLICT (id) DO NOTHING;")
    L.append("INSERT INTO clientes(id,nome,email,telefone,criado_em) VALUES\n" + ",\n".join(
        f"({c[0]},{q(c[1])},{q(c[2])},{q(c[3])},{q(fmt(c[4]))})" for c in clientes) + "\nON CONFLICT DO NOTHING;")
    rows = [f"({a['id']},{q(a['cliente_id'])},{a['servico_id']},{a['profissional_id']},{q(fmt(a['inicio']))},{q(fmt(a['fim']))},"
            f"{a['duracao']},{a['preco']:.2f},{q(a['status'])},{q(a['observacao'])},{q(a['cancelado_por'])},{q(fmt(a['criado_em']))},{q(fmt(a['criado_em']))})" for a in ag]
    L.append("INSERT INTO agendamentos(id,cliente_id,servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,observacao,cancelado_por,criado_em,atualizado_em) VALUES\n"
             + ",\n".join(rows) + "\nON CONFLICT (id) DO NOTHING;")
    for t in ("servicos", "profissionais", "horarios_atendimento", "clientes", "agendamentos"):
        L.append(f"SELECT setval(pg_get_serial_sequence('{t}','id'), (SELECT MAX(id) FROM {t}));")
    L.append("COMMIT;")
    (ROOT / "database" / "seed_demo.sql").write_text("\n".join(L) + "\n", encoding="utf-8")

    out = ROOT / "analytics" / "sample_data"; out.mkdir(parents=True, exist_ok=True)
    with open(out / "fato_agendamentos.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["agendamento_id", "servico_id", "profissional_id", "data_inicio", "inicio", "fim", "dia_semana_iso", "hora_inicio", "ano_mes",
                    "duracao_minutos", "preco_referencia", "status", "cancelado_por", "criado_em", "data_criacao", "antecedencia_horas"])
        for a in ag:
            w.writerow([a["id"], a["servico_id"], a["profissional_id"], a["inicio"].date(), fmt(a["inicio"]), fmt(a["fim"]),
                        a["inicio"].isoweekday(), a["inicio"].hour, a["inicio"].year * 100 + a["inicio"].month, a["duracao"], f"{a['preco']:.2f}", a["status"], a["cancelado_por"] or "",
                        fmt(a["criado_em"]), a["criado_em"].date(), round((a["inicio"] - a["criado_em"]).total_seconds() / 3600, 1)])
    with open(out / "dim_servico.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["servico_id", "servico", "preco_tabela_atual", "duracao_tabela_atual", "requer_avaliacao", "ilustrativo", "ativo"])
        for s in SERVICOS: w.writerow([s[0], s[1], f"{s[3]:.2f}", s[4], str(s[5]).lower(), "true", "true"])
    with open(out / "dim_profissional.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["profissional_id", "profissional", "ativo"])
        for p in PROFISSIONAIS: w.writerow([p[0], p[1], "true"])
    return agora, len(ag)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(); ap.add_argument("--anchor", default="2026-10-24"); ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args(); ag, n = escrever(date.fromisoformat(a.anchor), a.seed)
    print(f"{n} agendamentos fictícios gerados (âncora {a.anchor}).")
