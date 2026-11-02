// Power Query (M). Escolha UMA origem. Substitua os parâmetros (Servidor, Banco) pelos seus; NUNCA grave senha neste arquivo.
// --- Origem A: arquivos tratados pelo pipeline Python (recomendado para o portfólio) ---
let
    PastaSaida = "C:\\caminho\\para\\barbershop-do-gui\\analytics\\output\\",   // ajuste
    Fonte = Csv.Document(File.Contents(PastaSaida & "fato_agendamentos_tratado.csv"), [Delimiter=",", Encoding=65001, QuoteStyle=QuoteStyle.Csv]),
    Cabecalho = Table.PromoteHeaders(Fonte, [PromoteAllScalars=true]),
    Tipos = Table.TransformColumnTypes(Cabecalho, {
        {"agendamento_id", Int64.Type}, {"servico_id", Int64.Type}, {"profissional_id", Int64.Type},
        {"data_inicio", type date}, {"inicio", type datetime}, {"fim", type datetime}, {"dia_semana_iso", Int64.Type},
        {"hora_inicio", Int64.Type}, {"ano_mes", Int64.Type}, {"duracao_minutos", Int64.Type}, {"preco_referencia", type number},
        {"status", type text}, {"cancelado_por", type text}, {"criado_em", type datetime}, {"data_criacao", type date}, {"antecedencia_horas", type number}}, "en-US")
in
    Tipos

// --- Origem B: views do PostgreSQL (usuário somente leitura analytics_ro) ---
// let Fonte = PostgreSQL.Database(Servidor, Banco), Fato = Fonte{[Schema="public", Item="vw_fato_agendamentos"]}[Data] in Fato
// Repita para vw_dim_servico, vw_dim_profissional e vw_dim_calendario (ou use dim_calendario.csv).
