"""Testes do pipeline (unittest, sem dependências além de pandas).
Executar em analytics/: python -m unittest discover -s tests -v
"""
import csv, re, shutil, sqlite3, tempfile, unittest
from pathlib import Path
import pandas as pd
from barbershop_analytics import pipeline, sources

ROOT = Path(__file__).resolve().parents[2]
AMOSTRA = ROOT / "analytics" / "sample_data"
QUERIES = ROOT / "database" / "queries"
AGORA = pd.Timestamp("2026-10-24T12:00:00")


def rodar(entrada, saida):
    return pipeline.executar("csv", Path(entrada), Path(saida), AGORA)


def sqlite_com_csv(pasta):
    """Carrega os CSVs em SQLite com os mesmos nomes das views, para executar as consultas de database/queries/."""
    con = sqlite3.connect(":memory:")
    for chave, view in sources.TABELAS.items():
        pd.read_csv(Path(pasta) / sources.ARQUIVOS[chave]).to_sql(view, con, index=False)
    return con


def consulta(con, arquivo, **params):
    sql = re.sub(r"--.*", "", (QUERIES / arquivo).read_text(encoding="utf-8"))
    return pd.read_sql_query(sql, con, params=params)


class PipelineDemo(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp())
        cls.rel = rodar(AMOSTRA, cls.tmp / "out")
        cls.con = sqlite_com_csv(AMOSTRA)
        cls.agora = "2026-10-24 12:00:00"

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def test_arquivos_exportados(self):
        for nome in ["fato_agendamentos_tratado", "agg_status", "agg_servico", "agg_dia_hora", "agg_profissional",
                     "agg_mensal", "dim_calendario", "quality_report"]:
            self.assertTrue(any((self.tmp / "out").glob(nome + ".*")), nome)

    def test_contagem_independente_do_pandas(self):
        with open(AMOSTRA / "fato_agendamentos.csv", encoding="utf-8") as f:
            linhas = list(csv.DictReader(f))
        pedidos = [l for l in linhas if l["status"] in ("SOLICITADO", "CONFIRMADO", "CONCLUIDO", "CANCELADO")]
        k = self.rel["kpis"]
        self.assertEqual(k["pedidos"], len(pedidos))
        self.assertEqual(k["concluidos"], sum(l["status"] == "CONCLUIDO" for l in linhas))
        self.assertEqual(k["cancelados"], sum(l["status"] == "CANCELADO" for l in linhas))
        self.assertEqual(self.rel["linhas_lidas"], len(linhas))

    def test_reconciliacao_sql_x_python_kpis(self):
        s = consulta(self.con, "02_indicadores_chave.sql", agora=self.agora).iloc[0]
        k = self.rel["kpis"]
        for campo in ("pedidos", "concluidos", "cancelados", "pedidos_vencidos"):
            self.assertEqual(int(s[campo]), k[campo], campo)
        for campo in ("taxa_cancelamento_pct", "taxa_conclusao_pct", "taxa_abandono_reserva_pct"):
            self.assertAlmostEqual(float(s[campo]), k[campo], places=1, msg=campo)

    def test_reconciliacao_sql_x_python_agregados(self):
        saida = self.tmp / "out"
        comparacoes = [("01_visao_geral.sql", "agg_status", ["status"]), ("03_servicos_demanda.sql", "agg_servico", ["servico"]),
                       ("04_dia_semana_hora.sql", "agg_dia_hora", ["dia_semana_iso", "hora_inicio"]),
                       ("05_profissionais.sql", "agg_profissional", ["profissional"]), ("06_evolucao_mensal.sql", "agg_mensal", ["ano_mes"]),
                       ("07_cancelamentos.sql", "agg_cancelamentos", ["origem"]), ("08_pendencias_operacionais.sql", "agg_pendencias", ["status"])]
        for sql, csv_nome, chave in comparacoes:
            with self.subTest(sql):
                a = consulta(self.con, sql, agora=self.agora).sort_values(chave).reset_index(drop=True)
                b = pd.read_csv(saida / f"{csv_nome}.csv").sort_values(chave).reset_index(drop=True)
                self.assertEqual(list(a.columns), list(b.columns))
                pd.testing.assert_frame_equal(a, b, check_dtype=False, check_exact=False, atol=0.051)

    def test_seed_sql_confere_com_csv(self):
        """seed_demo.sql carregado em SQLite (esquema mínimo) deve ter as mesmas contagens do CSV."""
        con = sqlite3.connect(":memory:")
        con.executescript("""CREATE TABLE servicos(id,nome,descricao,preco,duracao_minutos,requer_avaliacao,ilustrativo,ativo,ordem);
          CREATE TABLE profissionais(id,nome,ativo,ordem); CREATE TABLE profissional_servicos(profissional_id,servico_id);
          CREATE TABLE horarios_atendimento(id,profissional_id,dia_semana,hora_inicio,hora_fim);
          CREATE TABLE clientes(id,nome,email,telefone,criado_em);
          CREATE TABLE agendamentos(id,cliente_id,servico_id,profissional_id,inicio,fim,duracao_minutos,preco_referencia,status,observacao,cancelado_por,criado_em,atualizado_em);""")
        sql = "\n".join(l for l in (ROOT / "database" / "seed_demo.sql").read_text(encoding="utf-8").splitlines()
                        if not l.startswith(("BEGIN", "COMMIT", "SELECT setval", "--")))
        sql = sql.replace("ON CONFLICT (id) DO NOTHING", "").replace("ON CONFLICT DO NOTHING", "")
        con.executescript(sql)
        por_status = dict(con.execute("SELECT status,COUNT(*) FROM agendamentos GROUP BY status").fetchall())
        csv_status = pd.read_csv(AMOSTRA / "fato_agendamentos.csv").groupby("status").size().to_dict()
        self.assertEqual(por_status, csv_status)
        self.assertEqual(con.execute("SELECT COUNT(*) FROM profissionais").fetchone()[0], 3)
        self.assertEqual(con.execute("SELECT COUNT(*) FROM servicos").fetchone()[0], 13)
        self.assertEqual(con.execute("SELECT COUNT(*) FROM clientes WHERE email NOT LIKE '%@exemplo.invalid'").fetchone()[0], 0)


