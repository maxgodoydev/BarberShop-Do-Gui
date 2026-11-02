#!/usr/bin/env python3
"""Verificações estáticas do repositório (não compila Java nem executa SQL). Uso: python3 scripts/check_project.py"""
from pathlib import Path
import re, sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "app/src/main/resources/static"
errors = []

REQUIRED = ["README.md", "LICENSE", ".gitignore", ".env.example", "app/pom.xml", "database/schema.sql", "database/analytics_views.sql",
            "database/seed_demo.sql", "database/aplicar.sh", "scripts/verifica_banco.py", "analytics/barbershop_analytics/pipeline.py", "analytics/tests/test_pipeline.py",
            "analytics/powerbi/medidas.dax", "analytics/powerbi/modelo-e-layout.md", "docs/testes.md", "docs/seguranca.md", "docs/api.md"]
for rel in REQUIRED:
    p = ROOT / rel
    if not p.is_file() or p.stat().st_size == 0:
        errors.append(f"ausente ou vazio: {rel}")

try:
    ET.parse(ROOT / "app/pom.xml")
except ET.ParseError as exc:
    errors.append(f"pom.xml inválido: {exc}")

if (ROOT / ".env").exists():
    errors.append(".env não deve existir no pacote")

# HTML: links locais existem; sem estilo/handler inline (CSP)
for html in STATIC.rglob("*.html"):
    text = html.read_text(encoding="utf-8")
    if re.search(r'\sstyle="|\son[a-z]+="', text):
        errors.append(f"{html.relative_to(ROOT)}: estilo ou handler inline (bloqueado pela CSP)")
    for ref in re.findall(r'(?:href|src)="(/[^"#?]*)', text):
        if ref != "/" and not (STATIC / ref.lstrip("/")).exists() and not ref.startswith("/api/"):
            errors.append(f"{html.relative_to(ROOT)}: referência inexistente {ref}")

# segredos
patterns = ["-----begin private key-----", "ghp_", "sk-live-", "akia"]
for p in ROOT.rglob("*"):
    if not p.is_file() or p.suffix in {".png", ".jpg", ".zip", ".jar"} or "target" in p.parts or p.name == "check_project.py" or ".git" in p.parts:
        continue
    low = p.read_text(encoding="utf-8", errors="ignore").lower()
    if any(x in low for x in patterns):
        errors.append(f"possível segredo em {p.relative_to(ROOT)}")

# dados de demonstração só fictícios
seed = (ROOT / "database/seed_demo.sql").read_text(encoding="utf-8")
emails = set(re.findall(r"'([^'\s]+@[^'\s]+)'", seed))
if any(not e.endswith("@exemplo.invalid") for e in emails):
    errors.append("seed contém e-mail fora do domínio reservado .invalid")

# ordem do banco: toda migração precisa constar nos scripts e docs (evita doc/CI desatualizados)
migs = sorted(p.name for p in (ROOT / "database/migracoes").glob("*.sql"))
for rel in ["database/aplicar.sh", "app/src/main/java/br/com/barbershopdogui/config/LocalDatabaseConfig.java",
            "scripts/rodar-local.sh", "scripts/rodar-local.ps1", "docs/deploy.md", "docs/supabase.md", "README.md"]:
    txt = (ROOT / rel).read_text(encoding="utf-8")
    for m in migs:
        if m.split("_")[0] not in txt and m not in txt:
            errors.append(f"{rel}: não menciona a migração {m}")

# produção: padrões seguros no perfil prod
prod = ROOT / "app/src/main/resources/application-prod.properties"
if not prod.is_file() or "cookie.secure=true" not in prod.read_text(encoding="utf-8") or "forward-headers-strategy=framework" not in prod.read_text(encoding="utf-8"):
    errors.append("application-prod.properties ausente ou sem cookie seguro / forward-headers")

if errors:
    print("FALHAS:"); [print(" -", e) for e in errors]; sys.exit(1)
print("OK: arquivos essenciais, pom.xml, links/CSP do front, ausência de segredos e seed fictício verificados.")
