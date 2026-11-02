#!/usr/bin/env python3
"""Executa database/tests/*.sql contra o PostgreSQL e FALHA (exit 1) se algum caso não se comportar como o
próprio script declara em "\\echo CASO n - ESPERADO ...". Antes, os erros esperados apenas apareciam no log e a
CI ficava verde mesmo se uma regra do banco quebrasse.

Regras de leitura de cada caso:
  ESPERADO erro XXXXX   -> a saída do caso deve conter o SQLSTATE XXXXX
  ESPERADO PALAVRA      -> (maiúsculas) a saída do caso deve conter essa palavra sozinha numa linha (ex.: OK)
  qualquer outro        -> (ok / sucesso) a saída do caso não pode conter ERROR
Conexão: variáveis padrão do libpq (PGHOST, PGUSER, PGPASSWORD, PGDATABASE). Uso: python3 scripts/verifica_banco.py
"""
import re, subprocess, sys
from pathlib import Path

TESTS = sorted((Path(__file__).resolve().parents[1] / "database" / "tests").glob("*.sql"))
CASO = re.compile(r"^CASO (\d+) - (ESPERADO .*)$", re.M)
falhas, total = [], 0

for t in TESTS:
    r = subprocess.run(["psql", "-X", "-v", "VERBOSITY=verbose", "-f", str(t)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    out = r.stdout  # stderr no mesmo fluxo: preserva a ordem entre \echo e mensagens de erro
    marks = list(CASO.finditer(out))
    if not marks:
        falhas.append(f"{t.name}: nenhum caso executado (conexão/erro de setup?) -> {out.strip()[:300]}")
        continue
    setup = out[:marks[0].start()]
    if "ERROR" in setup:
        falhas.append(f"{t.name}: erro no preparo antes do 1º caso -> {setup.strip()[:300]}")
    for i, m in enumerate(marks):
        total += 1
        fim = marks[i + 1].start() if i + 1 < len(marks) else len(out)
        secao, esperado = out[m.end():fim], m.group(2)
        e = re.match(r"ESPERADO erro (\w{5})", esperado)
        w = re.match(r"ESPERADO ([A-Z_]+)(?![A-Za-z])", esperado)
        if e:
            ok = re.search(rf"ERROR:\s+{e.group(1)}\b", secao) is not None
        elif w:
            ok = re.search(rf"(?m)^\s*{w.group(1)}\s*$", secao) is not None and "ERROR" not in secao
        else:
            ok = "ERROR" not in secao
        print(f"{'OK   ' if ok else 'FALHA'} {t.name} caso {m.group(1)}: {esperado}")
        if not ok:
            falhas.append(f"{t.name} caso {m.group(1)}: {esperado}\n    saída: {secao.strip()[:300]}")

if falhas:
    print(f"\n{len(falhas)} FALHA(S) de {total} casos:")
    for f in falhas: print(" -", f)
    sys.exit(1)
print(f"\nOK: {total} casos de banco se comportaram como esperado.")
