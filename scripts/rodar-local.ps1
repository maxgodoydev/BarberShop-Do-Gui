# Roda o projeto localmente (Windows/PowerShell). Requer: JDK 21, Maven, PostgreSQL 15+ (psql no PATH).
# Uso, na raiz do projeto:  powershell -ExecutionPolicy Bypass -File scripts/rodar-local.ps1
# NÃO foi testado em máquina real; se algo falhar, leia a mensagem e veja o README.
$ErrorActionPreference = "Stop"
foreach ($c in "java","mvn","psql") { if (-not (Get-Command $c -ErrorAction SilentlyContinue)) { throw "Comando '$c' não encontrado no PATH. Instale-o e reabra o terminal." } }
$pgUser = Read-Host "Usuário do PostgreSQL (padrão: postgres)"; if (-not $pgUser) { $pgUser = "postgres" }
$pgPass = Read-Host "Senha do PostgreSQL" -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pgPass))
$db = "barbershop_gui"
$existe = psql -h localhost -U $pgUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'"
if (-not $existe) { psql -h localhost -U $pgUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db" }
foreach ($f in "schema.sql","analytics_views.sql","seed_demo.sql","migracoes/004_etapa4.sql","migracoes/005_exclusao_e_supabase.sql","migracoes/006_bi.sql","migracoes/007_etapa4b.sql","migracoes/008_perfis_balcao.sql","migracoes/009_regras_servicos.sql","seed_demo_produtos.sql") { psql -h localhost -U $pgUser -d $db -v ON_ERROR_STOP=1 -f "database/$f" }
$env:DB_URL = "jdbc:postgresql://localhost:5432/$db"; $env:DB_USERNAME = $pgUser; $env:DB_PASSWORD = $env:PGPASSWORD
$env:BOOTSTRAP_OWNER_USERNAME = Read-Host "Crie o usuário do proprietário (ex.: gui)"
$own = Read-Host "Senha do proprietário (12+ caracteres)" -AsSecureString
$env:BOOTSTRAP_OWNER_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($own))
Write-Host "Subindo em http://localhost:8080  (admin: /admin-login.html). O link de consulta do cliente aparece neste terminal (modo dev, sem e-mail real)."
Set-Location app; mvn spring-boot:run
