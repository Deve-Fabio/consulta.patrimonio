package br.gov.detrandf.patrimonio;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet de consulta de patrimônio — DETRAN-DF
 *
 * URL: GET /api/patrimonio/{numero}
 * Exemplo: GET /api/patrimonio/040608
 *
 * Retorna JSON com os dados do bem patrimonial.
 * O banco nunca é acessado diretamente pelo navegador.
 */
@WebServlet("/api/patrimonio/*")
public class PatrimonioServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PatrimonioServlet.class.getName());

    // Regex: aceita apenas números, entre 1 e 10 dígitos
    private static final String NUMERO_VALIDO = "\\d{1,10}";

    // ─────────────────────────────────────────────────────────────
    // GET /api/patrimonio/{numero}
    // ─────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // 1. Cabeçalhos de resposta
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");           // não cacheia dados
        resp.setHeader("X-Content-Type-Options", "nosniff");   // segurança extra

        // Permite que o app.js (mesma origem) acesse normalmente.
        // Se front e back estiverem em origens diferentes, ajuste o domínio abaixo.
        resp.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = resp.getWriter();

        // 2. Extrair número do patrimônio da URL  →  /api/patrimonio/040608
        String pathInfo = req.getPathInfo();           // "/040608"
        if (pathInfo == null || pathInfo.length() < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"erro\":\"Informe o numero do patrimônio na URL.\"}");
            return;
        }

        String numeroRaw = pathInfo.substring(1);      // remove a barra inicial

        // 3. Validação: somente dígitos, tamanho razoável
        if (!numeroRaw.matches(NUMERO_VALIDO)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"erro\":\"Numero de patrimônio inválido.\"}");
            return;
        }

        // 4. Consultar o banco via JNDI DataSource (configurado no context.xml)
        try {
            String json = consultarPatrimonio(numeroRaw);

            if (json == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"erro\":\"Patrimônio não encontrado.\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
                out.print(json);
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao consultar patrimônio: " + numeroRaw, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // Mensagem genérica — nunca expor detalhes do erro ao cliente
            out.print("{\"erro\":\"Erro interno. Tente novamente.\"}");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Consulta ao banco
    // ─────────────────────────────────────────────────────────────
    private String consultarPatrimonio(String numero) throws Exception {

        // Busca o DataSource configurado no context.xml do Tomcat
        Context ctx  = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/patrimonio");

        /*
         * A query traz o bem pelo número exato OU com zero-padding de 6/7 dígitos,
         * reproduzindo a mesma lógica que estava no DB local do app.js.
         *
         * Ajuste a query, tabela e colunas conforme o seu banco real.
         */
        String sql = """
                SELECT
                    p.numero,
                    p.descricao,
                    p.situacao_fisica,
                    p.status,
                    p.unidade,
                    p.endereco,
                    o.tipo        AS obs_tipo,
                    o.texto       AS obs_texto,
                    o.destino     AS obs_destino,
                    o.solicitante AS obs_solicitante,
                    o.data        AS obs_data,
                    o.protocolo   AS obs_protocolo
                FROM patrimonio p
                LEFT JOIN patrimonio_obs o ON o.numero = p.numero
                WHERE p.numero = ?
                   OR p.numero = LPAD(?, 6, '0')
                   OR p.numero = LPAD(?, 7, '0')
                LIMIT 1
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // PreparedStatement: banco trata o valor como DADO, nunca como código SQL
            ps.setString(1, numero);
            ps.setString(2, numero);
            ps.setString(3, numero);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;   // não encontrado
                }
                return montarJson(rs);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Monta o JSON manualmente (sem dependência extra como Gson/Jackson)
    // ─────────────────────────────────────────────────────────────
    private String montarJson(ResultSet rs) throws SQLException {

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"num\":"        ).append(jsonStr(rs.getString("numero")))        .append(",");
        sb.append("\"desc\":"       ).append(jsonStr(rs.getString("descricao")))      .append(",");
        sb.append("\"sf\":"         ).append(jsonStr(rs.getString("situacao_fisica"))).append(",");
        sb.append("\"status\":"     ).append(jsonStr(rs.getString("status")))         .append(",");
        sb.append("\"unidade\":"    ).append(jsonStr(rs.getString("unidade")))        .append(",");
        sb.append("\"end\":"        ).append(jsonStr(rs.getString("endereco")));

        // Observação é opcional (LEFT JOIN pode retornar null)
        String obsTipo = rs.getString("obs_tipo");
        if (obsTipo != null) {
            sb.append(",\"obs\":{");
            sb.append("\"tipo\":"       ).append(jsonStr(obsTipo))                          .append(",");
            sb.append("\"texto\":"      ).append(jsonStr(rs.getString("obs_texto")))        .append(",");
            sb.append("\"destino\":"    ).append(jsonStr(rs.getString("obs_destino")))      .append(",");
            sb.append("\"solicitante\":").append(jsonStr(rs.getString("obs_solicitante")))  .append(",");
            sb.append("\"data\":"       ).append(jsonStr(rs.getString("obs_data")))         .append(",");
            sb.append("\"protocolo\":" ).append(jsonStr(rs.getString("obs_protocolo")));
            sb.append("}");
        } else {
            sb.append(",\"obs\":null");
        }

        sb.append("}");
        return sb.toString();
    }

    // Escapa string para JSON seguro (evita XSS via JSON)
    private String jsonStr(String valor) {
        if (valor == null) return "null";
        return "\"" + valor
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