class CasosLimite(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _copiar(self):
        d = self.tmp / "in"
        shutil.copytree(AMOSTRA, d)
        return d

    def test_base_vazia_nao_quebra(self):
        d = self._copiar()
        header = (AMOSTRA / "fato_agendamentos.csv").read_text(encoding="utf-8").splitlines()[0]
        (d / "fato_agendamentos.csv").write_text(header + "\n", encoding="utf-8")
        r = rodar(d, self.tmp / "out")
        self.assertEqual(r["linhas_lidas"], 0)
        self.assertIsNone(r["kpis"]["taxa_cancelamento_pct"])
        self.assertIn("Base vazia", r["kpis"]["observacao"])
        self.assertTrue((self.tmp / "out" / "agg_servico.csv").exists())

    def test_dados_sujos_sao_rejeitados_ou_sinalizados(self):
        d = self._copiar()
        df = pd.read_csv(d / "fato_agendamentos.csv", dtype=str, keep_default_na=False)
        df.loc[0, "status"] = "INEXISTENTE"                                  # R02
        df.loc[1, "fim"] = df.loc[1, "inicio"]                                # R03
        df.loc[2, "servico_id"] = "999"                                       # R05
        df.loc[3, "agendamento_id"] = df.loc[10, "agendamento_id"]            # R04 duplicado (3 repete o id da linha 10)
        df.to_csv(d / "fato_agendamentos.csv", index=False)
        r = rodar(d, self.tmp / "out")
        regras = {a["regra"] for a in r["achados"]}
        self.assertTrue({"R02_STATUS_INVALIDO", "R03_PERIODO_INVALIDO", "R05_SERVICO_ORFAO", "R04_ID_DUPLICADO"} <= regras, regras)
        self.assertEqual(r["linhas_validas"] + r["linhas_rejeitadas"], r["linhas_lidas"])
        self.assertGreaterEqual(r["linhas_rejeitadas"], 4)

    def test_sobreposicao_ativa_e_detectada(self):
        d = self._copiar()
        df = pd.read_csv(d / "fato_agendamentos.csv", dtype=str, keep_default_na=False)
        a = df[(df.status == "CONCLUIDO") & (df.profissional_id == "1")].iloc[0].copy()
        b = a.copy()
        b["agendamento_id"] = "999001"
        df = pd.concat([df, pd.DataFrame([b])], ignore_index=True)
        df.to_csv(d / "fato_agendamentos.csv", index=False)
        r = rodar(d, self.tmp / "out")
        self.assertIn("R11_SOBREPOSICAO_ATIVA", {x["regra"] for x in r["achados"]})

    def test_arquivo_ausente_gera_erro_claro(self):
        with self.assertRaises(FileNotFoundError):
            sources.ler_csv(self.tmp)

    def test_cli_retorna_codigo_de_erro(self):
        self.assertEqual(pipeline.main(["--input", str(self.tmp), "--output", str(self.tmp / "o")]), 1)


if __name__ == "__main__":
    unittest.main()
